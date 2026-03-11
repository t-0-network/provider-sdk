package network.t0.sdk.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Keccak256 hash function.
 */
class Keccak256Test {

    // Test vector
    private static final String MESSAGE = "please sign me!";
    private static final String EXPECTED_HASH = "46d5cbf7d8477720c337b94a2fe332fe54205914619e7e4889595c3c944b646b";

    @Test
    void hash_shouldMatchExpectedValue() {
        byte[] message = MESSAGE.getBytes();
        byte[] hash = Keccak256.hash(message);

        assertThat(bytesToHex(hash)).isEqualTo(EXPECTED_HASH);
    }

    @Test
    void hash_shouldProduceCorrectLength() {
        byte[] hash = Keccak256.hash("test".getBytes());
        assertThat(hash).hasSize(32);
    }

    @Test
    void hash_emptyInput_shouldWork() {
        byte[] hash = Keccak256.hash(new byte[0]);
        assertThat(hash).hasSize(32);
        // Keccak-256 of empty input
        assertThat(bytesToHex(hash))
                .isEqualTo("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    }

    @Test
    void hash_nullInput_shouldThrow() {
        assertThatThrownBy(() -> Keccak256.hash((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("data must not be null");
    }

    @Test
    void hash_multipleInputs_shouldConcatenateAndHash() {
        byte[] part1 = "test request body".getBytes();
        byte[] part2 = hexToBytes("002486358d010000"); // timestamp bytes from Go test vector

        byte[] hash = Keccak256.hash(part1, part2);

        // Expected: Keccak256(body + timestampBytes)
        assertThat(bytesToHex(hash))
                .isEqualTo("49a567a359bf25d9652b24acc5567bc38c93139467c8fcf798f059ada585697e");
    }

    @Test
    void hash_isNotSha3_256() {
        // SHA3-256 and Keccak-256 produce different results due to different padding
        // This test ensures we're using Keccak-256 (legacy), not SHA3-256 (NIST)
        byte[] message = "test".getBytes();
        byte[] hash = Keccak256.hash(message);

        // SHA3-256("test") = "36f028580bb02cc8272a9a020f4200e346e276ae664e45ee80745574e2f5ab80"
        // Keccak-256("test") = "9c22ff5f21f0b81b113e63f7db6da94fedef11b2119b4088b89664fb9a3cb658"
        assertThat(bytesToHex(hash))
                .isEqualTo("9c22ff5f21f0b81b113e63f7db6da94fedef11b2119b4088b89664fb9a3cb658");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
