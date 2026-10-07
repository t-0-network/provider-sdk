package network.t0.sdk.common;

import build.buf.validate.FieldPath;
import build.buf.validate.FieldPathElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the field_path_cases of cross_test/test_vectors.json through the formatter that writes the
 * field path of each violation for the server and {@code Validate.check}. It lives here because that
 * formatter is package-private.
 */
class FieldPathVectorTest {

    @Test
    void fieldPath_shouldMatchAllVectors() throws IOException {
        // Gradle runs tests with CWD = project root (java/sdk/)
        String json = Files.readString(Path.of("../../cross_test/test_vectors.json"));
        JsonArray cases = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("field_path_cases");
        assertThat(cases).isNotEmpty();

        SoftAssertions softly = new SoftAssertions();
        for (JsonElement element : cases) {
            JsonObject vec = element.getAsJsonObject();
            FieldPath.Builder path = FieldPath.newBuilder();
            for (JsonElement e : vec.getAsJsonArray("path")) {
                path.addElements(element(e.getAsJsonObject()));
            }
            softly.assertThat(ValidationUtils.fieldPath(path.build()))
                    .as(vec.get("name").getAsString())
                    .isEqualTo(vec.get("expected").getAsString());
        }
        softly.assertAll();
    }

    // int_key and uint_key are decimal strings in the file, so that every 64-bit value survives.
    private static FieldPathElement element(JsonObject e) {
        FieldPathElement.Builder b = FieldPathElement.newBuilder().setFieldName(e.get("field_name").getAsString());
        if (e.has("index")) {
            b.setIndex(e.get("index").getAsLong());
        } else if (e.has("bool_key")) {
            b.setBoolKey(e.get("bool_key").getAsBoolean());
        } else if (e.has("int_key")) {
            b.setIntKey(Long.parseLong(e.get("int_key").getAsString()));
        } else if (e.has("uint_key")) {
            b.setUintKey(Long.parseUnsignedLong(e.get("uint_key").getAsString()));
        } else if (e.has("string_key")) {
            b.setStringKey(e.get("string_key").getAsString());
        }
        return b.build();
    }
}
