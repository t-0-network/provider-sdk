"""ASGI middleware for T-0 Network signature verification.

This middleware intercepts the raw request body BEFORE ConnectRPC deserializes it,
verifies the cryptographic signature, and stores any errors in contextvars for the
ConnectRPC interceptor to convert into proper error responses.

Architecture:
    ASGI Request → SignatureVerificationMiddleware → ConnectRPC ASGI App
                          ↓                                ↓
                   Read raw body                    SignatureErrorInterceptor
                   Verify signature                 (reads error from contextvars)
                   Store error in contextvars
                   Replay body to downstream
"""

from __future__ import annotations

import asyncio
import contextvars
import struct
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from coincurve import PublicKey

from t0_provider_sdk._messages import (
    NETWORK_PUBLIC_KEY_INVALID,
    TIMESTAMP_NOT_DECIMAL,
    TIMESTAMP_OUT_OF_RANGE,
)
from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import _decode_hex_strict, _parse_public_key, _public_key_from_bytes_strict
from t0_provider_sdk.crypto.verifier import verify_signature
from t0_provider_sdk.provider.errors import (
    BodyTooLargeError,
    InvalidHeaderEncodingError,
    InvalidTimestampError,
    MissingRequiredHeaderError,
    NetworkPublicKeyRequiredError,
    SignatureFailedError,
    SignatureVerificationError,
    TimestampOutOfRangeError,
    UnknownPublicKeyError,
)


class _NotVerified:
    """The value of signature_error_var where the middleware has not set it: the request was not verified."""


NOT_VERIFIED = _NotVerified()

# Context variable for passing signature errors from middleware to interceptor: None when the
# signature is valid, NOT_VERIFIED where the middleware did not run, so the interceptor refuses the call.
signature_error_var: contextvars.ContextVar[SignatureVerificationError | _NotVerified | None] = contextvars.ContextVar(
    "signature_error", default=NOT_VERIFIED
)

# The largest request body a provider server accepts unless max_body_size sets another: the whole
# HTTP body of a unary call, gRPC prefix included.
DEFAULT_MAX_BODY_SIZE = 10 * 1024 * 1024  # 10 MiB

# How far X-Signature-Timestamp may be from the server's clock, either way. Fixed: no option changes it.
TIMESTAMP_WINDOW_MS = 60_000


@dataclass(frozen=True)
class VerifySignatureFn:
    """The network public key the middleware verifies requests against.

    Built by new_verify_signature, the only way to build one that the middleware accepts.
    """

    network_public_key: PublicKey

    def __call__(self, public_key_bytes: bytes, message: bytes, signature: bytes) -> None:
        """Verify signature, raising appropriate errors on failure.

        The checks of the server after the timestamp window, in its order (rule V10): the public
        key is the network key (else UnknownPublicKeyError), the signature is 64 or 65 bytes, and it
        verifies over Keccak-256(message) (else SignatureFailedError)."""
        _check_signer(self.network_public_key, public_key_bytes, signature)
        if not _verifies(self.network_public_key, message, signature):
            raise SignatureFailedError()


def _check_signer(network_public_key: PublicKey, public_key_bytes: bytes, signature: bytes) -> None:
    """What the server checks between the timestamp window and the body (rule V10): that the X-Public-Key
    bytes are the network key, then that the signature is 64 or 65 bytes. VerifySignatureFn runs the
    same checks, so the two refuse a request alike."""
    try:
        signer_public_key = _public_key_from_bytes_strict(public_key_bytes)
    except ValueError:
        # Bytes that are not a key cannot be the network key either.
        raise UnknownPublicKeyError()
    # Compared as points: the compressed and the uncompressed form of the network key both match.
    if signer_public_key.format(compressed=False) != network_public_key.format(compressed=False):
        raise UnknownPublicKeyError()
    if len(signature) not in (64, 65):
        raise SignatureFailedError()


def _verifies(network_public_key: PublicKey, message: bytes, signature: bytes) -> bool:
    """Whether the signature, without its recovery byte, is the network key's over Keccak-256(message)."""
    return verify_signature(network_public_key, legacy_keccak256(message), signature[:64])


# A verify_fn of the caller's own, which the middlewares also take: called with the X-Public-Key bytes,
# the signed message (the body, then the timestamp bytes) and the X-Signature bytes, it raises a
# SignatureVerificationError to refuse the request.
CustomVerifyFn = Callable[[bytes, bytes, bytes], None]


