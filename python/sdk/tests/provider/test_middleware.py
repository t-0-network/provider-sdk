"""Tests for ASGI signature verification middleware.

Mirrors Go's verify_signature_test.go test cases.
"""

import struct
import time

import pytest
from t0_provider_sdk._messages import TIMESTAMP_NOT_DECIMAL
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer
from t0_provider_sdk.provider.errors import (
    InvalidHeaderEncodingError,
    InvalidTimestampError,
    MissingRequiredHeaderError,
    SignatureFailedError,
    TimestampOutOfRangeError,
    UnknownPublicKeyError,
)
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    NOT_VERIFIED,
    new_verify_signature,
    signature_error_var,
    signature_verification_middleware,
)

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"

# Not ASCII digits below 2^63: negative, 2^63, 2^64, surrounding space, a sign, an underscore,
# non-ASCII digits, and more digits than int() parses.
MALFORMED_TIMESTAMPS = ["-1", str(2**63), str(2**64), " 1", "1 ", "+1", "1_0", "\u0661\u0662", "1" * 5000]

# A gRPC body: one uncompressed frame (flag 0, uint32be length, message).
GRPC_MESSAGE = b"grpc message"
GRPC_FRAME = b"\x00" + len(GRPC_MESSAGE).to_bytes(4, "big") + GRPC_MESSAGE


def _make_signed_request(
    body: bytes = b"test body",
    private_key: str = PRIVATE_KEY,
    timestamp_ms: int | None = None,
    override_headers: dict[str, str] | None = None,
    signed_body: bytes | None = None,
) -> tuple[dict, bytes]:
    """Create a valid signed ASGI request scope and body."""
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

    scope = {
        "type": "http",
        "method": "POST",
        "path": "/test",
        "headers": [(k.encode(), v.encode()) for k, v in headers.items()],
    }
    return scope, body


async def _run_middleware(
    scope: dict,
    body: bytes,
    network_key: str = PUBLIC_KEY,
    max_body_size: int = DEFAULT_MAX_BODY_SIZE,
    verify_fn=None,
):
    """Run the middleware and return the signature error (if any)."""
    if verify_fn is None:
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

    @pytest.mark.parametrize("timestamp", MALFORMED_TIMESTAMPS)
    async def test_malformed_timestamp(self, timestamp):
        """Anything but ASCII digits below 2^63 -> an invalid timestamp, never an exception."""
        scope, body = _make_signed_request(override_headers={"x-signature-timestamp": timestamp})
        error = await _run_middleware(scope, body)
        assert isinstance(error, InvalidTimestampError)

    async def test_largest_timestamp_is_out_of_range(self):
        scope, body = _make_signed_request(override_headers={"x-signature-timestamp": str(2**63 - 1)})
        error = await _run_middleware(scope, body)
        assert isinstance(error, TimestampOutOfRangeError)

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
        assert str(error) == "request signed with unknown public key"

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

    @pytest.mark.parametrize("content_type", ["application/grpc", "application/grpc+proto"])
    @pytest.mark.parametrize("signed_body", [GRPC_FRAME, GRPC_MESSAGE], ids=["framed body", "message"])
    async def test_grpc_request_signed_over_framed_body_or_message(self, content_type, signed_body):
        """A gRPC body signed below the framer (the network) or above it (the Java SDK) passes."""
        scope, body = _make_signed_request(
            body=GRPC_FRAME, signed_body=signed_body, override_headers={"content-type": content_type}
        )
        assert await _run_middleware(scope, body) is None

    @pytest.mark.parametrize(
        ("content_type", "body"),
        [
            ("application/proto", GRPC_FRAME),
            ("application/grpc", b"\x01" + GRPC_FRAME[1:]),
            ("application/grpc", GRPC_FRAME + GRPC_FRAME),
        ],
        ids=["not grpc", "compressed flag", "two frames"],
    )
    async def test_signature_without_prefix_needs_one_uncompressed_grpc_frame(self, content_type, body):
        """Signed over everything after the first 5 bytes: refused unless the body is one gRPC frame."""
        scope, body = _make_signed_request(
            body=body, signed_body=body[5:], override_headers={"content-type": content_type}
        )
        assert isinstance(await _run_middleware(scope, body), SignatureFailedError)

    async def test_body_replayed_to_downstream(self):
        """Body is correctly replayed to the downstream app."""
        test_body = b"important data"
        scope, body = _make_signed_request(body=test_body)
        # _run_middleware already asserts body is replayed correctly
        error = await _run_middleware(scope, body)
        assert error is None

    async def test_result_does_not_outlive_the_request(self):
        """Once the request is done its context holds no result, so another request served in it
        is not taken as verified."""
        scope, body = _make_signed_request()
        assert await _run_middleware(scope, body) is None
        assert signature_error_var.get() is NOT_VERIFIED


