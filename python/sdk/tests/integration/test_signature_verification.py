"""End-to-end integration test: signing transport → ASGI middleware → interceptor.

Creates a real ASGI app with signature verification middleware,
sends a signed request, and verifies it succeeds end-to-end.
"""

import struct
import time

import pytest

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer, new_signer_from_hex
from t0_provider_sdk.network.signing import _sign_request
from t0_provider_sdk.provider.middleware import (
    new_verify_signature,
    signature_error_var,
    signature_verification_middleware,
)

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"


async def _send_signed_request_through_middleware(
    body: bytes,
    private_key: str,
    network_public_key: str,
) -> tuple[bytes | None, Exception | None]:
    """Create a signed request and send it through the middleware.

    Returns (received_body, signature_error).
    """
    # Sign the request (this is what the client side does)
    sign_fn = new_signer_from_hex(private_key)
    headers = _sign_request(sign_fn, body, None)

    # Convert to ASGI scope headers
    scope_headers = [(k.lower().encode(), str(v).encode()) for k, v in headers.items()]
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/test",
        "headers": scope_headers,
    }

    # Track what the downstream app sees
    received_body = None
    received_error = None

    async def downstream_app(scope, receive, send):
        nonlocal received_body, received_error
        received_error = signature_error_var.get()
        msg = await receive()
        received_body = msg.get("body", b"")

    # Create middleware
    verify_fn = new_verify_signature(network_public_key)
    app = signature_verification_middleware(downstream_app, verify_fn)

    # Send request
    body_sent = False
    async def receive():
        nonlocal body_sent
        if not body_sent:
            body_sent = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    async def send(message):
        pass

    await app(scope, receive, send)
    return received_body, received_error


@pytest.mark.asyncio
class TestEndToEndSignatureVerification:
    async def test_valid_signed_request_succeeds(self):
        """Client signs → server verifies → success."""
        body = b"payment request data"
        received_body, error = await _send_signed_request_through_middleware(
            body, PRIVATE_KEY, PUBLIC_KEY
        )
        assert error is None
        assert received_body == body

    async def test_wrong_key_fails(self):
        """Client signs with wrong key → server rejects."""
        body = b"payment request data"
        received_body, error = await _send_signed_request_through_middleware(
            body, OTHER_PRIVATE_KEY, PUBLIC_KEY
        )
        assert error is not None
        assert "unknown public key" in str(error)

    async def test_empty_body_succeeds(self):
        """Empty body signed request → success."""
        received_body, error = await _send_signed_request_through_middleware(
            b"", PRIVATE_KEY, PUBLIC_KEY
        )
        assert error is None
        assert received_body == b""

    async def test_large_body_succeeds(self):
        """Large body signed request → success."""
        body = b"x" * 100_000
        received_body, error = await _send_signed_request_through_middleware(
            body, PRIVATE_KEY, PUBLIC_KEY
        )
        assert error is None
        assert received_body == body

    async def test_body_integrity(self):
        """Body is not modified during signing/verification."""
        body = bytes(range(256)) * 10  # Various byte values
        received_body, error = await _send_signed_request_through_middleware(
            body, PRIVATE_KEY, PUBLIC_KEY
        )
        assert error is None
        assert received_body == body
