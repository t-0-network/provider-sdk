"""Tests for signature verification."""

from coincurve import PrivateKey

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex, public_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer
from t0_provider_sdk.crypto.verifier import verify_signature

PRIVATE_KEY_HEX_1 = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY_HEX_1 = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

PRIVATE_KEY_HEX_2 = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
PUBLIC_KEY_HEX_2 = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"


class TestVerifySignature:
    def test_valid_65_byte_signature(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"valid 65")
        signature, _ = sign_fn(digest)
        assert verify_signature(key.public_key, digest, signature)

    def test_valid_64_byte_signature(self):
        """Verify works with just r+s (no recovery byte)."""
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"valid 64")
        signature, _ = sign_fn(digest)
        assert verify_signature(key.public_key, digest, signature[:64])

    def test_wrong_public_key_fails(self):
        key1 = private_key_from_hex(PRIVATE_KEY_HEX_1)
        key2_pub = public_key_from_hex(PUBLIC_KEY_HEX_2)
        sign_fn = new_signer(key1)
        digest = legacy_keccak256(b"wrong key")
        signature, _ = sign_fn(digest)
        assert not verify_signature(key2_pub, digest, signature)

    def test_wrong_digest_fails(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"original")
        wrong_digest = legacy_keccak256(b"tampered")
        signature, _ = sign_fn(digest)
        assert not verify_signature(key.public_key, wrong_digest, signature)

    def test_corrupted_signature_fails(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        sign_fn = new_signer(key)
        digest = legacy_keccak256(b"corrupt")
        signature, _ = sign_fn(digest)
        # Flip a byte in the signature
        corrupted = bytearray(signature)
        corrupted[0] ^= 0xFF
        assert not verify_signature(key.public_key, digest, bytes(corrupted))

    def test_too_short_signature_fails(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        digest = legacy_keccak256(b"short")
        assert not verify_signature(key.public_key, digest, b"\x00" * 63)

    def test_too_long_signature_fails(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        digest = legacy_keccak256(b"long")
        assert not verify_signature(key.public_key, digest, b"\x00" * 66)

    def test_wrong_digest_length_fails(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        assert not verify_signature(key.public_key, b"\x00" * 31, b"\x00" * 64)
        assert not verify_signature(key.public_key, b"\x00" * 33, b"\x00" * 64)

    def test_cross_key_verification(self):
        """Sign with key1, verify with both keys — only key1 should pass."""
        key1 = private_key_from_hex(PRIVATE_KEY_HEX_1)
        key2 = private_key_from_hex(PRIVATE_KEY_HEX_2)
        sign_fn = new_signer(key1)
        digest = legacy_keccak256(b"cross key test")
        signature, _ = sign_fn(digest)
        assert verify_signature(key1.public_key, digest, signature)
        assert not verify_signature(key2.public_key, digest, signature)

    def test_multiple_messages_independently_verifiable(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        sign_fn = new_signer(key)
        for i in range(10):
            digest = legacy_keccak256(f"msg-{i}".encode())
            sig, _ = sign_fn(digest)
            assert verify_signature(key.public_key, digest, sig)
            assert verify_signature(key.public_key, digest, sig[:64])
