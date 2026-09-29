"""Tests using shared cross-language test vectors."""

import json
import struct
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path

import pyqwest
import pytest
from t0_provider_sdk.common.headers import SIGNATURE_HEADER, SIGNATURE_TIMESTAMP_HEADER
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import public_key_from_bytes
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature
from t0_provider_sdk.network import signing
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient

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


def _first_envelope(body: bytes) -> bytes:
    if not body:
        return b""
    assert len(body) >= 5
    return body[: 5 + int.from_bytes(body[1:5], "big")]


def _envelopes(body: bytes) -> list[bytes]:
    envelopes = []
    while body:
        envelopes.append(_first_envelope(body))
        body = body[len(envelopes[-1]) :]
    return envelopes


class _RecordingStreamClient:
    """Stands in for pyqwest.Client: records the headers and the body of a stream request."""

    def __init__(self):
        self.headers = None
        self.body = None

    @asynccontextmanager
    async def stream(self, method, url, headers=None, content=None):
        self.headers = headers
        self.body = b"".join([chunk async for chunk in content])
        yield None


class _RecordingStreamSyncClient:
    """Stands in for pyqwest.SyncClient."""

    def __init__(self):
        self.headers = None
        self.body = None

    @contextmanager
    def stream(self, method, url, headers=None, content=None, timeout=None):
        self.headers = headers
        self.body = b"".join(content)
        yield None


def _first_envelope_cases():
    # This SDK signs below the gRPC framer, so it only ever produces first_envelope signatures.
    return [vec for vec in VECTORS["stream_signing_cases"] if vec["covers"] == "first_envelope"]


def _assert_signed_like_vector(vec, recorder):
    assert recorder.headers[SIGNATURE_HEADER][2:130] == vec["expected_signature"], vec["name"]
    assert recorder.headers[SIGNATURE_TIMESTAMP_HEADER] == str(vec["timestamp_ms"]), vec["name"]
    assert recorder.body == bytes.fromhex(vec["body_hex"]), vec["name"]


class TestCrossVectorsStreamSigningCases:
    """first_envelope covers the first envelope as sent; first_payload the same message without its
    5-byte prefix, as a signer above the gRPC framer (Java) signs it."""

    def test_all_cases(self):
        cases = VECTORS["stream_signing_cases"]
        assert cases

        sign_fn = new_signer_from_hex(VECTORS["keys"]["private_key"])
        for vec in cases:
            envelope = _first_envelope(bytes.fromhex(vec["body_hex"]))
            match vec["covers"]:
                case "first_envelope":
                    signed = envelope
                case "first_payload":
                    signed = envelope[5:]
                case other:
                    raise AssertionError(f"{vec['name']}: unknown covers {other!r}")
            assert signed.hex() == vec["signed_hex"], f"signed bytes for {vec['name']}"

            digest = legacy_keccak256(signed + struct.pack("<Q", vec["timestamp_ms"]))
            assert digest.hex() == vec["expected_hash"], f"digest for {vec['name']}"

            sig, _ = sign_fn(digest)
            assert sig[:64].hex() == vec["expected_signature"], f"signature for {vec['name']}"

    def test_sync_transport_signs_like_vectors(self, monkeypatch):
        cases = _first_envelope_cases()
        assert cases

        for vec in cases:
            monkeypatch.setattr(signing, "_timestamp_ms", lambda vec=vec: vec["timestamp_ms"])
            client = SigningSyncClient(new_signer_from_hex(VECTORS["keys"]["private_key"]))
            client._inner = recorder = _RecordingStreamSyncClient()

            headers = pyqwest.Headers({"Content-Type": vec["content_type"]})
            content = iter(_envelopes(bytes.fromhex(vec["body_hex"])))
            with client.stream("POST", "http://example.test/", headers=headers, content=content):
                pass

            _assert_signed_like_vector(vec, recorder)

    @pytest.mark.asyncio
    async def test_async_transport_signs_like_vectors(self, monkeypatch):
        cases = _first_envelope_cases()
        assert cases

        async def chunks(envelopes):
            for envelope in envelopes:
                yield envelope

        for vec in cases:
            monkeypatch.setattr(signing, "_timestamp_ms", lambda vec=vec: vec["timestamp_ms"])
            client = SigningClient(new_signer_from_hex(VECTORS["keys"]["private_key"]))
            client._inner = recorder = _RecordingStreamClient()

            headers = pyqwest.Headers({"Content-Type": vec["content_type"]})
            content = chunks(_envelopes(bytes.fromhex(vec["body_hex"])))
            async with client.stream("POST", "http://example.test/", headers=headers, content=content):
                pass

            _assert_signed_like_vector(vec, recorder)
