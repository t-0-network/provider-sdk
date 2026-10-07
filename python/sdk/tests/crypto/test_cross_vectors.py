"""Tests using shared cross-language test vectors."""

import json
import re
import struct
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path

import pyqwest
import pytest
from t0_provider_sdk.common.headers import SIGNATURE_HEADER, SIGNATURE_TIMESTAMP_HEADER
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import (
    _decode_hex_strict,
    _parse_public_key,
    _public_key_from_bytes_strict,
    private_key_from_hex,
    public_key_from_bytes,
    public_key_from_hex,
    public_key_from_private_key,
)
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature
from t0_provider_sdk.network import signing
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient
from t0_provider_sdk.provider import NetworkPublicKeyRequiredError, new_asgi_app, new_wsgi_app
from t0_provider_sdk.provider.errors import SignatureVerificationError
from t0_provider_sdk.provider.middleware import _parse_timestamp, new_verify_signature

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
        pub_key = _public_key_from_bytes_strict(pub_key_bytes)
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


def _hex_decodable(vec) -> bool:
    try:
        _decode_hex_strict(vec["input"])
    except ValueError:
        return False
    return True


def _exactly(message: str) -> str:
    """A pytest.raises match for this message and nothing else."""
    return f"^{re.escape(message)}$"


class TestCrossVectorsPublicKeyParsing:
    """The parser the server uses for the configured network key and the X-Public-Key header, and the
    deprecated public helpers, which follow the same rule. A refused key fails with the row's error."""

    @pytest.mark.parametrize("vec", VECTORS["public_key_parsing"], ids=lambda vec: vec["name"])
    def test_case(self, vec):
        if vec["valid"]:
            assert _parse_public_key(vec["input"]).format(compressed=False).hex() == vec["uncompressed"]
        else:
            with pytest.raises(ValueError, match=_exactly(vec["error"])):
                _parse_public_key(vec["input"])

    @pytest.mark.parametrize("vec", VECTORS["public_key_parsing"], ids=lambda vec: vec["name"])
    def test_public_key_from_hex(self, vec):
        with pytest.deprecated_call():
            if vec["valid"]:
                assert public_key_from_hex(vec["input"]).format(compressed=False).hex() == vec["uncompressed"]
            else:
                with pytest.raises(ValueError, match=_exactly(vec["error"])):
                    public_key_from_hex(vec["input"])

    @pytest.mark.parametrize(
        "vec", [vec for vec in VECTORS["public_key_parsing"] if _hex_decodable(vec)], ids=lambda vec: vec["name"]
    )
    def test_public_key_from_bytes(self, vec):
        """The rows that are hex at all, decoded. The others fail before there are bytes, so their
        error is not one this helper can raise."""
        data = _decode_hex_strict(vec["input"])
        with pytest.deprecated_call():
            if vec["valid"]:
                assert public_key_from_bytes(data).format(compressed=False).hex() == vec["uncompressed"]
            else:
                with pytest.raises(ValueError, match=_exactly(vec["error"])):
                    public_key_from_bytes(data)


class TestCrossVectorsNetworkPublicKey:
    """Rule V1: the configured network key, checked when the server is set up. Surrounding whitespace
    is trimmed; then a blank key fails with "network public key is not set" and any other refused key
    with "invalid network public key: " and the row's error."""

    @pytest.mark.parametrize(
        "build",
        [
            pytest.param(new_asgi_app, id="new_asgi_app"),
            pytest.param(new_wsgi_app, id="new_wsgi_app"),
            pytest.param(new_verify_signature, id="new_verify_signature"),
        ],
    )
    @pytest.mark.parametrize("pad", [pytest.param("", id="as is"), pytest.param(" ", id="padded")])
    @pytest.mark.parametrize("vec", VECTORS["public_key_parsing"], ids=lambda vec: vec["name"])
    def test_case(self, vec, pad, build):
        key = f"{pad}{vec['input']}{pad}"
        if vec["valid"]:
            assert callable(build(key))
        elif not vec["input"].strip():
            with pytest.raises(NetworkPublicKeyRequiredError, match=_exactly("network public key is not set")):
                build(key)
        else:
            with pytest.raises(ValueError, match=_exactly(f"invalid network public key: {vec['error']}")) as exc_info:
                build(key)
            assert not isinstance(exc_info.value, NetworkPublicKeyRequiredError)


