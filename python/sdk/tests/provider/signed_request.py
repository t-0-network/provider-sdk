"""Keys, cases and request signing shared by the ASGI and WSGI middleware tests."""

import struct
import time

import pytest
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer
from t0_provider_sdk.provider.errors import SignatureFailedError, UnknownPublicKeyError

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


def signed_headers(
    body: bytes,
    private_key: str = PRIVATE_KEY,
    timestamp_ms: int | None = None,
    override_headers: dict[str, str] | None = None,
    signed_body: bytes | None = None,
) -> dict[str, str]:
    """The signature headers of a request with body, signed by private_key at timestamp_ms (now
    when None), with override_headers applied on top."""
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
    return headers
