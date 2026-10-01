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

    def test_with_upper_case_0x_prefix(self):
        key = private_key_from_hex("0X" + PRIVATE_KEY_HEX_1.removeprefix("0x"))
        assert key.secret == private_key_from_hex(PRIVATE_KEY_HEX_1).secret

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

    @pytest.mark.parametrize("key", ["", None])
    def test_empty_key_is_refused(self, key):
        with pytest.raises(ValueError, match="^private key must not be null or empty$"):
            private_key_from_hex(key)

    @pytest.mark.parametrize(
        "key",
        [
            "0x",
            "01" * 31,
            "0x" + "01" * 31,
            "01" * 33,
            "01" * 31 + "  ",
            " " + "01" * 31 + " ",
            "zz" + "01" * 31,
            "+1" + "01" * 31,
            "\u06661" + "01" * 31,
        ],
        ids=[
            "prefix only",
            "62 hex",
            "0x + 62 hex",
            "66 hex",
            "62 hex + 2 spaces",
            "spaces around",
            "zz + 62 hex",
            "a sign",
            "a digit that is not ASCII",
        ],
    )
    def test_key_that_is_not_64_hex_digits_is_refused(self, key):
        """A 31-byte key, padded with whitespace or not, would otherwise become a different key."""
        with pytest.raises(ValueError, match=r"^private key must be 32 bytes \(64 hex characters\)$"):
            private_key_from_hex(key)

    @pytest.mark.parametrize(
        "key",
        ["0" * 64, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", "F" * 64],
        ids=["zero", "the order n", "above n"],
    )
    def test_key_outside_the_curve_order_is_refused(self, key):
        """Never reduced mod n: such a key is refused, not turned into another one."""
        with pytest.raises(ValueError, match=r"^private key must be in range \[1, n-1\]$"):
            private_key_from_hex(key)


class TestPublicKeyFromHex:
    """Deprecated; the public_key_parsing vectors run through it in test_cross_vectors."""

    def test_from_uncompressed_hex(self):
        with pytest.deprecated_call():
            key = public_key_from_hex(PUBLIC_KEY_HEX_1)
        assert isinstance(key, PublicKey)
        assert key.format(compressed=False) == bytes.fromhex(PUBLIC_KEY_HEX_1.removeprefix("0x"))

    def test_without_0x_prefix(self):
        with pytest.deprecated_call():
            key = public_key_from_hex(PUBLIC_KEY_HEX_1.removeprefix("0x"))
        assert key.format(compressed=False) == bytes.fromhex(PUBLIC_KEY_HEX_1.removeprefix("0x"))


@pytest.mark.parametrize(
    ("helper", "key"),
    [(public_key_from_hex, PUBLIC_KEY_HEX_1), (public_key_from_bytes, bytes.fromhex(PUBLIC_KEY_HEX_1[2:]))],
    ids=["public_key_from_hex", "public_key_from_bytes"],
)
def test_public_key_helper_warns_that_it_is_deprecated(helper, key):
    with pytest.warns(DeprecationWarning, match="not used by the SDK; will be removed in a future major version"):
        helper(key)


class TestPublicKeyRoundTrip:
    def test_to_bytes_and_back(self):
        original = private_key_from_hex(PRIVATE_KEY_HEX_1).public_key
        raw = public_key_to_bytes(original)
        assert len(raw) == 65  # Uncompressed: 04 || x(32) || y(32)
        assert raw[0] == 0x04
        with pytest.deprecated_call():
            recovered = public_key_from_bytes(raw)
        assert recovered.format(compressed=False) == original.format(compressed=False)

    def test_from_compressed_bytes(self):
        """Compressed format (33 bytes) should also work."""
        original = private_key_from_hex(PRIVATE_KEY_HEX_1).public_key
        compressed = original.format(compressed=True)
        assert len(compressed) == 33
        with pytest.deprecated_call():
            recovered = public_key_from_bytes(compressed)
        assert recovered.format(compressed=False) == original.format(compressed=False)
