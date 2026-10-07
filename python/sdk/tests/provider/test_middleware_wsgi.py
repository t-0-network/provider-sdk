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
from t0_provider_sdk.provider.errors import (
    InvalidTimestampError,
    SignatureFailedError,
    TimestampOutOfRangeError,
    UnknownPublicKeyError,
)
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    NOT_VERIFIED,
    new_verify_signature,
    signature_error_var,
)
from t0_provider_sdk.provider.middleware_wsgi import signature_verification_middleware_wsgi

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"

# Not ASCII digits below 2^63: negative, 2^63, 2^64, surrounding space, a sign, an underscore,
# non-ASCII digits, and more digits than int() parses.
MALFORMED_TIMESTAMPS = ["-1", str(2**63), str(2**64), " 1", "1 ", "+1", "1_0", "\u0661\u0662", "1" * 5000]

# A gRPC body: one uncompressed frame (flag 0, uint32be length, message).
GRPC_MESSAGE = b"grpc message"
GRPC_FRAME = b"\x00" + len(GRPC_MESSAGE).to_bytes(4, "big") + GRPC_MESSAGE

UNKNOWN_PUBLIC_KEY = "request signed with unknown public key"
SIGNATURE_VERIFICATION_FAILED = "signature verification failed"

# Rule V10: X-Public-Key is checked against the network key before the signature length, by the
# server and by the verifier new_verify_signature builds alike. (X-Public-Key, X-Signature, the rejection.)
KEY_BEFORE_LENGTH_CASES = [
    pytest.param(
        OTHER_PUBLIC_KEY, "0x11", UnknownPublicKeyError, UNKNOWN_PUBLIC_KEY, id="unknown key, 1-byte signature"
    ),
    pytest.param(
        OTHER_PUBLIC_KEY,
        "0x" + "11" * 66,
        UnknownPublicKeyError,
        UNKNOWN_PUBLIC_KEY,
        id="unknown key, 66-byte signature",
    ),
    pytest.param(
        "0x05" + "11" * 64, "0x11", UnknownPublicKeyError, UNKNOWN_PUBLIC_KEY, id="not a key, 1-byte signature"
    ),
    pytest.param(
        PUBLIC_KEY, "0x11", SignatureFailedError, SIGNATURE_VERIFICATION_FAILED, id="network key, 1-byte signature"
    ),
    pytest.param(
        PUBLIC_KEY,
        "0x" + "11" * 66,
        SignatureFailedError,
        SIGNATURE_VERIFICATION_FAILED,
        id="network key, 66-byte signature",
    ),
]


def _make_signed_environ(
    body: bytes = b"test body",
    private_key: str = PRIVATE_KEY,
    timestamp_ms: int | None = None,
    override_headers: dict[str, str] | None = None,
    signed_body: bytes | None = None,
) -> dict:
    """Create a valid signed WSGI environ dict."""
    key = private_key_from_hex(private_key)
    sign_fn = new_signer(key)

    if timestamp_ms is None:
        timestamp_ms = int(time.time() * 1000)

    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    # The signature covers signed_body when given, the body otherwise.
    digest = legacy_keccak256((body if signed_body is None else signed_body) + timestamp_bytes)
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


