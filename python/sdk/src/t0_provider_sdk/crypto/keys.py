"""Key conversion utilities for secp256k1 ECDSA keys."""

from coincurve import PrivateKey, PublicKey

PRIVATE_KEY_HEX_LENGTH = 64


def private_key_from_hex(hex_key: str) -> PrivateKey:
    """Create a PrivateKey from a hex-encoded string of 32 bytes.

    Supports optional '0x' prefix. Raises ValueError for an empty key or one of another length.
    """
    if not hex_key:
        raise ValueError("private key must not be null or empty")
    cleaned = hex_key.removeprefix("0x")
    # Checked here: the curve library would accept a shorter key and pad it.
    if len(cleaned) != PRIVATE_KEY_HEX_LENGTH:
        raise ValueError("private key must be 32 bytes (64 hex characters)")
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
