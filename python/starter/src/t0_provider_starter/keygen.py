"""Key generation utilities for T-0 Network providers."""

from coincurve import PrivateKey


def generate_keypair() -> tuple[str, str]:
    """Generate a new secp256k1 keypair.

    Returns:
        Tuple of (private_key_hex, public_key_hex), both with 0x prefix.
        Public key is in uncompressed format (65 bytes).
    """
    key = PrivateKey()
    private_hex = "0x" + key.secret.hex()
    public_hex = "0x" + key.public_key.format(compressed=False).hex()
    return private_hex, public_hex
