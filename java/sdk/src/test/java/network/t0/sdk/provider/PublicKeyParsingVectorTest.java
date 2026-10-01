package network.t0.sdk.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.t0.sdk.common.HexUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the public_key_parsing vectors of cross_test/test_vectors.json through the parser used for
 * the configured network key and the X-Public-Key header. It lives here rather than in
 * {@code CrossVectorTest} because that parser is package-private.
 */
class PublicKeyParsingVectorTest {

    @Test
    void publicKeyParsing_shouldMatchAllVectors() throws IOException {
        // Gradle runs tests with CWD = project root (java/sdk/)
        String json = Files.readString(Path.of("../../cross_test/test_vectors.json"));
        JsonArray cases = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("public_key_parsing");
        assertThat(cases).isNotEmpty();

        for (var element : cases) {
            JsonObject vec = element.getAsJsonObject();
            String name = vec.get("name").getAsString();
            String input = vec.get("input").getAsString();

            if (vec.get("valid").getAsBoolean()) {
                assertThat(HexUtils.bytesToHex(SignatureVerificationInterceptor.parsePublicKey(input)))
                        .as(name)
                        .isEqualTo(vec.get("uncompressed").getAsString());
            } else {
                assertThatThrownBy(() -> SignatureVerificationInterceptor.parsePublicKey(input))
                        .as(name)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
