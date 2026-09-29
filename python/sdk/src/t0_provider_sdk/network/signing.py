"""Signing HTTP transport wrappers for pyqwest Client and SyncClient.

These wrappers add T-0 Network signature headers to outgoing requests before
delegating to the underlying pyqwest client. ConnectRPC uses exactly three methods
on the client: get(), post(), and stream(); get() is refused.

The content type decides what is signed: enveloped requests (Connect streaming, gRPC) over their
first envelope as sent, everything else over the whole body. See docs/STREAMING.md.
"""

from __future__ import annotations

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
    from collections.abc import AsyncIterator, Iterable, Iterator
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

    body is what the signature covers: the whole body, or an enveloped request's first envelope.
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


def _is_enveloped(headers: pyqwest.Headers | None) -> bool:
    """Whether only the first envelope is signed. Not for gRPC-Web: the network verifies its whole body."""
    content_type = headers.get("content-type") if headers is not None else None
    media_type = (content_type or "").partition(";")[0].strip().lower()
    return media_type == "application/grpc" or media_type.startswith(("application/connect+", "application/grpc+"))


def _broken_first_message() -> ConnectError:
    return ConnectError(Code.INVALID_ARGUMENT, "streaming request ends inside its first message")


def _first_envelope(body: bytes) -> bytes:
    if not body:
        return b""
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(body[1:_ENVELOPE_PREFIX_SIZE], "big")
    if len(body) < _ENVELOPE_PREFIX_SIZE or len(body) < size:
        raise _broken_first_message()
    return body[:size]


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
    """Deducts the time a sync source took to yield what is signed. A blocked source is not
    interrupted: once it yields with no time left, this raises TimeoutError and nothing is sent."""
    if timeout is None:
        return None
    timeout -= time.monotonic() - started
    if timeout <= 0:
        raise TimeoutError(f"timed out waiting for {waited_for}")
    return timeout


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
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.Client(transport=transport) if transport else pyqwest.Client()
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
        content: bytes | AsyncIterator[bytes] | None = None,
    ) -> AbstractAsyncContextManager[pyqwest.Response]:
        enveloped = _is_enveloped(headers)
        if content is None or isinstance(content, (bytes, bytearray, memoryview)):
            body = bytes(content or b"")
            headers = _sign_request(self._sign_fn, _first_envelope(body) if enveloped else body, headers)
            return self._inner.stream(method, url, headers=headers, content=content)
        if enveloped:
            return self._stream_signing_first_envelope(method, url, headers, aiter(content))
        return self._stream_signing_whole_body(method, url, headers, aiter(content))

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

    @asynccontextmanager
    async def _stream_signing_whole_body(
        self, method: str, url: str, headers: pyqwest.Headers | None, source: AsyncIterator[bytes]
    ) -> AsyncIterator[pyqwest.Response]:
        try:
            body = b"".join([chunk async for chunk in source])
            headers = _sign_request(self._sign_fn, body, headers)
        finally:
            await _aclose(source)
        async with self._inner.stream(method, url, headers=headers, content=body) as response:
            yield response


class SigningSyncClient:
    """Sync signing wrapper for pyqwest.SyncClient.

    Passed to ConnectRPC sync client via http_client= parameter.
    Signs post() and stream(); get() is refused, since a GET has no body to sign.

    A streaming request is sent once its first message is available: send one (or close the
    stream) before waiting for a response. Bidirectional streams are not supported; only the
    factory-built clients reject them.

    A blocked source is not interrupted: the time it takes to yield the first message (for a
    whole-body iterator, the whole body) is deducted from the call's timeout, and if none is left,
    nothing is sent, the source is closed and the call fails with TimeoutError (DEADLINE_EXCEEDED
    in connectrpc). Bounding the time of each read is up to the source. See docs/STREAMING.md.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.SyncClient(transport=transport) if transport else pyqwest.SyncClient()
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
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return self._inner.post(url, headers=headers, content=content, timeout=timeout)

    def stream(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | Iterable[bytes] | None = None,
        timeout: float | None = None,
    ) -> AbstractContextManager[pyqwest.SyncResponse]:
        enveloped = _is_enveloped(headers)
        if content is None or isinstance(content, (bytes, bytearray, memoryview)):
            body = bytes(content or b"")
            headers = _sign_request(self._sign_fn, _first_envelope(body) if enveloped else body, headers)
            return self._inner.stream(method, url, headers=headers, content=content, timeout=timeout)
        if enveloped:
            return self._stream_signing_first_envelope(method, url, headers, iter(content), timeout)
        return self._stream_signing_whole_body(method, url, headers, iter(content), timeout)

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
            timeout = _remaining_timeout(timeout, started, "the first request message")
            headers = _sign_request(self._sign_fn, first or b"", headers)
        except BaseException:
            _close(source)
            raise
        content = _SyncChain(first, source)
        with self._inner.stream(method, url, headers=headers, content=content, timeout=timeout) as response:
            yield response

    @contextmanager
    def _stream_signing_whole_body(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None,
        source: Iterator[bytes],
        timeout: float | None,
    ) -> Iterator[pyqwest.SyncResponse]:
        started = time.monotonic()
        try:
            body = b"".join(source)
            timeout = _remaining_timeout(timeout, started, "the request body")
            headers = _sign_request(self._sign_fn, body, headers)
        finally:
            _close(source)
        with self._inner.stream(method, url, headers=headers, content=body, timeout=timeout) as response:
            yield response
