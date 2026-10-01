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


def _decode_hex_strict(value: str) -> bytes:
    """Decode hex with an optional '0x' or '0X' prefix: at least one byte, nothing but hex digits.

    unhexlify, not bytes.fromhex, which skips whitespace. Raises ValueError.
    """
    cleaned = value[2:] if value[:2] in ("0x", "0X") else value
    if not cleaned:
        raise ValueError("not hex: no digits")
    try:
        return binascii.unhexlify(cleaned)
    except ValueError as e:
        raise ValueError(f"not hex: {e}") from e


def _public_key_from_bytes_strict(data: bytes) -> PublicKey:
    """Parse a compressed (33 bytes, 02/03) or uncompressed (65 bytes, 04) key on secp256k1.

    The prefix is checked here because the curve library also accepts the hybrid encoding (06/07).
    Raises ValueError.
    """
    if not ((len(data) == 33 and data[0] in (0x02, 0x03)) or (len(data) == 65 and data[0] == 0x04)):
        raise ValueError("public key must be 33 bytes starting with 02 or 03, or 65 bytes starting with 04")
    try:
        return PublicKey(data)
    except ValueError as e:
        raise ValueError("public key is not a point on secp256k1") from e


def _parse_public_key(value: str) -> PublicKey:
    """The server's parser for the configured network key and the X-Public-Key header.

    Unlike public_key_from_hex, it accepts only strict hex and only the compressed and uncompressed
    encodings. Raises ValueError.
    """
    return _public_key_from_bytes_strict(_decode_hex_strict(value))


def public_key_to_bytes(key: PublicKey) -> bytes:
    """Serialize a PublicKey to 65 uncompressed bytes (04 || x || y)."""
    return key.format(compressed=False)


def public_key_from_bytes(data: bytes) -> PublicKey:
    """Deserialize a PublicKey from bytes.

    Accepts both compressed (33 bytes) and uncompressed (65 bytes) formats.
    """
    return PublicKey(data)
