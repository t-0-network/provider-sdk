"""Tests for key conversion utilities, using Go SDK test vectors."""

import pytest
from coincurve import PrivateKey, PublicKey

from t0_provider_sdk.crypto.keys import (
    private_key_from_hex,
    public_key_from_bytes,
    public_key_from_hex,
    public_key_to_bytes,
)

# Go SDK test vectors (from crypto/helper_test.go and crypto/sign_test.go)
PRIVATE_KEY_HEX_1 = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY_HEX_1 = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

PRIVATE_KEY_HEX_2 = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
PUBLIC_KEY_HEX_2 = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"


class TestPrivateKeyFromHex:
    def test_with_0x_prefix(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        assert isinstance(key, PrivateKey)

    def test_without_0x_prefix(self):
        key = private_key_from_hex(PRIVATE_KEY_HEX_1.removeprefix("0x"))
        assert isinstance(key, PrivateKey)

    def test_derives_correct_public_key_1(self):
        """Verify private key derives the expected public key (Go test vector 1)."""
        key = private_key_from_hex(PRIVATE_KEY_HEX_1)
        pub_bytes = key.public_key.format(compressed=False)
        expected = bytes.fromhex(PUBLIC_KEY_HEX_1.removeprefix("0x"))
        assert pub_bytes == expected

    def test_derives_correct_public_key_2(self):
        """Verify private key derives the expected public key (Go test vector 2)."""
        key = private_key_from_hex(PRIVATE_KEY_HEX_2)
        pub_bytes = key.public_key.format(compressed=False)
        expected = bytes.fromhex(PUBLIC_KEY_HEX_2.removeprefix("0x"))
        assert pub_bytes == expected

    def test_invalid_hex_raises(self):
        with pytest.raises((ValueError, Exception)):
            private_key_from_hex("0xNOTHEX")


class TestPublicKeyFromHex:
    def test_from_uncompressed_hex(self):
        key = public_key_from_hex(PUBLIC_KEY_HEX_1)
        assert isinstance(key, PublicKey)
        assert key.format(compressed=False) == bytes.fromhex(PUBLIC_KEY_HEX_1.removeprefix("0x"))

    def test_without_0x_prefix(self):
        key = public_key_from_hex(PUBLIC_KEY_HEX_1.removeprefix("0x"))
        assert key.format(compressed=False) == bytes.fromhex(PUBLIC_KEY_HEX_1.removeprefix("0x"))


class TestPublicKeyRoundTrip:
    def test_to_bytes_and_back(self):
        original = public_key_from_hex(PUBLIC_KEY_HEX_1)
        raw = public_key_to_bytes(original)
        assert len(raw) == 65  # Uncompressed: 04 || x(32) || y(32)
        assert raw[0] == 0x04
        recovered = public_key_from_bytes(raw)
        assert recovered.format(compressed=False) == original.format(compressed=False)

    def test_from_compressed_bytes(self):
        """Compressed format (33 bytes) should also work."""
        original = public_key_from_hex(PUBLIC_KEY_HEX_1)
        compressed = original.format(compressed=True)
        assert len(compressed) == 33
        recovered = public_key_from_bytes(compressed)
        assert recovered.format(compressed=False) == original.format(compressed=False)
