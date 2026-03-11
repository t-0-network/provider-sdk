"""Tests for signing transport."""

import struct

import pyqwest

from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex, public_key_from_bytes
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature
from t0_provider_sdk.network.signing import _sign_request

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY_HEX = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"


class TestSignRequest:
    def test_adds_all_three_headers(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        headers = _sign_request(sign_fn, b"test body", None)

        assert PUBLIC_KEY_HEADER in headers
        assert SIGNATURE_HEADER in headers
        assert SIGNATURE_TIMESTAMP_HEADER in headers

    def test_public_key_header_format(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        headers = _sign_request(sign_fn, b"test", None)
        pub_key_hex = headers[PUBLIC_KEY_HEADER]
        assert pub_key_hex.startswith("0x")
        expected = PUBLIC_KEY_HEX
        assert pub_key_hex == expected

    def test_timestamp_is_milliseconds(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        headers = _sign_request(sign_fn, b"test", None)
        ts = int(headers[SIGNATURE_TIMESTAMP_HEADER])
        # Should be a recent timestamp in milliseconds (13+ digits)
        assert ts > 1_000_000_000_000

    def test_signature_is_verifiable(self):
        """The produced signature can be verified with the crypto module."""
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        body = b"verifiable body"
        headers = _sign_request(sign_fn, body, None)

        # Extract values
        pub_key_bytes = bytes.fromhex(headers[PUBLIC_KEY_HEADER][2:])
        signature = bytes.fromhex(headers[SIGNATURE_HEADER][2:])
        timestamp_ms = int(headers[SIGNATURE_TIMESTAMP_HEADER])
        timestamp_bytes = struct.pack("<Q", timestamp_ms)

        # Reconstruct digest
        message = body + timestamp_bytes
        digest = legacy_keccak256(message)

        # Verify
        pub_key = public_key_from_bytes(pub_key_bytes)
        assert verify_signature(pub_key, digest, signature)

    def test_preserves_existing_headers(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        existing = pyqwest.Headers({"Content-Type": "application/proto"})
        headers = _sign_request(sign_fn, b"test", existing)
        assert headers["Content-Type"] == "application/proto"
        assert PUBLIC_KEY_HEADER in headers

    def test_empty_body_signs_correctly(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        headers = _sign_request(sign_fn, b"", None)

        signature = bytes.fromhex(headers[SIGNATURE_HEADER][2:])
        pub_key_bytes = bytes.fromhex(headers[PUBLIC_KEY_HEADER][2:])
        timestamp_ms = int(headers[SIGNATURE_TIMESTAMP_HEADER])
        timestamp_bytes = struct.pack("<Q", timestamp_ms)

        digest = legacy_keccak256(b"" + timestamp_bytes)
        pub_key = public_key_from_bytes(pub_key_bytes)
        assert verify_signature(pub_key, digest, signature)
