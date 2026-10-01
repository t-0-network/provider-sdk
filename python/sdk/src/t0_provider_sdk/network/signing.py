"""Signing HTTP transport wrappers for pyqwest Client and SyncClient.

These wrappers add T-0 Network signature headers to outgoing requests before
delegating to the underlying pyqwest client. ConnectRPC uses exactly three methods
on the client: get(), post(), and stream(); get() is refused.

post() carries Connect unary calls and is signed over its whole body. stream() carries every
streaming call and every gRPC call (unary included) as envelopes, one per chunk, and is signed over
its first envelope as sent. See docs/STREAMING.md.
"""

from __future__ import annotations

import functools
import inspect
import struct
import time
import types
from contextlib import asynccontextmanager, contextmanager
from typing import TYPE_CHECKING, Any

import pyqwest
from connectrpc.code import Code
from connectrpc.errors import ConnectError

from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256

if TYPE_CHECKING:
    from collections.abc import AsyncIterable, AsyncIterator, Iterable, Iterator
    from contextlib import AbstractAsyncContextManager, AbstractContextManager

    from t0_provider_sdk.crypto.signer import SignFn

# An envelope is flags (1 byte) || big-endian uint32 payload length || payload.
_ENVELOPE_PREFIX_SIZE = 5


def _timestamp_ms() -> int:
    return int(time.time() * 1000)


def _sign_request(
    sign_fn: SignFn,
    body: bytes,
    headers: pyqwest.Headers | None,
) -> pyqwest.Headers:
    """Compute signature and add signing headers.

    Protocol:
    1. timestamp_ms = current time in milliseconds
    2. timestamp_le = little-endian uint64 encoding of timestamp_ms (8 bytes)
    3. digest = Keccak256(body + timestamp_le)
    4. signature, public_key = sign(digest)
    5. Set X-Public-Key, X-Signature, X-Signature-Timestamp headers

    body is what the signature covers: the whole body of post(), or the first envelope of stream().
    """
    timestamp_ms = _timestamp_ms()
    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    digest = legacy_keccak256(body + timestamp_bytes)
    signature, pub_key = sign_fn(digest)

    if headers is None:
        headers = pyqwest.Headers()
    headers[PUBLIC_KEY_HEADER] = f"0x{pub_key.hex()}"
    headers[SIGNATURE_HEADER] = f"0x{signature.hex()}"
    headers[SIGNATURE_TIMESTAMP_HEADER] = str(timestamp_ms)
    return headers


def _broken_first_message() -> ConnectError:
    return ConnectError(Code.INVALID_ARGUMENT, "streaming request ends inside its first message")


def _require_one_envelope(chunk: bytes) -> None:
    """connectrpc yields one envelope per chunk. Checked, not trusted: a change in that framing must
    fail the call, never sign the wrong bytes."""
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(chunk[1:_ENVELOPE_PREFIX_SIZE], "big")
    if len(chunk) < _ENVELOPE_PREFIX_SIZE or len(chunk) < size:
        raise _broken_first_message()
    if len(chunk) != size:
        raise ConnectError(Code.INTERNAL, "the first request chunk is not one complete envelope")


def _get_unsupported() -> ConnectError:
    # A GET carries its message in the URL, which the signature would not cover.
    return ConnectError(Code.UNIMPLEMENTED, "GET requests are not supported")


def _remaining_timeout(timeout: float | None, started: float, waited_for: str) -> float | None:
    """Deducts time spent preparing a sync request. Blocking source reads and signing are not
    interrupted: once they return with no time left, this raises TimeoutError and nothing is sent."""
    if timeout is None:
        return None
    timeout -= time.monotonic() - started
    if timeout <= 0:
        raise TimeoutError(f"timed out waiting for {waited_for}")
    return timeout


_BYTES = (bytes, bytearray, memoryview)


def _pre_framed(body: bytes | bytearray | memoryview | None) -> list[bytes]:
    """A body given whole, cut after its first envelope so that the same reader checks and signs that
    envelope as it does for an iterator. The rest follows unchanged; a body that ends inside its first
    envelope stays one chunk, which the reader refuses."""
    body = bytes(body or b"")
    if not body:
        return []
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(body[1:_ENVELOPE_PREFIX_SIZE], "big")
    return [body[:size], body[size:]] if len(body) > size else [body]


async def _chunks(body: bytes | bytearray | memoryview | None) -> AsyncIterator[bytes]:
    for chunk in _pre_framed(body):
        yield chunk


async def _aclose(source: AsyncIterator[bytes]) -> None:
    aclose = getattr(source, "aclose", None)
    if aclose is not None:
        await aclose()


def _close(source: Iterator[bytes]) -> None:
    # pyqwest closes the body from the caller's thread while its writer thread may be inside the
    # generator. It skips a running generator but cannot see the one behind the chain, so do it here.
    if isinstance(source, types.GeneratorType) and inspect.getgeneratorstate(source) == inspect.GEN_RUNNING:
        return
    close = getattr(source, "close", None)
    if close is not None:
        close()


@functools.cache
def _shared_transport(*, sync: bool, http2: bool) -> Any:
    """One per kind for the process: a transport holds its connections, and clients never close it.

    Redirects are not followed: the next request would carry this request's signature to another URL.
    The system's CA certificates are trusted: a transport built here has no roots of its own, so
    every https:// call would fail without them.
    """
    options = {
        "http_version": pyqwest.HTTPVersion.HTTP2 if http2 else None,
        "follow_redirects": False,
        "tls_include_system_certs": True,
    }
    if sync:
        return pyqwest.SyncHTTPTransport(**options)
    return pyqwest.HTTPTransport(**options)


