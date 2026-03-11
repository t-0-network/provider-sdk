package network.t0.sdk.crypto;

import org.bouncycastle.jcajce.provider.digest.Keccak;

/**
 * Keccak-256 hash function implementation.
 *
 * <p>This uses the original Keccak algorithm (before NIST standardization as SHA-3),
 * which is the same algorithm used by Ethereum for signing.
 *
 * <p>Note: SHA3-256 (NIST SHA-3) uses different padding than Keccak-256.
 * Ethereum and this SDK use the original Keccak-256 ("Legacy Keccak").
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All methods are stateless and can be
 * called concurrently from multiple threads without synchronization.
 */
public final class Keccak256 {

    private Keccak256() {
        // Utility class
    }

    /**
     * Computes the Keccak-256 hash of the input data.
     *
     * @param data the input data to hash
     * @return 32-byte Keccak-256 hash
     */
    public static byte[] hash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }

        Keccak.Digest256 digest = new Keccak.Digest256();
        digest.update(data);
        return digest.digest();
    }

    /**
     * Computes the Keccak-256 hash of multiple byte arrays concatenated.
     *
     * @param parts the byte arrays to concatenate and hash
     * @return 32-byte Keccak-256 hash
     */
    public static byte[] hash(byte[]... parts) {
        Keccak.Digest256 digest = new Keccak.Digest256();
        for (byte[] part : parts) {
            if (part != null) {
                digest.update(part);
            }
        }
        return digest.digest();
    }
}