class TestCrossVectorsPrivateKeyParsing:
    """Rule S5: the parser of the private key a client signs with."""

    def test_row_count(self):
        assert len(VECTORS["private_key_parsing"]) == 17

    @pytest.mark.parametrize("vec", VECTORS["private_key_parsing"], ids=lambda vec: vec["name"])
    def test_case(self, vec):
        if vec["valid"]:
            key = private_key_from_hex(vec["input"])
            assert key.public_key.format(compressed=False).hex() == vec["public_key"]
        else:
            with pytest.raises(ValueError, match=f"^{re.escape(vec['error'])}$"):
                private_key_from_hex(vec["input"])


class TestCrossVectorsTimestampParsing:
    """The parser of the X-Signature-Timestamp header."""

    @pytest.mark.parametrize("vec", VECTORS["timestamp_parsing"], ids=lambda vec: vec["name"])
    def test_case(self, vec):
        headers = {SIGNATURE_TIMESTAMP_HEADER.lower(): vec["input"]}
        if vec["valid"]:
            assert _parse_timestamp(headers) == (int(vec["value"]), struct.pack("<Q", int(vec["value"])))
        else:
            with pytest.raises(SignatureVerificationError, match=f"^{re.escape(vec['error'])}$"):
                _parse_timestamp(headers)


class TestCrossVectorsSignatureVerification:
    """The presented-request cases, including the ones a provider has to refuse."""

    def test_all_cases(self):
        cases = VECTORS["signature_verification"]
        assert cases

        for vec in cases:
            # verify_signature takes a parsed key, so a key that rule V2 refuses (hybrid-key) fails
            # here, as it fails the helper of every other SDK.
            try:
                public_key = _public_key_from_bytes_strict(bytes.fromhex(vec["public_key"]))
            except ValueError:
                assert not vec["valid"], f"{vec['name']}: the key of a case that verifies parses"
                continue
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
    """Both stream wrappers sign the vectors' first envelopes to the expected signatures."""

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


class TestCrossVectorsPublicKeyFromPrivateKey:
    """public_key_from_private_key over every private_key_parsing row: "0x" and the lowercase
    uncompressed key, or the private-key parser's error."""

    @pytest.mark.parametrize("vec", VECTORS["private_key_parsing"], ids=lambda vec: vec["name"])
    def test_case(self, vec):
        if vec["valid"]:
            assert public_key_from_private_key(vec["input"]) == "0x" + vec["public_key"].lower()
            assert public_key_from_private_key(private_key_hex=vec["input"]) == "0x" + vec["public_key"].lower()
        else:
            with pytest.raises(ValueError, match=f"^{re.escape(vec['error'])}$"):
                public_key_from_private_key(vec["input"])


class TestCrossVectorsSignerCases:
    """new_signer_from_hex's signer over fixed digests: 65 bytes r || s || v and the 65-byte
    uncompressed key, or the error for a digest that is not 32 bytes (signer_cases)."""

    @pytest.mark.parametrize("vec", VECTORS["signer_cases"], ids=lambda vec: vec["name"])
    def test_case(self, vec):
        sign = new_signer_from_hex(vec["private_key"])
        digest = bytes.fromhex(vec["digest"])
        if "error" in vec:
            with pytest.raises(ValueError, match=f"^{re.escape(vec['error'])}$"):
                sign(digest)
        else:
            signature, public_key = sign(digest)
            assert signature.hex() == vec["signature"]
            assert public_key.hex() == vec["public_key"]
