"""Signature verification using secp256k1 public key recovery.

Uses the recovery-based approach: recover the public key from r and s with each
recovery id and compare it to the expected key. This uses only public coincurve API
and, unlike libsecp256k1's verify, accepts a high s as every SDK does. A 65-byte
signature (r+s+v) is verified as its first 64 bytes: v is ignored, as in every SDK.
"""

from coincurve import PublicKey


def verify_signature(public_key: PublicKey, digest: bytes, signature: bytes) -> bool:
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

    expected = public_key.format(compressed=False)
    rs = signature[:64]

    # Try both recovery ids: the one in a 65-byte signature is not trusted.
    return any(_verify_recoverable(expected, digest, rs + bytes([v])) for v in (0, 1))


def _verify_recoverable(expected_uncompressed: bytes, digest: bytes, sig_65: bytes) -> bool:
    """Attempt to verify by recovering the public key from a 65-byte signature."""
    try:
        recovered = PublicKey.from_signature_and_message(sig_65, digest, hasher=None)
        return recovered.format(compressed=False) == expected_uncompressed
    except Exception:
        return False
