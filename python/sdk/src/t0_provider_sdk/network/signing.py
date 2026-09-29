"""Signing HTTP transport wrappers for pyqwest Client and SyncClient.

These wrappers intercept outgoing requests to add T-0 Network signature headers
before delegating to the underlying pyqwest client. ConnectRPC uses exactly
three methods on the client: get(), post(), and stream().

What is signed depends on the request's content type -- its media type, parameters dropped and
case ignored -- as in every SDK, whatever form the body comes in:

- Enveloped requests, a sequence of envelopes: Connect streaming (``application/connect+*``) and
  gRPC (``application/grpc`` and ``application/grpc+*``). Only the first envelope is signed,
  exactly as sent -- flags, 4-byte big-endian length and payload. connectrpc hands these bodies
  over as an iterator yielding one envelope per message: the request goes out as soon as the first
  envelope is available, and later messages are forwarded as they come, not covered by the
  signature. A gRPC unary request is a single envelope, so for it this is the whole body. An
  enveloped body given as bytes is signed over the first envelope cut from it by its length prefix.
- Everything else: the whole body. That is Connect unary (``application/proto`` and
  ``application/json``, bytes), GET (no body) and gRPC-Web (``application/grpc-web*``), whose
  iterator body is read to its end, signed and sent as bytes.

Bidirectional streams are not supported; the clients built by network.client reject them.

Go equivalent: network/signing_transport.go → SigningTransport.RoundTrip(req)
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

    body is whatever the request's signature covers: the whole body, or the first envelope of a
    streaming request.
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
    """Whether the request body is a sequence of envelopes, of which only the first is signed.

    pyqwest.Headers looks names up case-insensitively. gRPC-Web (application/grpc-web*) is not
    enveloped here: the network signs and verifies its whole body.
    """
    content_type = headers.get("content-type") if headers is not None else None
    media_type = (content_type or "").partition(";")[0].strip().lower()
    return (
        media_type.startswith("application/connect+")
        or media_type == "application/grpc"
        or media_type.startswith("application/grpc+")
    )


def _first_envelope(body: bytes) -> bytes:
    """The first envelope of an enveloped body given as bytes; b"" for an empty body, as for a
    client stream closed before its first message. A body that ends inside its first envelope
    fails the call rather than being signed over bytes that are not that envelope."""
    if not body:
        return b""
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(body[1:_ENVELOPE_PREFIX_SIZE], "big")
    if len(body) < _ENVELOPE_PREFIX_SIZE or len(body) < size:
        raise ConnectError(Code.INTERNAL, "the request body ends inside its first envelope")
    return body[:size]


def _require_one_envelope(chunk: bytes) -> None:
    """connectrpc yields one complete envelope per chunk, so the first chunk is the first envelope.
    It is checked rather than trusted: a change in that framing must fail the call, never sign bytes
    that are not the first envelope."""
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(chunk[1:_ENVELOPE_PREFIX_SIZE], "big")
    if len(chunk) < _ENVELOPE_PREFIX_SIZE or len(chunk) != size:
        raise ConnectError(Code.INTERNAL, "the first request chunk is not one complete envelope")


def _remaining_timeout(timeout: float | None, started: float, waited_for: str) -> float | None:
    """What is left of a sync call's timeout after waiting since started for its body: the timeout
    covers that wait too, as it does for async calls."""
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
    # pyqwest writes the body on its own thread and closes it from the caller's. It skips a
    # generator that is running, since that cannot be closed reliably, but it cannot see the
    # generator behind the chain, so the chain does the same.
    if isinstance(source, types.GeneratorType) and inspect.getgeneratorstate(source) == inspect.GEN_RUNNING:
        return
    close = getattr(source, "close", None)
    if close is not None:
        close()


class _AsyncChain:
    """Request body: the first envelope, already read from source, then the rest of source as it
    comes.

    pyqwest closes only the iterator it is given, so closing the chain closes source.
    """

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
    Intercepts get(), post(), stream() to add signature headers.

    A streaming request is sent only when its first message is available, so send a message (or
    close the stream) before waiting for a response. Bidirectional streams are not supported: the
    clients built by new_service_client() reject them, and a client built on this wrapper by hand
    must not make them.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.Client(transport=transport) if transport else pyqwest.Client()
        self._sign_fn = sign_fn

    async def get(self, url: str, headers: pyqwest.Headers | None = None) -> Any:
        headers = _sign_request(self._sign_fn, b"", headers)
        return await self._inner.get(url, headers=headers)

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
            # A client stream closed before its first message has none: b"" is signed and sent,
            # and the network rejects it.
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
    Intercepts get(), post(), stream() to add signature headers.

    A streaming request is sent only when its first message is available, so send a message (or
    close the stream) before waiting for a response. Bidirectional streams are not supported: the
    clients built by new_service_client_sync() reject them, and a client built on this wrapper by
    hand must not make them.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.SyncClient(transport=transport) if transport else pyqwest.SyncClient()
        self._sign_fn = sign_fn

    def get(self, url: str, headers: pyqwest.Headers | None = None, timeout: float | None = None) -> Any:
        headers = _sign_request(self._sign_fn, b"", headers)
        return self._inner.get(url, headers=headers, timeout=timeout)

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
