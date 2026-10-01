package network.t0.sdk.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the timestamp_parsing vectors of cross_test/test_vectors.json through the parser used for the
 * X-Signature-Timestamp header. It lives here rather than in {@code CrossVectorTest} because that
 * parser is package-private.
 */
class TimestampParsingVectorTest {

    @Test
    void timestampParsing_shouldMatchAllVectors() throws IOException {
        // Gradle runs tests with CWD = project root (java/sdk/)
        String json = Files.readString(Path.of("../../cross_test/test_vectors.json"));
        JsonArray cases = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("timestamp_parsing");
        assertThat(cases).isNotEmpty();

        for (var element : cases) {
            JsonObject vec = element.getAsJsonObject();
            String name = vec.get("name").getAsString();
            String input = vec.get("input").getAsString();

            if (vec.get("valid").getAsBoolean()) {
                assertThat(Long.toString(SignatureVerificationInterceptor.parseTimestamp(input)))
                        .as(name)
                        .isEqualTo(vec.get("value").getAsString());
            } else {
                assertThatThrownBy(() -> SignatureVerificationInterceptor.parseTimestamp(input))
                        .as(name)
                        .isInstanceOf(NumberFormatException.class);
            }
        }
    }
}
