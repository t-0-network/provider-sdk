"""Keccak256 hash function (legacy, pre-standardization).

Uses pycryptodome's Keccak implementation. This is NOT the same as
hashlib.sha3_256() which uses the NIST-standardized SHA-3 (different padding).
"""

from Crypto.Hash import SHA256, keccak
from Crypto.Hash.SHA256 import SHA256Hash


def legacy_keccak256(data: bytes) -> bytes:
    """Compute the legacy Keccak-256 hash of data.

    This is the Ethereum-style Keccak256, not NIST SHA3-256.
    """
    h = keccak.new(digest_bits=256)
    h.update(data)
    return h.digest()


class _Prehashed:
    """A 32-byte digest computed elsewhere, as the hash object pycryptodome's DSS takes.

    DSS reads the digest from it, and its deterministic signer builds HMAC from it: new, digest_size
    and block_size are SHA-256's, so k comes from HMAC-SHA256 (RFC 6979) as in every SDK (rule S6),
    whatever hash produced the digest.
    """

    digest_size = SHA256.digest_size
    block_size = SHA256.block_size

    def __init__(self, digest: bytes) -> None:
        self._digest = digest

    def digest(self) -> bytes:
        return self._digest

    @staticmethod
    def new(data: bytes | None = None) -> SHA256Hash:
        return SHA256.new(data)
