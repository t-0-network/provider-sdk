"""Tests for WSGI signature verification middleware.

Mirrors test_middleware.py test cases but for the WSGI transport layer.
"""

import io
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
)
from t0_provider_sdk.provider.middleware_wsgi import signature_verification_middleware_wsgi

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"


def _make_signed_environ(
    body: bytes = b"test body",
    private_key: str = PRIVATE_KEY,
    timestamp_ms: int | None = None,
    override_headers: dict[str, str] | None = None,
) -> dict:
    """Create a valid signed WSGI environ dict."""
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

    environ = {
        "REQUEST_METHOD": "POST",
        "PATH_INFO": "/test",
        "wsgi.input": io.BytesIO(body),
        "CONTENT_LENGTH": str(len(body)),
    }

    # Convert headers to WSGI environ format: x-public-key -> HTTP_X_PUBLIC_KEY
    for name, value in headers.items():
        wsgi_key = "HTTP_" + name.upper().replace("-", "_")
        environ[wsgi_key] = value

    return environ


def _run_middleware(environ: dict, network_key: str = PUBLIC_KEY, max_body_size: int = DEFAULT_MAX_BODY_SIZE):
    """Run the WSGI middleware and return (signature_error, downstream_body)."""
    verify_fn = new_verify_signature(network_key)

    captured_error = None
    captured_body = None

    def downstream_app(environ, start_response):
        nonlocal captured_error, captured_body
        captured_error = signature_error_var.get()
        captured_body = environ["wsgi.input"].read()
        start_response("200 OK", [])
        return [b"ok"]

    app = signature_verification_middleware_wsgi(downstream_app, verify_fn, max_body_size)

    def start_response(status, headers):
        pass

    app(environ, start_response)
    return captured_error, captured_body


class TestSignatureVerificationMiddlewareWSGI:
    def test_valid_request(self):
        """Valid request with all headers -> success."""
        environ = _make_signed_environ()
        error, _ = _run_middleware(environ)
        assert error is None

    def test_valid_request_empty_body(self):
        """Empty body -> success."""
        environ = _make_signed_environ(body=b"")
        error, _ = _run_middleware(environ)
        assert error is None

    def test_missing_public_key(self):
        """Missing X-Public-Key -> error."""
        environ = _make_signed_environ()
        del environ["HTTP_X_PUBLIC_KEY"]
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "missing required header" in str(error)

    def test_missing_signature(self):
        """Missing X-Signature -> error."""
        environ = _make_signed_environ()
        del environ["HTTP_X_SIGNATURE"]
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "missing required header" in str(error)

    def test_missing_timestamp(self):
        """Missing X-Signature-Timestamp -> error."""
        environ = _make_signed_environ()
        del environ["HTTP_X_SIGNATURE_TIMESTAMP"]
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "missing required header" in str(error)

    def test_invalid_hex_encoding(self):
        """Invalid hex encoding -> error."""
        environ = _make_signed_environ(override_headers={"x-signature": "0xNOTHEX"})
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "invalid header encoding" in str(error)

    def test_header_too_short(self):
        """Header value too short -> error."""
        environ = _make_signed_environ(override_headers={"x-signature": "0"})
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "invalid header encoding" in str(error)

    def test_timestamp_too_old(self):
        """Timestamp >60s in the past -> error."""
        old_ts = int(time.time() * 1000) - 120_000  # 2 minutes ago
        environ = _make_signed_environ(timestamp_ms=old_ts)
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "time window" in str(error)

    def test_timestamp_too_new(self):
        """Timestamp >60s in the future -> error."""
        future_ts = int(time.time() * 1000) + 120_000  # 2 minutes ahead
        environ = _make_signed_environ(timestamp_ms=future_ts)
        error, _ = _run_middleware(environ)
        assert error is not None
        assert "time window" in str(error)

    def test_wrong_public_key(self):
        """Wrong public key -> error."""
        environ = _make_signed_environ()
        error, _ = _run_middleware(environ, network_key=OTHER_PUBLIC_KEY)
        assert error is not None
        assert "unknown public key" in str(error)

    def test_invalid_signature(self):
        """Tampered signature -> error."""
        environ = _make_signed_environ()
        # Corrupt the signature
        sig_hex = environ["HTTP_X_SIGNATURE"]
        corrupted = bytearray(bytes.fromhex(sig_hex[2:]))
        corrupted[0] ^= 0xFF
        environ["HTTP_X_SIGNATURE"] = f"0x{bytes(corrupted).hex()}"
        error, _ = _run_middleware(environ)
        assert error is not None

    def test_body_too_large(self):
        """Body exceeds max size -> error."""
        environ = _make_signed_environ(body=b"x" * 100)
        error, _ = _run_middleware(environ, max_body_size=50)
        assert error is not None
        assert "max payload size" in str(error)

    def test_body_replayed_to_downstream(self):
        """Body is correctly replayed to the downstream app."""
        test_body = b"important data"
        environ = _make_signed_environ(body=test_body)
        error, downstream_body = _run_middleware(environ)
        assert error is None
        assert downstream_body == test_body