class _RecordingVerifyFn:
    """A verify_fn of the caller's own: records its calls and accepts what accept says."""

    def __init__(self, accept=lambda public_key, message, signature: True):
        self.calls = []
        self._accept = accept

    def __call__(self, public_key: bytes, message: bytes, signature: bytes) -> None:
        self.calls.append((public_key, message, signature))
        if not self._accept(public_key, message, signature):
            raise SignatureFailedError()


def _header(scope: dict, name: bytes) -> str:
    return next(v for k, v in scope["headers"] if k == name).decode()


@pytest.mark.asyncio
class TestCustomVerifyFn:
    """A plain callable as verify_fn, as 1.2.1 took it: called after the header checks with the
    X-Public-Key bytes, the body and the timestamp bytes, and the X-Signature bytes."""

    async def test_called_with_the_raw_header_bytes_and_the_signed_message(self):
        scope, body = _make_signed_request()
        verify_fn = _RecordingVerifyFn()
        assert await _run_middleware(scope, body, verify_fn=verify_fn) is None
        timestamp_bytes = struct.pack("<Q", int(_header(scope, b"x-signature-timestamp")))
        assert verify_fn.calls == [
            (
                bytes.fromhex(_header(scope, b"x-public-key")[2:]),
                body + timestamp_bytes,
                bytes.fromhex(_header(scope, b"x-signature")[2:]),
            )
        ]

    async def test_its_refusal_is_the_result(self):
        scope, body = _make_signed_request()
        verify_fn = _RecordingVerifyFn(accept=lambda *_: False)
        assert isinstance(await _run_middleware(scope, body, verify_fn=verify_fn), SignatureFailedError)

    @pytest.mark.parametrize("content_type", ["application/grpc", "application/grpc+proto"])
    async def test_retried_over_the_message_of_one_grpc_frame(self, content_type):
        scope, body = _make_signed_request(body=GRPC_FRAME, override_headers={"content-type": content_type})
        verify_fn = _RecordingVerifyFn(accept=lambda _key, message, _sig: message.startswith(GRPC_MESSAGE))
        assert await _run_middleware(scope, body, verify_fn=verify_fn) is None
        assert [message[: len(GRPC_MESSAGE)] for _, message, _ in verify_fn.calls] == [GRPC_FRAME[:12], GRPC_MESSAGE]

    async def test_header_checks_come_first(self):
        scope, body = _make_signed_request()
        scope["headers"] = [(k, v) for k, v in scope["headers"] if k != b"x-signature"]
        verify_fn = _RecordingVerifyFn()
        assert isinstance(await _run_middleware(scope, body, verify_fn=verify_fn), MissingRequiredHeaderError)
        assert verify_fn.calls == []

    async def test_public_key_that_is_not_hex_is_unknown(self):
        scope, body = _make_signed_request(override_headers={"x-public-key": "0xnothex"})
        verify_fn = _RecordingVerifyFn()
        assert isinstance(await _run_middleware(scope, body, verify_fn=verify_fn), UnknownPublicKeyError)
        assert verify_fn.calls == []


def test_invalid_timestamp_is_an_invalid_header_encoding():
    """A malformed timestamp is still caught as InvalidHeaderEncodingError, as in 1.2.1, with its own message."""
    error = InvalidTimestampError(TIMESTAMP_NOT_DECIMAL)
    assert isinstance(error, InvalidHeaderEncodingError)
    assert error.header_name == "X-Signature-Timestamp"
    assert str(error) == "invalid timestamp header: not a decimal number"
