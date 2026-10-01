"""Key conversion utilities for secp256k1 ECDSA keys."""

import binascii

from coincurve import PrivateKey, PublicKey
from coincurve.utils import GROUP_ORDER_INT


def private_key_from_hex(hex_key: str) -> PrivateKey:
    """Create a PrivateKey from a hex-encoded string of 32 bytes.

    Supports an optional '0x' or '0X' prefix. Raises ValueError for an empty key, for anything that
    is not exactly 64 hex digits, and for a value outside [1, n-1].
    """
    if not hex_key:
        raise ValueError("private key must not be null or empty")
    cleaned = hex_key[2:] if hex_key[:2] in ("0x", "0X") else hex_key
    # unhexlify, not bytes.fromhex, which skips whitespace; and the length is checked here, because the
    # curve library pads a short key. A malformed key would otherwise become a different, valid one.
    try:
        secret = binascii.unhexlify(cleaned)
    except ValueError:
        secret = b""
    if len(secret) != 32:
        raise ValueError("private key must be 32 bytes (64 hex characters)")
    # GROUP_ORDER_INT is the order n of secp256k1.
    if not 0 < int.from_bytes(secret, "big") < GROUP_ORDER_INT:
        raise ValueError("private key must be in range [1, n-1]")
    return PrivateKey(secret)


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
