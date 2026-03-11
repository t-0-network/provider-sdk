"""Key conversion utilities for secp256k1 ECDSA keys."""

from coincurve import PrivateKey, PublicKey


def private_key_from_hex(hex_key: str) -> PrivateKey:
    """Create a PrivateKey from a hex-encoded string.

    Supports optional '0x' prefix.
    """
    cleaned = hex_key.removeprefix("0x")
    return PrivateKey(bytes.fromhex(cleaned))


def public_key_from_hex(hex_key: str) -> PublicKey:
    """Create a PublicKey from a hex-encoded string.

    Supports optional '0x' prefix. Accepts both compressed (33 bytes)
    and uncompressed (65 bytes) formats.
    """
    cleaned = hex_key.removeprefix("0x")
    return PublicKey(bytes.fromhex(cleaned))


def public_key_to_bytes(key: PublicKey) -> bytes:
    """Serialize a PublicKey to 65 uncompressed bytes (04 || x || y)."""
    return key.format(compressed=False)


def public_key_from_bytes(data: bytes) -> PublicKey:
    """Deserialize a PublicKey from bytes.

    Accepts both compressed (33 bytes) and uncompressed (65 bytes) formats.
    """
    return PublicKey(data)
