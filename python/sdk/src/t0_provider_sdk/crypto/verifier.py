"""Signature verification with secp256k1.

A high s verifies, as in every SDK. A 65-byte signature (r+s+v) is verified as its first
64 bytes: v is ignored, as in every SDK.
"""

from Crypto.PublicKey.ECC import EccKey
from Crypto.Signature import DSS

from t0_provider_sdk.crypto.hash import _Prehashed


def verify_signature(public_key: EccKey, digest: bytes, signature: bytes) -> bool:
    """Verify an ECDSA signature against a public key.

    Args:
        public_key: Expected signer's public key.
        digest: 32-byte pre-hashed message digest.
        signature: 64 bytes (r+s) or 65 bytes (r+s+v) signature. The recovery byte v
            is ignored: only r and s are checked.

    Returns:
        True if the signature is valid and was produced by the given public key.
    """
    if len(digest) != 32:
        return False
    if len(signature) not in (64, 65):
        return False
    # Verifying is the same in every DSS mode. low_s is off, so a high s verifies; r and s must be in
    # [1, n-1].
    try:
        DSS.new(public_key, "deterministic-rfc6979").verify(_Prehashed(digest), signature[:64])
    except ValueError:
        return False
    return True
