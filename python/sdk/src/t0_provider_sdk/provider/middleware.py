"""ASGI middleware for T-0 Network signature verification.

This middleware intercepts the raw request body BEFORE ConnectRPC deserializes it,
verifies the cryptographic signature, and stores any errors in contextvars for the
ConnectRPC interceptor to convert into proper error responses.

Go equivalent: provider/verify_signature.go → newSignatureVerifierMiddleware()

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
from dataclasses import dataclass
from typing import Any, Callable

from coincurve import PublicKey

from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import public_key_from_bytes, public_key_from_hex
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

# Context variable for passing signature errors from middleware to interceptor.
# Go equivalent: context.WithValue(ctx, signatureErrorContextKey{}, errObj)
signature_error_var: contextvars.ContextVar[SignatureVerificationError | None] = contextvars.ContextVar(
    "signature_error", default=None
)

# Default max body size (4 MB), matching Go SDK
DEFAULT_MAX_BODY_SIZE = 4 * 1024 * 1024

# Timestamp tolerance: ±60 seconds
TIMESTAMP_TOLERANCE_MS = 60_000


@dataclass(frozen=True)
class VerifySignatureFn:
    """Verifies a signature against the expected network public key."""

    network_public_key: PublicKey

    def __call__(self, public_key_bytes: bytes, message: bytes, signature: bytes) -> None:
        """Verify signature, raising appropriate errors on failure.

        Go equivalent: newVerifySignature() closure
        """
        if len(signature) < 64 or len(signature) > 65:
            raise SignatureFailedError()

        signer_public_key = public_key_from_bytes(public_key_bytes)
        if signer_public_key.format(compressed=False) != self.network_public_key.format(compressed=False):
            raise UnknownPublicKeyError()

        digest = legacy_keccak256(message)
        if not verify_signature(signer_public_key, digest, signature[:64]):
            raise SignatureFailedError()


def new_verify_signature(network_public_key_hex: str) -> VerifySignatureFn:
    """Create a signature verification function bound to a network public key."""
    network_public_key = public_key_from_hex(network_public_key_hex)
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

        # Read the full body
        try:
            body = await _read_body(receive, max_body_size)
        except BodyTooLargeError as e:
            signature_error_var.set(e)
            await app(scope, _replay_receive(b""), send)
            return

        # Parse and verify
        error = _verify_request(verify_fn, headers, body)
        signature_error_var.set(error)

        # Replay body to downstream
        await app(scope, _replay_receive(body), send)

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
    """Parse a hex-encoded header value, stripping the 0x prefix.

    Go equivalent: parseRequiredHexedHeader()
    """
    header_key = header_name.lower()
    value = headers.get(header_key, "")
    if not value:
        raise MissingRequiredHeaderError(header_name)
    if len(value) < 2:
        raise InvalidHeaderEncodingError(header_name)
    try:
        return bytes.fromhex(value[2:])  # strip "0x"
    except ValueError:
        raise InvalidHeaderEncodingError(header_name)


def _parse_timestamp(headers: dict[str, str]) -> tuple[int, bytes]:
    """Parse the timestamp header and return (milliseconds, LE 8-byte encoding).

    Go equivalent: parseTimestamp()
    """
    header_key = SIGNATURE_TIMESTAMP_HEADER.lower()
    value = headers.get(header_key, "")
    if not value:
        raise MissingRequiredHeaderError(SIGNATURE_TIMESTAMP_HEADER)
    try:
        timestamp_ms = int(value)
    except ValueError:
        raise InvalidHeaderEncodingError(SIGNATURE_TIMESTAMP_HEADER)
    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    return timestamp_ms, timestamp_bytes


async def _read_body(receive: ASGIReceive, max_size: int) -> bytes:
    """Read the full request body from ASGI receive, enforcing size limit.

    Go equivalent: readBodyWithCap()
    """
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
