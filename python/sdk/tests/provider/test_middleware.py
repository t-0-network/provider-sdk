"""Tests for ASGI signature verification middleware.

Mirrors Go's verify_signature_test.go test cases.
"""

import struct
import time

import pytest

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    new_verify_signature,
    signature_error_var,
    signature_verification_middleware,
)

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"


def _make_signed_request(
    body: bytes = b"test body",
    private_key: str = PRIVATE_KEY,
    timestamp_ms: int | None = None,
    override_headers: dict[str, str] | None = None,
) -> tuple[dict, bytes]:
    """Create a valid signed ASGI request scope and body."""
    key = private_key_from_hex(private_key)
    sign_fn = new_signer(key)

    if timestamp_ms is None:
        timestamp_ms = int(time.time() * 1000)

    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    digest = legacy_keccak256(body + timestamp_bytes)
    signature, pub_key = sign_fn(digest)

    headers = {
        "x-public-key": f"0x{pub_key.hex()}",
        "x-signature": f"0x{signature.hex()}",
        "x-signature-timestamp": str(timestamp_ms),
    }

    if override_headers:
        headers.update(override_headers)

    scope = {
        "type": "http",
        "method": "POST",
        "path": "/test",
        "headers": [(k.encode(), v.encode()) for k, v in headers.items()],
    }
    return scope, body


async def _run_middleware(scope: dict, body: bytes, network_key: str = PUBLIC_KEY, max_body_size: int = DEFAULT_MAX_BODY_SIZE):
    """Run the middleware and return the signature error (if any)."""
    verify_fn = new_verify_signature(network_key)

    captured_error = None

    async def downstream_app(scope, receive, send):
        nonlocal captured_error
        captured_error = signature_error_var.get()
        msg = await receive()
        # Only assert body equality when there's no error (valid requests)
        if captured_error is None:
            assert msg["body"] == body

    app = signature_verification_middleware(downstream_app, verify_fn, max_body_size)

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
    return captured_error


@pytest.mark.asyncio
class TestSignatureVerificationMiddleware:
    async def test_valid_request(self):
        """Valid request with all headers → success."""
        scope, body = _make_signed_request()
        error = await _run_middleware(scope, body)
        assert error is None

    async def test_valid_request_empty_body(self):
        """Empty body → success."""
        scope, body = _make_signed_request(body=b"")
        error = await _run_middleware(scope, body)
        assert error is None

    async def test_missing_public_key(self):
        """Missing X-Public-Key → error."""
        scope, body = _make_signed_request()
        # Remove public key header
        scope["headers"] = [(k, v) for k, v in scope["headers"] if k != b"x-public-key"]
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "missing required header" in str(error)

    async def test_missing_signature(self):
        """Missing X-Signature → error."""
        scope, body = _make_signed_request()
        scope["headers"] = [(k, v) for k, v in scope["headers"] if k != b"x-signature"]
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "missing required header" in str(error)

    async def test_missing_timestamp(self):
        """Missing X-Signature-Timestamp → error."""
        scope, body = _make_signed_request()
        scope["headers"] = [(k, v) for k, v in scope["headers"] if k != b"x-signature-timestamp"]
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "missing required header" in str(error)

    async def test_invalid_hex_encoding(self):
        """Invalid hex encoding → error."""
        scope, body = _make_signed_request(override_headers={"x-signature": "0xNOTHEX"})
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "invalid header encoding" in str(error)

    async def test_header_too_short(self):
        """Header value too short → error."""
        scope, body = _make_signed_request(override_headers={"x-signature": "0"})
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "invalid header encoding" in str(error)

    async def test_timestamp_too_old(self):
        """Timestamp >60s in the past → error."""
        old_ts = int(time.time() * 1000) - 120_000  # 2 minutes ago
        scope, body = _make_signed_request(timestamp_ms=old_ts)
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "time window" in str(error)

    async def test_timestamp_too_new(self):
        """Timestamp >60s in the future → error."""
        future_ts = int(time.time() * 1000) + 120_000  # 2 minutes ahead
        scope, body = _make_signed_request(timestamp_ms=future_ts)
        error = await _run_middleware(scope, body)
        assert error is not None
        assert "time window" in str(error)

    async def test_wrong_public_key(self):
        """Wrong public key → error."""
        scope, body = _make_signed_request()
        error = await _run_middleware(scope, body, network_key=OTHER_PUBLIC_KEY)
        assert error is not None
        assert "unknown public key" in str(error)

    async def test_invalid_signature(self):
        """Tampered signature → error."""
        scope, body = _make_signed_request()
        # Corrupt the signature
        for i, (k, v) in enumerate(scope["headers"]):
            if k == b"x-signature":
                corrupted = bytearray(bytes.fromhex(v.decode()[2:]))
                corrupted[0] ^= 0xFF
                scope["headers"][i] = (k, f"0x{bytes(corrupted).hex()}".encode())
                break
        error = await _run_middleware(scope, body)
        assert error is not None

    async def test_body_too_large(self):
        """Body exceeds max size → error."""
        scope, body = _make_signed_request(body=b"x" * 100)
        error = await _run_middleware(scope, body, max_body_size=50)
        assert error is not None
        assert "max payload size" in str(error)

    async def test_body_replayed_to_downstream(self):
        """Body is correctly replayed to the downstream app."""
        test_body = b"important data"
        scope, body = _make_signed_request(body=test_body)
        # _run_middleware already asserts body is replayed correctly
        error = await _run_middleware(scope, body)
        assert error is None
