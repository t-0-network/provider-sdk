"""ECDSA signing with secp256k1 (Ethereum-compatible).

The signing produces recoverable signatures in Ethereum format:
r (32 bytes) || s (32 bytes) || v (1 byte, recovery id 0 or 1).
"""

from typing import Protocol

from Crypto.PublicKey import ECC
from Crypto.PublicKey.ECC import EccKey
from Crypto.Signature import DSS

from t0_provider_sdk._messages import DIGEST_LENGTH
from t0_provider_sdk.crypto.hash import _Prehashed
from t0_provider_sdk.crypto.keys import _CURVE, _CURVE_ORDER, private_key_from_hex, public_key_to_bytes

# The generator G of secp256k1, as the public point of the key 1.
_GENERATOR = ECC.construct(curve=_CURVE, d=1).pointQ


class SignFn(Protocol):
    """A signer: takes the 32-byte digest of a request and returns (signature, public_key).

    The signature is 65 bytes r || s || v (or 64 bytes r || s), sent as it is; the public key is the
    65-byte uncompressed key (04 || x || y). new_signer and new_signer_from_hex build one; a client
    takes it as its signer.
    """

    def __call__(self, digest: bytes) -> tuple[bytes, bytes]: ...


def new_signer(private_key: EccKey) -> SignFn:
    """Create a signing function from a private key.

    The returned function signs a pre-hashed 32-byte digest and returns
    (signature, public_key) where both are in uncompressed format. A digest of another length
    raises ValueError ("digest must be 32 bytes").
    """
    public_key = private_key.public_key()
    pub_key_bytes = public_key_to_bytes(public_key)

    def sign(digest: bytes) -> tuple[bytes, bytes]:
        if len(digest) != 32:
            raise ValueError(DIGEST_LENGTH)
        # RFC 6979 with HMAC-SHA256 (_Prehashed) and a low s: the signature libsecp256k1 makes.
        rs = DSS.new(private_key, "deterministic-rfc6979", low_s=True).sign(_Prehashed(digest))
        return rs + bytes([_recovery_id(public_key, digest, rs)]), pub_key_bytes

    return sign


def new_signer_from_hex(hex_key: str) -> SignFn:
    """Create a signing function from a hex-encoded private key."""
    return new_signer(private_key_from_hex(hex_key))


def _recovery_id(public_key: EccKey, digest: bytes, rs: bytes) -> int:
    """v: the parity of y of the signature's point R, which DSS does not return.

    R is computed again from public values only, as a verifier does: R = (e/s)·G + (r/s)·Q. Its x is r;
    an x at or above n, for which libsecp256k1 would give 2 or 3, has a probability of about 2^-128.
    """
    r = int.from_bytes(rs[:32], "big")
    s_inverse = pow(int.from_bytes(rs[32:], "big"), -1, _CURVE_ORDER)
    e = int.from_bytes(digest, "big")
    point = _GENERATOR * (e * s_inverse % _CURVE_ORDER) + public_key.pointQ * (r * s_inverse % _CURVE_ORDER)
    return point.y & 1
