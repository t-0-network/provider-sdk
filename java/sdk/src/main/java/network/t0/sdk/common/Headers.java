package network.t0.sdk.common;

/**
 * HTTP header names used for request signing and verification.
 *
 * <p>These headers are used to transport the ECDSA signature and related metadata
 * between the provider and T-0 Network.
 */
public final class Headers {

    /**
     * The signature header containing the ECDSA signature in hex format.
     * Value format: "0x" + hex(signature[65])
     */
    public static final String SIGNATURE = "X-Signature";

    /**
     * The timestamp header containing the Unix timestamp in milliseconds.
     * Value format: decimal string representing milliseconds since epoch
     */
    public static final String SIGNATURE_TIMESTAMP = "X-Signature-Timestamp";

    /**
     * The public key header containing the signer's public key in hex format.
     * Value format: "0x" + hex(publicKey[65])
     */
    public static final String PUBLIC_KEY = "X-Public-Key";

    /**
     * The validity window for timestamp verification (in milliseconds).
     * Requests with timestamps outside this window will be rejected.
     */
    public static final long TIMESTAMP_VALIDITY_WINDOW_MS = 60_000; // 60 seconds

    private Headers() {
        // Constants class
    }

    /**
     * Encodes a timestamp as 8-byte little-endian bytes.
     *
     * @param timestampMs the timestamp in milliseconds since epoch
     * @return 8-byte little-endian encoded timestamp
     */
    public static byte[] encodeTimestamp(long timestampMs) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (timestampMs & 0xFF);
            timestampMs >>= 8;
        }
        return bytes;
    }
}
