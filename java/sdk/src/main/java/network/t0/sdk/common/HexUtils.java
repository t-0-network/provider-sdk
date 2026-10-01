package network.t0.sdk.common;

import java.util.HexFormat;

/**
 * Utility class for hexadecimal encoding and decoding.
 *
 * <p>This class is thread-safe. All methods are stateless and can be called
 * concurrently from multiple threads without synchronization.
 */
public final class HexUtils {

    private static final HexFormat HEX = HexFormat.of();

    private HexUtils() {
        // Utility class
    }

    /**
     * Converts a hex string to bytes.
     *
     * @param hex the hex string (without 0x prefix)
     * @return the decoded bytes
     * @throws IllegalArgumentException if the hex string is invalid
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex string must not be null");
        }
        // ASCII 0-9, a-f and A-F only. Non-ASCII digits are rejected.
        return HEX.parseHex(hex);
    }

    /**
     * Converts bytes to a hex string.
     *
     * @param bytes the bytes to encode
     * @return the hex string (without 0x prefix)
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        return HEX.formatHex(bytes);
    }

    /**
     * Removes the 0x prefix from a hex string if present.
     *
     * @param hex the hex string (with or without 0x prefix)
     * @return the hex string without prefix
     */
    public static String stripHexPrefix(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.length() >= 2 && hex.charAt(0) == '0' &&
                (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
            return hex.substring(2);
        }
        return hex;
    }

    /**
     * Adds the 0x prefix to a hex string if not present.
     *
     * @param hex the hex string
     * @return the hex string with 0x prefix
     */
    public static String addHexPrefix(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.length() >= 2 && hex.charAt(0) == '0' &&
                (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
            return hex;
        }
        return "0x" + hex;
    }
}
