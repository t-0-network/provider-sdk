"""Tests for ECDSA signing."""

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer, new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature

PRIVATE_KEY_HEX = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY_HEX = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"


class TestNewSigner:
    def test_sign_returns_65_byte_signature(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY_HEX)
        digest = legacy_keccak256(b"test message")
        signature, pub_key = sign_fn(digest)
        assert len(signature) == 65
        assert len(pub_key) == 65

    def test_sign_returns_correct_public_key(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY_HEX)
        digest = legacy_keccak256(b"test")
        _, pub_key = sign_fn(digest)
        expected = bytes.fromhex(PUBLIC_KEY_HEX.removeprefix("0x"))
        assert pub_key == expected

    def test_signature_v_byte_is_0_or_1(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY_HEX)
        # Sign multiple messages to increase chance of seeing both v values
        for i in range(20):
            digest = legacy_keccak256(f"message-{i}".encode())
            signature, _ = sign_fn(digest)
            v = signature[64]
            assert v in (0, 1), f"v byte should be 0 or 1, got {v}"

    def test_signature_is_verifiable(self):
        """Sign-then-verify round trip."""
        key = private_key_from_hex(PRIVATE_KEY_HEX)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"round trip test")
        signature, _ = sign_fn(digest)
        assert verify_signature(key.public_key, digest, signature)

    def test_signature_verifiable_with_64_bytes(self):
        """Verify works when v byte is stripped (64-byte signature)."""
        key = private_key_from_hex(PRIVATE_KEY_HEX)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"strip v byte")
        signature, _ = sign_fn(digest)
        assert verify_signature(key.public_key, digest, signature[:64])

    def test_different_messages_different_signatures(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY_HEX)
        digest1 = legacy_keccak256(b"message1")
        digest2 = legacy_keccak256(b"message2")
        sig1, _ = sign_fn(digest1)
        sig2, _ = sign_fn(digest2)
        assert sig1 != sig2

    def test_new_signer_from_hex(self):
        sign_fn = new_signer_from_hex(PRIVATE_KEY_HEX)
        digest = legacy_keccak256(b"from hex")
        signature, pub_key = sign_fn(digest)
        assert len(signature) == 65
        assert pub_key == bytes.fromhex(PUBLIC_KEY_HEX.removeprefix("0x"))