def new_verify_signature(network_public_key_hex: str) -> VerifySignatureFn:
    """Parse the network public key, once, at startup.

    Surrounding whitespace is trimmed. Raises NetworkPublicKeyRequiredError for an empty or
    whitespace-only key, and ValueError ("invalid network public key: ...") for a malformed one.
    """
    key = (network_public_key_hex or "").strip()
    if not key:
        raise NetworkPublicKeyRequiredError()
    try:
        network_public_key = _parse_public_key(key)
    except ValueError as e:
        raise ValueError(NETWORK_PUBLIC_KEY_INVALID.format(reason=e)) from e
    return VerifySignatureFn(network_public_key=network_public_key)


def _body_limit(max_body_size: int) -> int:
    """The body limit for a max_body_size: 0 or less keeps DEFAULT_MAX_BODY_SIZE."""
    return max_body_size if max_body_size > 0 else DEFAULT_MAX_BODY_SIZE


ASGIApp = Callable[..., Any]
ASGIReceive = Callable[..., Any]
ASGISend = Callable[..., Any]
Scope = dict[str, Any]


def signature_verification_middleware(
    app: ASGIApp,
    verify_fn: VerifySignatureFn | CustomVerifyFn,
    max_body_size: int = DEFAULT_MAX_BODY_SIZE,
) -> ASGIApp:
    """Wrap an ASGI app with signature verification middleware.

    The middleware checks the signature headers, then reads the body (at most max_body_size
    bytes) and verifies the signature over it, all before the app reads anything. An accepted
    request goes on with its body; a rejected one goes on with an empty request message in place of
    its body, so the RPC library decodes nothing the caller sent, and the interceptor answers it
    with the rejection's code and message (signature_error_var). That answer is sent at once, without
    waiting for the rest of the body.

    verify_fn is the one new_verify_signature builds, or a CustomVerifyFn, which then decides the
    key and the signature after the body is read. A max_body_size of 0 or less keeps
    DEFAULT_MAX_BODY_SIZE.
    """
    max_body_size = _body_limit(max_body_size)

    async def middleware(scope: Scope, receive: ASGIReceive, send: ASGISend) -> None:
        if scope["type"] != "http":
            await app(scope, receive, send)
            return

        headers = _parse_scope_headers(scope)
        error: SignatureVerificationError | None = None
        reader = _BodyReader(receive)
        try:
            signed = _check_headers(verify_fn, headers)
            body = await reader.read(headers, max_body_size)
            _verify_body(verify_fn, headers, body, signed)
        except SignatureVerificationError as e:
            error = e
            body = _rejection_body(headers)
            scope = _rejection_scope(scope, len(body))
            if not reader.done:
                # The answer goes out at once, never after the rest of the body; what the caller
                # is still sending is read after it (_AnswerThenDrain).
                send = _AnswerThenDrain(scope, send, reader, _DRAIN_LIMIT_FACTOR * max_body_size)

        # The result is reset when the request ends, so a server that serves another request in
        # this context never finds it there.
        token = signature_error_var.set(error)
        try:
            await app(scope, _replay_receive(body), send)
        finally:
            signature_error_var.reset(token)

    return middleware


# What the headers give the body check: the X-Signature bytes, the timestamp bytes, and the
# X-Public-Key bytes.
_Signed = tuple[bytes, bytes, bytes]


def _check_headers(verify_fn: VerifySignatureFn | CustomVerifyFn, headers: dict[str, str]) -> _Signed:
    """Everything the signature headers alone decide, in the order of every SDK: the three are
    present and well-formed, the timestamp window, that the public key is the network key, and the
    signature length. Returns what the body check needs, or raises the rejection."""
    public_key_value = headers.get(PUBLIC_KEY_HEADER.lower(), "")
    if not public_key_value:
        raise MissingRequiredHeaderError(PUBLIC_KEY_HEADER)
    signature_value = headers.get(SIGNATURE_HEADER.lower(), "")
    if not signature_value:
        raise MissingRequiredHeaderError(SIGNATURE_HEADER)
    try:
        signature = _decode_hex_strict(signature_value)
    except ValueError:
        raise InvalidHeaderEncodingError(SIGNATURE_HEADER)
    timestamp_ms, timestamp_bytes = _parse_timestamp(headers)
    if abs(int(time.time() * 1000) - timestamp_ms) > TIMESTAMP_WINDOW_MS:
        raise TimestampOutOfRangeError()
    # A value that is not hex is not the network key either.
    try:
        public_key = _decode_hex_strict(public_key_value)
    except ValueError:
        raise UnknownPublicKeyError()
    # A CustomVerifyFn decides the key and the length with the signature, after the body is read.
    if isinstance(verify_fn, VerifySignatureFn):
        _check_signer(verify_fn.network_public_key, public_key, signature)
    return signature, timestamp_bytes, public_key


