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

import contextvars
import struct
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from coincurve import PublicKey

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
    MissingRequiredHeaderError,
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

# Default max body size (10 MiB), matching Go SDK and T-0 Network
DEFAULT_MAX_BODY_SIZE = 10 * 1024 * 1024

# Timestamp tolerance: ±60 seconds
TIMESTAMP_TOLERANCE_MS = 60_000


@dataclass(frozen=True)
class VerifySignatureFn:
    """Verifies a signature against the expected network public key."""

    network_public_key: PublicKey

    def __call__(self, public_key_bytes: bytes, message: bytes, signature: bytes) -> None:
        """Verify signature, raising appropriate errors on failure."""
        if len(signature) < 64 or len(signature) > 65:
            raise SignatureFailedError()

        try:
            signer_public_key = _public_key_from_bytes_strict(public_key_bytes)
        except ValueError:
            # Hex, but not a key: it cannot be the network's either.
            raise UnknownPublicKeyError()
        # Compared as points: the compressed and the uncompressed form of the network key both match.
        if signer_public_key.format(compressed=False) != self.network_public_key.format(compressed=False):
            raise UnknownPublicKeyError()

        digest = legacy_keccak256(message)
        if not verify_signature(signer_public_key, digest, signature[:64]):
            raise SignatureFailedError()


def new_verify_signature(network_public_key_hex: str) -> VerifySignatureFn:
    """Create a signature verification function bound to a network public key."""
    network_public_key = _parse_public_key(network_public_key_hex)
    return VerifySignatureFn(network_public_key=network_public_key)


ASGIApp = Callable[..., Any]
ASGIReceive = Callable[..., Any]
ASGISend = Callable[..., Any]
Scope = dict[str, Any]


def signature_verification_middleware(
    app: ASGIApp,
    verify_fn: VerifySignatureFn,
    max_body_size: int = DEFAULT_MAX_BODY_SIZE,
) -> ASGIApp:
    """Wrap an ASGI app with signature verification middleware.

    The middleware:
    1. Buffers the entire request body from ASGI receive
    2. Parses signature headers (X-Public-Key, X-Signature, X-Signature-Timestamp)
    3. Validates timestamp within ±60 seconds
    4. Verifies the signature against the network public key
    5. Stores any error in contextvars.ContextVar for the interceptor
    6. Replays the buffered body via a synthetic receive callable
    """

    async def middleware(scope: Scope, receive: ASGIReceive, send: ASGISend) -> None:
        if scope["type"] != "http":
            await app(scope, receive, send)
            return

        headers = _parse_scope_headers(scope)

        # Read the full body, then parse and verify
        error: SignatureVerificationError | None
        try:
            body = await _read_body(receive, max_body_size)
            error = _verify_request(verify_fn, headers, body)
        except BodyTooLargeError as e:
            body, error = b"", e

        # Replay body to downstream. The result is reset when the request ends, so a server that
        # serves another request in this context never finds it there.
        token = signature_error_var.set(error)
        try:
            await app(scope, _replay_receive(body), send)
        finally:
            signature_error_var.reset(token)

    return middleware


def _verify_request(
    verify_fn: VerifySignatureFn,
    headers: dict[str, str],
    body: bytes,
) -> SignatureVerificationError | None:
    """Parse headers and verify signature, returning error or None on success."""
    try:
        public_key = _parse_hex_header(headers, PUBLIC_KEY_HEADER)
        sig = _parse_hex_header(headers, SIGNATURE_HEADER)
        timestamp_ms, timestamp_bytes = _parse_timestamp(headers)
    except SignatureVerificationError as e:
        return e

    # Check timestamp within tolerance
    now_ms = int(time.time() * 1000)
    if abs(now_ms - timestamp_ms) > TIMESTAMP_TOLERANCE_MS:
        return TimestampOutOfRangeError()

    # Verify signature: message = body + timestamp_le_bytes
    message = body + timestamp_bytes
    try:
        verify_fn(public_key, message, sig)
    except SignatureVerificationError as e:
        return e

    return None


def _parse_scope_headers(scope: Scope) -> dict[str, str]:
    """Extract headers from ASGI scope into a case-insensitive dict."""
    result: dict[str, str] = {}
    for key_bytes, value_bytes in scope.get("headers", []):
        key = key_bytes.decode("latin-1").lower()
        result[key] = value_bytes.decode("latin-1")
    return result


def _parse_hex_header(headers: dict[str, str], header_name: str) -> bytes:
    """Parse a hex-encoded header value with an optional 0x or 0X prefix; strict hex, not trimmed."""
    header_key = header_name.lower()
    value = headers.get(header_key, "")
    if not value:
        raise MissingRequiredHeaderError(header_name)
    try:
        return _decode_hex_strict(value)
    except ValueError:
        raise InvalidHeaderEncodingError(header_name)


def _parse_timestamp(headers: dict[str, str]) -> tuple[int, bytes]:
    """Parse the timestamp header and return (milliseconds, LE 8-byte encoding)."""
    header_key = SIGNATURE_TIMESTAMP_HEADER.lower()
    value = headers.get(header_key, "")
    if not value:
        raise MissingRequiredHeaderError(SIGNATURE_TIMESTAMP_HEADER)
    # ASCII digits only: int() also takes a sign, spaces, underscores and other scripts' digits. The
    # value must fit the signed 64-bit timestamp the network sends; int() refuses an overlong string.
    if not (value.isascii() and value.isdigit()):
        raise InvalidHeaderEncodingError(SIGNATURE_TIMESTAMP_HEADER)
    try:
        timestamp_ms = int(value)
    except ValueError:
        raise InvalidHeaderEncodingError(SIGNATURE_TIMESTAMP_HEADER)
    if timestamp_ms >= 2**63:
        raise InvalidHeaderEncodingError(SIGNATURE_TIMESTAMP_HEADER)
    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    return timestamp_ms, timestamp_bytes


async def _read_body(receive: ASGIReceive, max_size: int) -> bytes:
    """Read the full request body from ASGI receive, enforcing size limit."""
    body = bytearray()
    while True:
        message = await receive()
        chunk = message.get("body", b"")
        body.extend(chunk)
        if len(body) > max_size:
            raise BodyTooLargeError(max_size)
        if not message.get("more_body", False):
            break
    return bytes(body)


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
