package network.t0.sdk.crypto;

import network.t0.sdk.common.HexUtils;

/**
 * Signs the 32-byte Keccak-256 digest of a request with a secp256k1 key.
 *
 * <p>{@link Signer} implements it with a private key held in memory. Implement it to keep the key
 * elsewhere, for example in a hardware security module or a remote signing service.
 */
public interface DigestSigner {

    /**
     * Signs a 32-byte digest.
     *
     * @param digest the 32-byte digest to sign
     * @return the signature (64 or 65 bytes) and the 65-byte uncompressed public key
     */
    SignResult sign(byte[] digest);

    /**
     * Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).
     *
     * @return the public key bytes
     */
    byte[] getPublicKey();

    /**
     * Returns the public key as hex string (without 0x prefix).
     *
     * @return hex-encoded public key
     */
    default String getPublicKeyHex() {
        return HexUtils.bytesToHex(getPublicKey());
    }

    /**
     * Returns the public key as hex string with 0x prefix.
     *
     * @return hex-encoded public key with 0x prefix
     */
    default String getPublicKeyHexPrefixed() {
        return "0x" + getPublicKeyHex();
    }
}