def _parse_timestamp(headers: dict[str, str]) -> tuple[int, bytes]:
    """The X-Signature-Timestamp header in milliseconds, and its 8-byte little-endian encoding."""
    value = headers.get(SIGNATURE_TIMESTAMP_HEADER.lower(), "")
    if not value:
        raise MissingRequiredHeaderError(SIGNATURE_TIMESTAMP_HEADER)
    # ASCII digits only: int() also takes a sign, spaces, underscores and other scripts' digits.
    if not (value.isascii() and value.isdigit()):
        raise InvalidTimestampError(TIMESTAMP_NOT_DECIMAL)
    # At most 19 significant digits fit a signed 64-bit integer; int() refuses an overlong string.
    significant = value.lstrip("0")
    if len(significant) > 19 or int(significant or "0") >= 2**63:
        raise InvalidTimestampError(TIMESTAMP_OUT_OF_RANGE)
    timestamp_ms = int(significant or "0")
    return timestamp_ms, struct.pack("<Q", timestamp_ms)


def _verify_body(
    verify_fn: VerifySignatureFn | CustomVerifyFn, headers: dict[str, str], body: bytes, signed: _Signed
) -> None:
    """The signature covers the whole body; failing that, for gRPC, a body of exactly one
    uncompressed frame without its 5-byte prefix, as the Java SDK signs it (rule V6)."""
    if not isinstance(verify_fn, VerifySignatureFn):
        _call_custom_verify_fn(verify_fn, headers, body, signed)
        return
    network_public_key = verify_fn.network_public_key
    signature, timestamp_bytes, _ = signed
    if _verifies(network_public_key, body + timestamp_bytes, signature):
        return
    payload = _grpc_frame_payload(headers, body)
    if payload is not None and _verifies(network_public_key, payload + timestamp_bytes, signature):
        return
    raise SignatureFailedError()


def _call_custom_verify_fn(verify_fn: CustomVerifyFn, headers: dict[str, str], body: bytes, signed: _Signed) -> None:
    """A CustomVerifyFn over the whole body; failing that, over the message of a gRPC body of
    exactly one uncompressed frame (rule V6). Its first refusal is the one raised."""
    signature, timestamp_bytes, public_key = signed
    try:
        verify_fn(public_key, body + timestamp_bytes, signature)
        return
    except SignatureVerificationError as e:
        refusal = e
    payload = _grpc_frame_payload(headers, body)
    if payload is not None:
        try:
            verify_fn(public_key, payload + timestamp_bytes, signature)
            return
        except SignatureVerificationError:
            pass
    raise refusal


def _is_grpc(headers: dict[str, str]) -> bool:
    content_type = headers.get("content-type", "").lower()
    return content_type == "application/grpc" or content_type.startswith(("application/grpc+", "application/grpc;"))


def _rejection_body(headers: dict[str, str]) -> bytes:
    """The body replayed in place of a rejected one: it decodes to an empty request message, so the
    RPC library reaches the interceptor, which answers with the rejection, whatever the caller sent."""
    content_type = headers.get("content-type", "").lower()
    if content_type.startswith(("application/grpc+json", "application/connect+json")):
        return b"\x00\x00\x00\x00\x02{}"  # one uncompressed frame holding {}
    if _is_grpc(headers) or content_type.startswith("application/connect+"):
        return b"\x00\x00\x00\x00\x00"  # one uncompressed frame holding an empty message
    if content_type.startswith("application/json"):
        return b"{}"
    return b""


# Request headers that describe the body as sent; the replayed rejection body is neither compressed
# nor of the sent length.
_BODY_HEADERS = (b"content-length", b"content-encoding", b"connect-content-encoding", b"grpc-encoding")


def _rejection_scope(scope: Scope, length: int) -> Scope:
    headers = [(k, v) for k, v in scope.get("headers", []) if k.lower() not in _BODY_HEADERS]
    headers.append((b"content-length", str(length).encode("latin-1")))
    return {**scope, "headers": headers}


def _grpc_frame_payload(headers: dict[str, str], body: bytes) -> bytes | None:
    """The message of a gRPC body that is exactly one uncompressed frame, else None."""
    if not _is_grpc(headers):
        return None
    if len(body) < 5 or body[0] != 0 or int.from_bytes(body[1:5], "big") != len(body) - 5:
        return None
    return body[5:]


