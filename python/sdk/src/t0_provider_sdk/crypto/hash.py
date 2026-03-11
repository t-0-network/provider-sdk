"""Keccak256 hash function (legacy, pre-standardization).

Uses pycryptodome's Keccak implementation. This is NOT the same as
hashlib.sha3_256() which uses the NIST-standardized SHA-3 (different padding).
"""

from Crypto.Hash import keccak


def legacy_keccak256(data: bytes) -> bytes:
    """Compute the legacy Keccak-256 hash of data.

    This is the Ethereum-style Keccak256, not NIST SHA3-256.
    """
    h = keccak.new(digest_bits=256)
    h.update(data)
    return h.digest()
