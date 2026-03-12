package network.t0.sdk.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.t0.sdk.common.HexUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests using shared cross-language test vectors from cross_test/test_vectors.json.
 */
class CrossVectorTest {

    private static JsonObject vectors;

    @BeforeAll
    static void loadVectors() throws IOException {
        // Gradle runs tests with CWD = project root (java/sdk/)
        Path vectorsPath = Path.of("../../cross_test/test_vectors.json");
        String json = Files.readString(vectorsPath);
        vectors = JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void keccak256_shouldMatchAllVectors() {
        JsonArray keccakVectors = vectors.getAsJsonArray("keccak256");
        for (var element : keccakVectors) {
            JsonObject vec = element.getAsJsonObject();
            String input = vec.get("input").getAsString();
            String expectedHash = vec.get("hash").getAsString();

            byte[] hash = Keccak256.hash(input.getBytes());
            assertThat(HexUtils.bytesToHex(hash))
                    .as("Keccak256 of \"%s\"", input)
                    .isEqualTo(expectedHash);
        }
    }

    @Test
    void keyDerivation_shouldMatchVectorPublicKey() {
        JsonObject keys = vectors.getAsJsonObject("keys");
        String privateKeyHex = keys.get("private_key").getAsString();
        String expectedPublicKeyHex = keys.get("public_key").getAsString();

        Signer signer = Signer.fromHex(privateKeyHex);
        assertThat(HexUtils.bytesToHex(signer.getPublicKey()))
                .isEqualTo(expectedPublicKeyHex);
    }

    @Test
    void requestHash_shouldMatchExpected() {
        JsonObject rs = vectors.getAsJsonObject("request_signing");
        String body = rs.get("body").getAsString();
        long timestampMs = rs.get("timestamp_ms").getAsLong();
        String expectedHash = rs.get("expected_hash").getAsString();

        byte[] tsBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(timestampMs)
                .array();

        byte[] hash = Keccak256.hash(body.getBytes(), tsBytes);
        assertThat(HexUtils.bytesToHex(hash)).isEqualTo(expectedHash);
    }

    @Test
    void signVerifyRoundTrip_shouldSucceed() {
        JsonObject keys = vectors.getAsJsonObject("keys");
        String privateKeyHex = keys.get("private_key").getAsString();

        Signer signer = Signer.fromHex(privateKeyHex);
        byte[] digest = Keccak256.hash("round trip test".getBytes());

        SignResult result = signer.sign(digest);
        boolean valid = SignatureVerifier.verify(
                signer.getPublicKey(), digest, result.getSignature());
        assertThat(valid).isTrue();
    }
}