def _parse_scope_headers(scope: Scope) -> dict[str, str]:
    """Extract headers from ASGI scope into a case-insensitive dict."""
    result: dict[str, str] = {}
    for key_bytes, value_bytes in scope.get("headers", []):
        key = key_bytes.decode("latin-1").lower()
        result[key] = value_bytes.decode("latin-1")
    return result


def _declared_length(headers: dict[str, str]) -> int | None:
    value = headers.get("content-length", "")
    return int(value) if value.isascii() and value.isdigit() else None


# How much of a rejected request's body is read and discarded after the answer: at most this
# multiple of the body limit, for at most this many seconds.
_DRAIN_LIMIT_FACTOR = 4
_DRAIN_TIMEOUT_SECONDS = 1.0


class _BodyReader:
    """Reads the request body from ASGI receive."""

    def __init__(self, receive: ASGIReceive) -> None:
        self._receive = receive
        self._done = False

    @property
    def done(self) -> bool:
        """Whether the body has ended, or the client has gone."""
        return self._done

    async def _next(self) -> bytes:
        message = await self._receive()
        if message.get("type") == "http.disconnect" or not message.get("more_body", False):
            self._done = True
        return bytes(message.get("body", b""))

    async def read(self, headers: dict[str, str], max_size: int) -> bytes:
        """The whole body, at most max_size bytes: a declared Content-Length over the limit is
        refused before anything is read, and an undeclared one as soon as the bytes read pass it."""
        declared = _declared_length(headers)
        if declared is not None and declared > max_size:
            raise BodyTooLargeError(max_size)
        body = bytearray()
        while not self._done:
            body.extend(await self._next())
            if len(body) > max_size:
                raise BodyTooLargeError(max_size)
        return bytes(body)

    async def drain(self, limit: int) -> None:
        """Reads and discards what is left of the body, up to limit bytes and for at most
        _DRAIN_TIMEOUT_SECONDS."""
        read = 0
        try:
            async with asyncio.timeout(_DRAIN_TIMEOUT_SECONDS):
                while not self._done and read <= limit:
                    read += len(await self._next())
        except TimeoutError:
            pass


class _AnswerThenDrain:
    """The send of a rejected request whose body may still be arriving.

    The answer goes out at once, all of it. Only the message that ends the response waits until
    what is left of the body is drained: an ASGI server stops reading the body when the response
    ends, and then an HTTP/1.1 client may find the connection reset under its upload before it
    reads the answer, and hypercorn fails the whole HTTP/2 connection on the stream's late data.

    A gRPC answer is its trailers, sent at once with more_trailers. An answer without trailers is
    held over HTTP/1.x only, and given a Content-Length, so that the client knows where it ends
    before the response ends. An HTTP/2 client waits for the end of the stream, so there the
    response ends at once and nothing is drained.
    """

    def __init__(self, scope: Scope, send: ASGISend, reader: _BodyReader, limit: int) -> None:
        self._send = send
        self._reader = reader
        self._limit = limit
        self._hold_body = scope.get("http_version", "1.1") in ("1.0", "1.1")
        self._start: dict[str, Any] | None = None
        self._body = bytearray()

    async def __call__(self, message: dict[str, Any]) -> None:
        kind = message.get("type")
        if kind == "http.response.start" and self._hold_body and not message.get("trailers", False):
            self._start = message  # sent with the whole body, to give it a Content-Length
            return
        if kind == "http.response.body" and self._start is not None:
            self._body.extend(message.get("body", b""))
            if message.get("more_body", False):
                return
            await self._send(_with_content_length(self._start, len(self._body)))
            await self._send({"type": "http.response.body", "body": bytes(self._body), "more_body": True})
            await self._reader.drain(self._limit)
            await self._send({"type": "http.response.body", "body": b"", "more_body": False})
            return
        if kind == "http.response.trailers" and not message.get("more_trailers", False):
            await self._send({**message, "more_trailers": True})
            await self._reader.drain(self._limit)
            await self._send({"type": "http.response.trailers", "headers": [], "more_trailers": False})
            return
        await self._send(message)


def _with_content_length(start: dict[str, Any], length: int) -> dict[str, Any]:
    headers = [
        (k, v) for k, v in start.get("headers", []) if k.lower() not in (b"content-length", b"transfer-encoding")
    ]
    headers.append((b"content-length", str(length).encode("latin-1")))
    return {**start, "headers": headers}


def _replay_receive(body: bytes) -> ASGIReceive:
    """Create a synthetic ASGI receive that replays buffered body bytes."""
    sent = False

    async def receive() -> dict[str, Any]:
        nonlocal sent
        if not sent:
            sent = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    return receive
