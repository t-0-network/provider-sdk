"""Signature verification using secp256k1 public key recovery.

Uses the recovery-based approach: recover the public key from the signature
and compare it to the expected key. This uses only public coincurve API
and handles both 64-byte (r+s) and 65-byte (r+s+v) signatures.
"""

from coincurve import PublicKey


def verify_signature(public_key: PublicKey, digest: bytes, signature: bytes) -> bool:
    """Verify an ECDSA signature against a public key.

    Args:
        public_key: Expected signer's public key.
        digest: 32-byte pre-hashed message digest.
        signature: 64 bytes (r+s) or 65 bytes (r+s+v) signature.

    Returns:
        True if the signature is valid and was produced by the given public key.
    """
    if len(digest) != 32:
        return False
    if len(signature) not in (64, 65):
        return False

    expected = public_key.format(compressed=False)

    if len(signature) == 65:
        # Full recoverable signature with v byte
        return _verify_recoverable(expected, digest, signature)

    # 64-byte signature without recovery id — try both possible values
    for v in (0, 1):
        recoverable_sig = signature + bytes([v])
        if _verify_recoverable(expected, digest, recoverable_sig):
            return True
    return False


def _verify_recoverable(expected_uncompressed: bytes, digest: bytes, sig_65: bytes) -> bool:
    """Attempt to verify by recovering the public key from a 65-byte signature."""
    try:
        recovered = PublicKey.from_signature_and_message(sig_65, digest, hasher=None)
        return recovered.format(compressed=False) == expected_uncompressed
    except Exception:
        return False
