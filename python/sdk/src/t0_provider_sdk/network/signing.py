"""Signing HTTP transport wrappers for pyqwest Client and SyncClient.

These wrappers intercept outgoing requests to add T-0 Network signature headers
before delegating to the underlying pyqwest client. ConnectRPC uses exactly
three methods on the client: get(), post(), and stream().

What is signed depends on the request's content type:

- Enveloped requests (Connect streaming, ``application/connect+*``, and gRPC, ``application/grpc``
  and ``application/grpc+*``) whose body is an iterator: only the first envelope, exactly as
  sent -- flags, 4-byte big-endian length and payload. The request goes out as soon as that
  envelope is available; later messages are forwarded as they come and are not covered by the
  signature. gRPC unary requests are a single envelope, so for them this is the whole body.
- Everything else (Connect unary ``application/proto`` and ``application/json``, GET, gRPC-Web):
  the whole body.

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
    """Reports whether the body is a sequence of enveloped messages (Connect streaming or gRPC,
    not gRPC-Web), in which case the signature covers only the first envelope."""
    if headers is None:
        return False
    media_type = headers.get("content-type", "").split(";", 1)[0].strip().lower()
    return (
        media_type.startswith("application/connect+")
        or media_type == "application/grpc"
        or media_type.startswith("application/grpc+")
    )


def _first_envelope_size(buf: bytearray) -> int | None:
    """Size of the envelope at the start of buf, or None until buf holds all of it."""
    if len(buf) < _ENVELOPE_PREFIX_SIZE:
        return None
    size = _ENVELOPE_PREFIX_SIZE + int.from_bytes(buf[1:_ENVELOPE_PREFIX_SIZE], "big")
    return size if len(buf) >= size else None


async def _read_first_envelope(source: AsyncIterator[bytes]) -> tuple[bytes, bytes]:
    """Reads source up to the end of its first envelope. Returns that envelope and any bytes read
    past it.

    connectrpc yields one whole envelope per chunk, so this is normally a single read. A body that
    ends before its first envelope is complete (b"" for a client stream closed before its first
    message) is returned as it is, to be signed and sent; the server rejects it.
    """
    buf = bytearray()
    while (size := _first_envelope_size(buf)) is None:
        chunk = await anext(source, None)
        if chunk is None:
            return bytes(buf), b""
        buf += chunk
    return bytes(buf[:size]), bytes(buf[size:])


def _read_first_envelope_sync(source: Iterator[bytes]) -> tuple[bytes, bytes]:
    """Sync variant of _read_first_envelope."""
    buf = bytearray()
    while (size := _first_envelope_size(buf)) is None:
        chunk = next(source, None)
        if chunk is None:
            return bytes(buf), b""
        buf += chunk
    return bytes(buf[:size]), bytes(buf[size:])


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
    """Request body: the bytes already read from source, then the rest of source as it comes.

    pyqwest closes only the iterator it is given, so closing the chain closes source.
    """

    def __init__(self, head: list[bytes], source: AsyncIterator[bytes]) -> None:
        self._head = [chunk for chunk in head if chunk]
        self._source = source

    def __aiter__(self) -> _AsyncChain:
        return self

    async def __anext__(self) -> bytes:
        if self._head:
            return self._head.pop(0)
        return await anext(self._source)

    async def aclose(self) -> None:
        await _aclose(self._source)


class _SyncChain:
    """Sync variant of _AsyncChain."""

    def __init__(self, head: list[bytes], source: Iterator[bytes]) -> None:
        self._head = [chunk for chunk in head if chunk]
        self._source = source

    def __iter__(self) -> _SyncChain:
        return self

    def __next__(self) -> bytes:
        if self._head:
            return self._head.pop(0)
        return next(self._source)

    def close(self) -> None:
        _close(self._source)


class SigningClient:
    """Async signing wrapper for pyqwest.Client.

    Passed to ConnectRPC async client via http_client= parameter.
    Intercepts get(), post(), stream() to add signature headers.

    A streaming request is sent only when its first message is available, so send a message (or
    close the stream) before waiting for a response. Bidirectional streams are not supported.
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
        if content is None or isinstance(content, (bytes, bytearray, memoryview)):
            headers = _sign_request(self._sign_fn, bytes(content or b""), headers)
            return self._inner.stream(method, url, headers=headers, content=content)
        if _is_enveloped(headers):
            return self._stream_signing_first_envelope(method, url, headers, aiter(content))
        return self._stream_signing_whole_body(method, url, headers, aiter(content))

    # The body is read when the context is entered, which connectrpc does inside its call timeout.

    @asynccontextmanager
    async def _stream_signing_first_envelope(
        self, method: str, url: str, headers: pyqwest.Headers | None, source: AsyncIterator[bytes]
    ) -> AsyncIterator[pyqwest.Response]:
        try:
            envelope, excess = await _read_first_envelope(source)
            headers = _sign_request(self._sign_fn, envelope, headers)
        except BaseException:
            await _aclose(source)
            raise
        content = _AsyncChain([envelope, excess], source)
        async with self._inner.stream(method, url, headers=headers, content=content) as response:
            yield response

    @asynccontextmanager
    async def _stream_signing_whole_body(
        self, method: str, url: str, headers: pyqwest.Headers | None, source: AsyncIterator[bytes]
    ) -> AsyncIterator[pyqwest.Response]:
        try:
            body = b"".join([chunk async for chunk in source])
        finally:
            await _aclose(source)
        headers = _sign_request(self._sign_fn, body, headers)
        async with self._inner.stream(method, url, headers=headers, content=body) as response:
            yield response


class SigningSyncClient:
    """Sync signing wrapper for pyqwest.SyncClient.

    Passed to ConnectRPC sync client via http_client= parameter.
    Intercepts get(), post(), stream() to add signature headers.

    A streaming request is sent only when its first message is available, so send a message (or
    close the stream) before waiting for a response. Bidirectional streams are not supported.
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
        if content is None or isinstance(content, (bytes, bytearray, memoryview)):
            headers = _sign_request(self._sign_fn, bytes(content or b""), headers)
            return self._inner.stream(method, url, headers=headers, content=content, timeout=timeout)
        if _is_enveloped(headers):
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
            envelope, excess = _read_first_envelope_sync(source)
            if timeout is not None:
                # The timeout covers the wait for the first message too, as it does for async calls.
                timeout -= time.monotonic() - started
                if timeout <= 0:
                    raise TimeoutError("timed out waiting for the first request message")
            headers = _sign_request(self._sign_fn, envelope, headers)
        except BaseException:
            _close(source)
            raise
        content = _SyncChain([envelope, excess], source)
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
        try:
            body = b"".join(source)
        finally:
            _close(source)
        headers = _sign_request(self._sign_fn, body, headers)
        with self._inner.stream(method, url, headers=headers, content=body, timeout=timeout) as response:
            yield response
