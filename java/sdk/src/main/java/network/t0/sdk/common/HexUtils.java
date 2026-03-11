package network.t0.sdk.common;

/**
 * Utility class for hexadecimal encoding and decoding.
 *
 * <p>This class is thread-safe. All methods are stateless and can be called
 * concurrently from multiple threads without synchronization.
 */
public final class HexUtils {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

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
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("hex string must have even length");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("invalid hex character at position " + i);
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
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

        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            result[i * 2] = HEX_CHARS[v >>> 4];
            result[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(result);
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