class _AsyncChain:
    """The first envelope, already read, then the rest of source. pyqwest closes only the body it
    is given, so closing the chain closes source."""

    def __init__(self, first: bytes | None, source: AsyncIterator[bytes]) -> None:
        self._first = first
        self._source = source

    def __aiter__(self) -> _AsyncChain:
        return self

    async def __anext__(self) -> bytes:
        if self._first is not None:
            first, self._first = self._first, None
            return first
        return await anext(self._source)

    async def aclose(self) -> None:
        await _aclose(self._source)


class _SyncChain:
    """Sync variant of _AsyncChain."""

    def __init__(self, first: bytes | None, source: Iterator[bytes]) -> None:
        self._first = first
        self._source = source

    def __iter__(self) -> _SyncChain:
        return self

    def __next__(self) -> bytes:
        if self._first is not None:
            first, self._first = self._first, None
            return first
        return next(self._source)

    def close(self) -> None:
        _close(self._source)


class SigningClient:
    """Async signing wrapper for pyqwest.Client.

    Passed to ConnectRPC async client via http_client= parameter.
    Signs post() and stream(); get() is refused, since a GET has no body to sign.

    A streaming request is sent once its first message is available: send one (or close the
    stream) before waiting for a response. Bidirectional streams are not supported; only the
    factory-built clients reject them. See docs/STREAMING.md.

    Redirects are not followed, so a 3xx response fails the call. A transport passed in must not
    follow them either.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.Client(transport=transport or _shared_transport(sync=False, http2=False))
        self._sign_fn = sign_fn

    async def get(self, url: str, headers: pyqwest.Headers | None = None) -> Any:
        raise _get_unsupported()

    async def post(self, url: str, headers: pyqwest.Headers | None = None, content: bytes | None = None) -> Any:
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return await self._inner.post(url, headers=headers, content=content)

    def stream(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | AsyncIterable[bytes] | None = None,
    ) -> AbstractAsyncContextManager[pyqwest.Response]:
        """content: envelopes, one per chunk, as connectrpc passes them; or a pre-framed body as bytes;
        or None for an empty stream."""
        if method.upper() == "GET":
            raise _get_unsupported()
        source = _chunks(content) if content is None or isinstance(content, _BYTES) else aiter(content)
        return self._stream_signing_first_envelope(method, url, headers, source)

    # The body is read when the context is entered, which connectrpc does inside its call timeout.

    @asynccontextmanager
    async def _stream_signing_first_envelope(
        self, method: str, url: str, headers: pyqwest.Headers | None, source: AsyncIterator[bytes]
    ) -> AsyncIterator[pyqwest.Response]:
        try:
            # An empty client stream is signed over b"" and sent; the network rejects it.
            first = await anext(source, None)
            if first is not None:
                _require_one_envelope(first)
            headers = _sign_request(self._sign_fn, first or b"", headers)
        except BaseException:
            await _aclose(source)
            raise
        content = _AsyncChain(first, source)
        async with self._inner.stream(method, url, headers=headers, content=content) as response:
            yield response


class SigningSyncClient:
    """Sync signing wrapper for pyqwest.SyncClient.

    Passed to ConnectRPC sync client via http_client= parameter.
    Signs post() and stream(); get() is refused, since a GET has no body to sign.

    A streaming request is sent once its first message is available: send one (or close the
    stream) before waiting for a response. Bidirectional streams are not supported; only the
    factory-built clients reject them.

    A blocked source or signer is not interrupted: the time it takes to yield and sign the first
    message is deducted from the call's timeout, and if none is left, nothing is sent, the source
    is closed and the call fails with TimeoutError (DEADLINE_EXCEEDED in connectrpc). Bounding the
    time of each read is up to the source. See docs/STREAMING.md.

    Redirects are not followed, so a 3xx response fails the call. A transport passed in must not
    follow them either.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.SyncClient(transport=transport or _shared_transport(sync=True, http2=False))
        self._sign_fn = sign_fn

    def get(self, url: str, headers: pyqwest.Headers | None = None, timeout: float | None = None) -> Any:
        raise _get_unsupported()

    def post(
        self,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | None = None,
        timeout: float | None = None,
    ) -> Any:
        started = time.monotonic()
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        timeout = _remaining_timeout(timeout, started, "request signing")
        return self._inner.post(url, headers=headers, content=content, timeout=timeout)

    def stream(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | Iterable[bytes] | None = None,
        timeout: float | None = None,
    ) -> AbstractContextManager[pyqwest.SyncResponse]:
        """content as for SigningClient.stream."""
        if method.upper() == "GET":
            raise _get_unsupported()
        source = iter(_pre_framed(content)) if content is None or isinstance(content, _BYTES) else iter(content)
        return self._stream_signing_first_envelope(method, url, headers, source, timeout)

    @contextmanager
    def _stream_signing_first_envelope(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None,
        source: Iterator[bytes],
        timeout: float | None,
    ) -> Iterator[pyqwest.SyncResponse]:
        started = time.monotonic()
        try:
            first = next(source, None)
            if first is not None:
                _require_one_envelope(first)
            _remaining_timeout(timeout, started, "the first request message")
            headers = _sign_request(self._sign_fn, first or b"", headers)
            timeout = _remaining_timeout(timeout, started, "request signing")
        except BaseException:
            _close(source)
            raise
        content = _SyncChain(first, source)
        with self._inner.stream(method, url, headers=headers, content=content, timeout=timeout) as response:
            yield response