def _run_middleware(
    environ: dict, network_key: str = PUBLIC_KEY, max_body_size: int = DEFAULT_MAX_BODY_SIZE, verify_fn=None
):
    """Run the WSGI middleware and return (signature_error, downstream_body)."""
    if verify_fn is None:
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

    def test_request_without_length_has_an_empty_body(self):
        """No CONTENT_LENGTH and an unterminated input: nothing is read, the body is empty."""

        class _OpenConnection:
            def read(self, *args):
                raise AssertionError("reading would wait for the connection to close")

        environ = _make_signed_environ(body=b"")
        del environ["CONTENT_LENGTH"]
        environ["wsgi.input"] = _OpenConnection()
        error, body = _run_middleware(environ)
        assert error is None
        assert body == b""

    def test_terminated_input_without_length_is_read_to_its_end(self):
        environ = _make_signed_environ(body=b"chunked body")
        del environ["CONTENT_LENGTH"]
        environ["wsgi.input_terminated"] = True
        error, body = _run_middleware(environ)
        assert error is None
        assert body == b"chunked body"

    def test_terminated_input_is_read_only_past_the_limit(self):
        body = b"x" * 100
        environ = _make_signed_environ(body=body)
        del environ["CONTENT_LENGTH"]
        environ["wsgi.input_terminated"] = True
        stream = environ["wsgi.input"]
        error, _ = _run_middleware(environ, max_body_size=10)
        assert "max payload size" in str(error)
        assert stream.tell() == 11, "reads one byte past the limit, not the whole body"

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

    @pytest.mark.parametrize("timestamp", MALFORMED_TIMESTAMPS)
    def test_malformed_timestamp(self, timestamp):
        """Anything but ASCII digits below 2^63 -> an invalid timestamp, never an exception."""
        environ = _make_signed_environ(override_headers={"x-signature-timestamp": timestamp})
        error, _ = _run_middleware(environ)
        assert isinstance(error, InvalidTimestampError)

    def test_largest_timestamp_is_out_of_range(self):
        environ = _make_signed_environ(override_headers={"x-signature-timestamp": str(2**63 - 1)})
        error, _ = _run_middleware(environ)
        assert isinstance(error, TimestampOutOfRangeError)

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
        assert str(error) == "request signed with unknown public key"

    @pytest.mark.parametrize(("public_key", "signature", "error_type", "message"), KEY_BEFORE_LENGTH_CASES)
    def test_key_is_checked_before_signature_length(self, public_key, signature, error_type, message):
        environ = _make_signed_environ(override_headers={"x-public-key": public_key, "x-signature": signature})
        error, _ = _run_middleware(environ)
        assert type(error) is error_type
        assert str(error) == message

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

    @pytest.mark.parametrize("content_type", ["application/grpc", "application/grpc+proto"])
    @pytest.mark.parametrize("signed_body", [GRPC_FRAME, GRPC_MESSAGE], ids=["framed body", "message"])
    def test_grpc_request_signed_over_framed_body_or_message(self, content_type, signed_body):
        """A gRPC body signed below the framer (the network) or above it (the Java SDK) passes."""
        environ = _make_signed_environ(body=GRPC_FRAME, signed_body=signed_body)
        environ["CONTENT_TYPE"] = content_type
        error, body = _run_middleware(environ)
        assert error is None
        assert body == GRPC_FRAME

    @pytest.mark.parametrize(
        ("content_type", "body"),
        [
            ("application/proto", GRPC_FRAME),
            ("application/grpc", b"\x01" + GRPC_FRAME[1:]),
            ("application/grpc", GRPC_FRAME + GRPC_FRAME),
        ],
        ids=["not grpc", "compressed flag", "two frames"],
    )
    def test_signature_without_prefix_needs_one_uncompressed_grpc_frame(self, content_type, body):
        """Signed over everything after the first 5 bytes: refused unless the body is one gRPC frame."""
        environ = _make_signed_environ(body=body, signed_body=body[5:])
        environ["CONTENT_TYPE"] = content_type
        error, _ = _run_middleware(environ)
        assert isinstance(error, SignatureFailedError)

    def test_body_replayed_to_downstream(self):
        """Body is correctly replayed to the downstream app."""
        test_body = b"important data"
        environ = _make_signed_environ(body=test_body)
        error, downstream_body = _run_middleware(environ)
        assert error is None
        assert downstream_body == test_body

    def test_result_does_not_outlive_the_request(self):
        """Once the app has returned the thread's context holds no result, so the next request the
        thread serves is not taken as verified."""
        environ = _make_signed_environ()
        error, _ = _run_middleware(environ)
        assert error is None
        assert signature_error_var.get() is NOT_VERIFIED


class TestCustomVerifyFnWSGI:
    """A plain callable as verify_fn, as 1.2.1 took it (see test_middleware.TestCustomVerifyFn)."""

    def test_called_with_the_raw_header_bytes_and_the_signed_message(self):
        environ = _make_signed_environ(body=b"payload")
        calls = []

        def verify_fn(public_key: bytes, message: bytes, signature: bytes) -> None:
            calls.append((public_key, message, signature))

        error, body = _run_middleware(environ, verify_fn=verify_fn)
        assert error is None
        assert body == b"payload"
        timestamp_bytes = struct.pack("<Q", int(environ["HTTP_X_SIGNATURE_TIMESTAMP"]))
        assert calls == [
            (
                bytes.fromhex(environ["HTTP_X_PUBLIC_KEY"][2:]),
                b"payload" + timestamp_bytes,
                bytes.fromhex(environ["HTTP_X_SIGNATURE"][2:]),
            )
        ]

    def test_its_refusal_is_the_result(self):
        def verify_fn(public_key: bytes, message: bytes, signature: bytes) -> None:
            raise SignatureFailedError()

        error, _ = _run_middleware(_make_signed_environ(), verify_fn=verify_fn)
        assert isinstance(error, SignatureFailedError)

    @pytest.mark.parametrize(("public_key", "signature", "error_type", "message"), KEY_BEFORE_LENGTH_CASES)
    def test_network_verifier_as_a_plain_callable_checks_the_key_first(
        self, public_key, signature, error_type, message
    ):
        """The verifier new_verify_signature builds, wrapped in a plain callable, refuses as the server does."""
        network_verify_fn = new_verify_signature(PUBLIC_KEY)
        environ = _make_signed_environ(override_headers={"x-public-key": public_key, "x-signature": signature})
        error, _ = _run_middleware(environ, verify_fn=lambda key, message, sig: network_verify_fn(key, message, sig))
        assert type(error) is error_type
        assert str(error) == message
