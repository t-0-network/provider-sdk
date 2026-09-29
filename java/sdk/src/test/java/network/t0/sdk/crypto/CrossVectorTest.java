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
import java.util.Arrays;

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

    @Test
    void requestSignature_shouldMatchExpected() {
        JsonObject keys = vectors.getAsJsonObject("keys");
        JsonObject rs = vectors.getAsJsonObject("request_signing");
        String privateKeyHex = keys.get("private_key").getAsString();
        String expectedSignature = rs.get("expected_signature").getAsString();

        byte[] body = rs.get("body").getAsString().getBytes();
        long timestampMs = rs.get("timestamp_ms").getAsLong();
        byte[] tsBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(timestampMs)
                .array();

        byte[] digest = Keccak256.hash(body, tsBytes);
        assertThat(HexUtils.bytesToHex(digest))
                .isEqualTo(rs.get("expected_hash").getAsString());

        Signer signer = Signer.fromHex(privateKeyHex);
        SignResult result = signer.sign(digest);
        // Compare first 64 bytes (r+s) against the cross-language test vector
        byte[] sig64 = new byte[64];
        System.arraycopy(result.getSignature(), 0, sig64, 0, 64);
        assertThat(HexUtils.bytesToHex(sig64)).isEqualTo(expectedSignature);
    }

    /**
     * Bodies the string-valued request_signing block cannot express: binary, gRPC-framed
     * (which is what this SDK verifies as its second path), empty, and one whose signature
     * has a leading zero byte — the case a signer that trims instead of padding to a fixed
     * 32 bytes fails, and only that case.
     */
    @Test
    void requestSigningCases_shouldMatchVectorBytes() {
        JsonObject keys = vectors.getAsJsonObject("keys");
        Signer signer = Signer.fromHex(keys.get("private_key").getAsString());

        JsonArray cases = vectors.getAsJsonArray("request_signing_cases");
        assertThat(cases).isNotEmpty();

        for (var element : cases) {
            JsonObject vec = element.getAsJsonObject();
            String name = vec.get("name").getAsString();
            byte[] digest = requestDigest(vec);

            assertThat(HexUtils.bytesToHex(digest))
                    .as("digest for %s", name)
                    .isEqualTo(vec.get("expected_hash").getAsString());

            byte[] sig64 = Arrays.copyOf(signer.sign(digest).getSignature(), 64);
            assertThat(HexUtils.bytesToHex(sig64))
                    .as("signature for %s", name)
                    .isEqualTo(vec.get("expected_signature").getAsString());
        }
    }

    /** The presented-request cases, including the ones a provider has to refuse. */
    @Test
    void signatureVerification_shouldMatchVectorOutcomes() {
        JsonArray cases = vectors.getAsJsonArray("signature_verification");
        assertThat(cases).isNotEmpty();

        for (var element : cases) {
            JsonObject vec = element.getAsJsonObject();
            byte[] publicKey = HexUtils.hexToBytes(vec.get("public_key").getAsString());
            byte[] signature = HexUtils.hexToBytes(vec.get("signature").getAsString());

            assertThat(SignatureVerifier.verify(publicKey, requestDigest(vec), signature))
                    .as("%s: %s", vec.get("name").getAsString(), vec.get("note").getAsString())
                    .isEqualTo(vec.get("valid").getAsBoolean());
        }
    }

    /** What a provider hashes: the raw body with the little-endian timestamp appended. */
    private static byte[] requestDigest(JsonObject vec) {
        byte[] body = HexUtils.hexToBytes(vec.get("body_hex").getAsString());
        byte[] tsBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(vec.get("timestamp_ms").getAsLong())
                .array();

        return Keccak256.hash(body, tsBytes);
    }
}
