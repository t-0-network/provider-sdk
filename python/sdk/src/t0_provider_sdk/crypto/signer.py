"""ECDSA signing with secp256k1 (Ethereum-compatible).

The signing produces recoverable signatures in Ethereum format:
r (32 bytes) || s (32 bytes) || v (1 byte, recovery id 0 or 1).
"""

from typing import Protocol

from coincurve import PrivateKey

from t0_provider_sdk.crypto.keys import private_key_from_hex


class SignFn(Protocol):
    """Signature function protocol.

    Takes a 32-byte digest and returns (signature_65_bytes, public_key_65_bytes).
    """

    def __call__(self, digest: bytes) -> tuple[bytes, bytes]: ...


def new_signer(private_key: PrivateKey) -> SignFn:
    """Create a signing function from a private key.

    The returned function signs a pre-hashed 32-byte digest and returns
    (signature, public_key) where both are in uncompressed format.
    """
    pub_key_bytes = private_key.public_key.format(compressed=False)

    def sign(digest: bytes) -> tuple[bytes, bytes]:
        # hasher=None means we pass a pre-hashed digest (no internal hashing)
        sig = private_key.sign_recoverable(digest, hasher=None)
        # coincurve returns: r(32) + s(32) + recovery_id(1) = 65 bytes
        # This matches the Ethereum signature format
        return sig, pub_key_bytes

    return sign


def new_signer_from_hex(hex_key: str) -> SignFn:
    """Create a signing function from a hex-encoded private key."""
    return new_signer(private_key_from_hex(hex_key))
