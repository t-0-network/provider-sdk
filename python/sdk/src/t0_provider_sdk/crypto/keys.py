"""Key conversion utilities for secp256k1 ECDSA keys."""

import binascii
import warnings

from coincurve import PrivateKey, PublicKey
from coincurve.utils import GROUP_ORDER_INT

from t0_provider_sdk._messages import (
    PRIVATE_KEY_EMPTY,
    PRIVATE_KEY_MALFORMED,
    PRIVATE_KEY_OUT_OF_RANGE,
    PUBLIC_KEY_NOT_A_POINT,
    PUBLIC_KEY_NOT_HEX,
)


def private_key_from_hex(hex_key: str) -> PrivateKey:
    """Create a PrivateKey from a hex-encoded string of 32 bytes.

    Supports an optional '0x' or '0X' prefix. Raises ValueError for an empty key, for anything that
    is not exactly 64 hex digits, and for a value outside [1, n-1].
    """
    if not hex_key:
        raise ValueError(PRIVATE_KEY_EMPTY)
    cleaned = hex_key[2:] if hex_key[:2] in ("0x", "0X") else hex_key
    # unhexlify, not bytes.fromhex, which skips whitespace; and the length is checked here, because the
    # curve library pads a short key. A malformed key would otherwise become a different, valid one.
    try:
        secret = binascii.unhexlify(cleaned)
    except ValueError:
        secret = b""
    if len(secret) != 32:
        raise ValueError(PRIVATE_KEY_MALFORMED)
    # GROUP_ORDER_INT is the order n of secp256k1.
    if not 0 < int.from_bytes(secret, "big") < GROUP_ORDER_INT:
        raise ValueError(PRIVATE_KEY_OUT_OF_RANGE)
    return PrivateKey(secret)


def public_key_from_private_key(private_key_hex: str) -> str:
    """The public key of a hex-encoded private key: "0x" and the 65-byte uncompressed key
    (04 || x || y) in lowercase hex.

    Parses the key as private_key_from_hex does, and raises the same ValueError.
    """
    return "0x" + private_key_from_hex(private_key_hex).public_key.format(compressed=False).hex()


@warnings.deprecated("public_key_from_hex is deprecated: not used by the SDK; will be removed in a future release")
def public_key_from_hex(hex_key: str) -> PublicKey:
    """Create a PublicKey from a hex-encoded string.

    Deprecated: not used by the SDK; will be removed in a future release.

    Follows the server's rule for public keys: an optional '0x' or '0X' prefix, strict hex, and a
    SEC1-encoded secp256k1 key (coincurve's parser). Raises ValueError.
    """
    return _parse_public_key(hex_key)


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
    """Parse a compressed (33 bytes, 02 or 03) or uncompressed (65 bytes, 04) point on secp256k1
    (rule V2). coincurve also accepts the hybrid forms (06, 07), so the form is checked first.
    Raises ValueError."""
    if not ((len(data) == 33 and data[0] in (2, 3)) or (len(data) == 65 and data[0] == 4)):
        raise ValueError(PUBLIC_KEY_NOT_A_POINT)
    try:
        return PublicKey(data)
    except ValueError as e:
        raise ValueError(PUBLIC_KEY_NOT_A_POINT) from e


def _parse_public_key(value: str) -> PublicKey:
    """The server's parser for the configured network key and the X-Public-Key header.

    Accepts only strict hex. Raises ValueError.
    """
    try:
        data = _decode_hex_strict(value)
    except ValueError as e:
        raise ValueError(PUBLIC_KEY_NOT_HEX) from e
    return _public_key_from_bytes_strict(data)


def public_key_to_bytes(key: PublicKey) -> bytes:
    """Serialize a PublicKey to 65 uncompressed bytes (04 || x || y)."""
    return key.format(compressed=False)


@warnings.deprecated("public_key_from_bytes is deprecated: not used by the SDK; will be removed in a future release")
def public_key_from_bytes(data: bytes) -> PublicKey:
    """Deserialize a PublicKey from bytes.

    Deprecated: not used by the SDK; will be removed in a future release.

    Accepts a SEC1-encoded secp256k1 key (coincurve's parser), as the server does. Raises ValueError.
    """
    return _public_key_from_bytes_strict(data)
