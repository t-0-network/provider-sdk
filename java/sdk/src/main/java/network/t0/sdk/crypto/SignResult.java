package network.t0.sdk.crypto;

import network.t0.sdk.common.HexUtils;

import java.util.Arrays;

/**
 * Result of a signing operation, containing the signature and public key.
 */
public final class SignResult {

    private static final int SIGNATURE_LENGTH = 65;
    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final int R_LENGTH = 32;
    private static final int S_LENGTH = 32;

    private final byte[] signature;
    private final byte[] publicKey;

    /**
     * Creates a new SignResult.
     *
     * @param signature the 65-byte Ethereum-style signature (r[32] + s[32] + v[1])
     * @param publicKey the 65-byte uncompressed public key
     */
    public SignResult(byte[] signature, byte[] publicKey) {
        if (signature == null || signature.length != SIGNATURE_LENGTH) {
            throw new IllegalArgumentException("signature must be 65 bytes");
        }
        if (publicKey == null || publicKey.length != PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("publicKey must be 65 bytes");
        }
        this.signature = Arrays.copyOf(signature, signature.length);
        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
    }

    /**
     * Returns the 65-byte Ethereum-style signature.
     * Format: r[32] + s[32] + v[1], where v is the recovery ID (0 or 1).
     *
     * @return copy of the signature bytes
     */
    public byte[] getSignature() {
        return Arrays.copyOf(signature, signature.length);
    }

    /**
     * Returns the 65-byte uncompressed public key.
     * Format: 0x04 prefix + x[32] + y[32]
     *
     * @return copy of the public key bytes
     */
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }

    /**
     * Returns the R component of the signature (first 32 bytes).
     *
     * @return R component
     */
    public byte[] getR() {
        return Arrays.copyOfRange(signature, 0, R_LENGTH);
    }

    /**
     * Returns the S component of the signature (bytes 32-64).
     *
     * @return S component
     */
    public byte[] getS() {
        return Arrays.copyOfRange(signature, R_LENGTH, R_LENGTH + S_LENGTH);
    }

    /**
     * Returns the V (recovery ID) component of the signature (last byte).
     *
     * @return V component (0 or 1)
     */
    public int getV() {
        return signature[R_LENGTH + S_LENGTH] & 0xFF;
    }

    /**
     * Returns the signature as a hex string with 0x prefix.
     *
     * @return hex-encoded signature
     */
    public String getSignatureHex() {
        return "0x" + HexUtils.bytesToHex(signature);
    }

    /**
     * Returns the public key as a hex string with 0x prefix.
     *
     * @return hex-encoded public key
     */
    public String getPublicKeyHex() {
        return "0x" + HexUtils.bytesToHex(publicKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignResult that)) return false;
        return Arrays.equals(signature, that.signature) && Arrays.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(signature);
        result = 31 * result + Arrays.hashCode(publicKey);
        return result;
    }

    @Override
    public String toString() {
        return "SignResult{signature=" + getSignatureHex() + ", publicKey=" + getPublicKeyHex() + "}";
    }
}
