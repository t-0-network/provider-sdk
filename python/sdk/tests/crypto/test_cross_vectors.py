"""Tests using shared cross-language test vectors."""

import json
import struct
from pathlib import Path

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import public_key_from_bytes
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature

VECTORS_PATH = Path(__file__).resolve().parents[4] / "cross_test" / "test_vectors.json"


def _load_vectors():
    with open(VECTORS_PATH) as f:
        return json.load(f)


VECTORS = _load_vectors()


class TestCrossVectorsKeccak256:
    def test_all_vectors(self):
        for vec in VECTORS["keccak256"]:
            result = legacy_keccak256(vec["input"].encode())
            assert result.hex() == vec["hash"], f"failed for input: {vec['input']!r}"


class TestCrossVectorsKeyDerivation:
    def test_public_key_matches(self):
        sign_fn = new_signer_from_hex(VECTORS["keys"]["private_key"])
        digest = legacy_keccak256(b"test")
        _sig, pub_key_bytes = sign_fn(digest)
        assert pub_key_bytes.hex() == VECTORS["keys"]["public_key"]


class TestCrossVectorsRequestHash:
    def test_body_plus_timestamp(self):
        rs = VECTORS["request_signing"]
        body = rs["body"].encode()
        ts_bytes = struct.pack("<Q", rs["timestamp_ms"])
        digest = legacy_keccak256(body + ts_bytes)
        assert digest.hex() == rs["expected_hash"]


class TestCrossVectorsSignVerifyRoundTrip:
    def test_round_trip(self):
        sign_fn = new_signer_from_hex(VECTORS["keys"]["private_key"])
        digest = legacy_keccak256(b"round trip test")
        sig, pub_key_bytes = sign_fn(digest)
        pub_key = public_key_from_bytes(pub_key_bytes)
        assert verify_signature(pub_key, digest, sig)


class TestCrossVectorsRequestSignature:
    def test_signature_matches_vector(self):
        rs = VECTORS["request_signing"]
        body = rs["body"].encode()
        ts_bytes = struct.pack("<Q", rs["timestamp_ms"])
        digest = legacy_keccak256(body + ts_bytes)
        assert digest.hex() == rs["expected_hash"]

        sign_fn = new_signer_from_hex(VECTORS["keys"]["private_key"])
        sig, _ = sign_fn(digest)
        # Compare first 64 bytes (r+s) against the cross-language test vector
        assert sig[:64].hex() == rs["expected_signature"]


def _request_digest(vec) -> bytes:
    """What the middleware hashes: raw body, little-endian millisecond timestamp appended."""
    body = bytes.fromhex(vec["body_hex"])
    ts_bytes = struct.pack("<Q", vec["timestamp_ms"])
    return legacy_keccak256(body + ts_bytes)


class TestCrossVectorsRequestSigningCases:
    """Bodies the string-valued request_signing block cannot express: binary, gRPC-framed,
    empty, and one whose signature has a leading zero byte — the case a signer that trims
    instead of padding to a fixed 32 bytes fails, and only that case."""

    def test_all_cases(self):
        cases = VECTORS["request_signing_cases"]
        assert cases

        sign_fn = new_signer_from_hex(VECTORS["keys"]["private_key"])
        for vec in cases:
            digest = _request_digest(vec)
            assert digest.hex() == vec["expected_hash"], f"digest for {vec['name']}"

            sig, _ = sign_fn(digest)
            assert sig[:64].hex() == vec["expected_signature"], f"signature for {vec['name']}"


class TestCrossVectorsSignatureVerification:
    """The presented-request cases, including the ones a provider has to refuse."""

    def test_all_cases(self):
        cases = VECTORS["signature_verification"]
        assert cases

        for vec in cases:
            public_key = public_key_from_bytes(bytes.fromhex(vec["public_key"]))
            signature = bytes.fromhex(vec["signature"])
            result = verify_signature(public_key, _request_digest(vec), signature)
            assert result == vec["valid"], f"{vec['name']}: {vec['note']}"
