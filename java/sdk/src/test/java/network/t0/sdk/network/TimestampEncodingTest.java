package network.t0.sdk.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for timestamp encoding using little-endian format.
 */
class TimestampEncodingTest {

    /**
     * Encodes a timestamp as little-endian 8 bytes.
     */
    private static byte[] encodeTimestamp(long timestampMs) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(timestampMs);
        return buffer.array();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0000000000000000",
            "1, 0100000000000000",
            "255, ff00000000000000",
            "256, 0001000000000000",
            "1000, e803000000000000",
            "1706000000000, 002486358d010000",
            "9223372036854775807, ffffffffffffff7f"  // Long.MAX_VALUE
    })
    void encodeTimestamp_matchesExpectedValues(long timestampMs, String expectedHex) {
        byte[] encoded = encodeTimestamp(timestampMs);

        assertThat(bytesToHex(encoded)).isEqualTo(expectedHex);
    }

    @Test
    void encodeTimestamp_shouldBe8Bytes() {
        byte[] encoded = encodeTimestamp(1706000000000L);

        assertThat(encoded).hasSize(8);
    }

    @Test
    void encodeTimestamp_isLittleEndian() {
        // Little-endian: least significant byte first
        // 0x0100 = 256 in little-endian is: 00 01 00 00 00 00 00 00
        byte[] encoded = encodeTimestamp(256);

        assertThat(encoded[0]).isEqualTo((byte) 0x00);
        assertThat(encoded[1]).isEqualTo((byte) 0x01);
        assertThat(encoded[2]).isEqualTo((byte) 0x00);
    }

    @Test
    void encodeTimestamp_negativeValue_shouldWork() {
        // Java's putLong handles negative values by interpreting as unsigned
        byte[] encoded = encodeTimestamp(-1);

        // -1 in two's complement is all 1s
        assertThat(bytesToHex(encoded)).isEqualTo("ffffffffffffffff");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
