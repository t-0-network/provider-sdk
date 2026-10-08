package network.t0.sdk.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.t0.sdk.common.Headers;
import network.t0.sdk.common.Messages;
import network.t0.sdk.kycsharing.KycFiles;
import network.t0.sdk.network.NetworkClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The named constants and messages of the SDK against {@code constants} and {@code messages} in
 * cross_test/test_vectors.json, the file every SDK compares its constants with.
 */
class ContractConstantsTest {

    private static JsonObject vectors;

    @BeforeAll
    static void loadVectors() throws IOException {
        // Gradle runs tests with CWD = the module directory (java/sdk).
        vectors = JsonParser.parseString(Files.readString(Path.of("../../cross_test/test_vectors.json")))
                .getAsJsonObject();
    }

    @Test
    void constantsMatchTheSharedFile() {
        JsonObject constants = vectors.getAsJsonObject("constants");

        Map<String, Object> sdk = Map.of(
                "default_max_body_size", (long) ProviderServer.DEFAULT_MAX_BODY_SIZE,
                "timestamp_window_ms", ProviderServer.TIMESTAMP_WINDOW.toMillis(),
                "public_key_header", Headers.PUBLIC_KEY,
                "signature_header", Headers.SIGNATURE,
                "signature_timestamp_header", Headers.SIGNATURE_TIMESTAMP,
                "default_base_url", NetworkClient.DEFAULT_BASE_URL,
                "default_timeout_ms", NetworkClient.DEFAULT_TIMEOUT.toMillis(),
                "default_stream_timeout_ms", NetworkClient.DEFAULT_STREAM_TIMEOUT.toMillis(),
                "max_timeout_ms", NetworkClient.MAX_TIMEOUT.toMillis(),
                "kyc_file_chunk_max_bytes", (long) KycFiles.KYC_FILE_CHUNK_MAX_BYTES);

        assertThat(new TreeSet<>(sdk.keySet())).as("every shared constant is defined")
                .isEqualTo(new TreeSet<>(constants.keySet()));
        for (Map.Entry<String, JsonElement> e : constants.entrySet()) {
            Object value = sdk.get(e.getKey());
            Object expected = value instanceof Long ? (Object) e.getValue().getAsLong() : e.getValue().getAsString();
            assertThat(value).as(e.getKey()).isEqualTo(expected);
        }
    }

    /** The body limit a server uses for each max body size it is given: 0 or less keeps the default. */
    @Test
    @SuppressWarnings("deprecation")
    void maxBodySizeCasesMatchTheSharedFile() {
        String networkPublicKey = "0x" + vectors.getAsJsonObject("keys").get("public_key").getAsString();
        for (JsonElement element : vectors.getAsJsonArray("max_body_size_cases")) {
            JsonObject c = element.getAsJsonObject();
            int input = c.get("input").getAsInt();
            long limit = c.get("limit").getAsLong();
            assertThat((long) ProviderServer.create(0, networkPublicKey).withMaxBodySize(input).maxBodySize())
                    .as(c.get("name").getAsString()).isEqualTo(limit);
            assertThat((long) ProviderServer.create(0, networkPublicKey).withMaxInboundMessageSize(input).maxBodySize())
                    .as(c.get("name").getAsString() + " (withMaxInboundMessageSize)").isEqualTo(limit);
        }
    }

    /**
     * Each message is the constant of the same name in {@link Messages}; a placeholder is its
     * {@code %s}. Java defines every message except those whose messages_scope leaves Java out.
     */
    @Test
    void messagesMatchTheSharedFile() throws IllegalAccessException {
        JsonObject messages = vectors.getAsJsonObject("messages");
        JsonObject scope = vectors.getAsJsonObject("messages_scope");

        TreeSet<String> expectedNames = new TreeSet<>();
        for (String name : messages.keySet()) {
            boolean java = !scope.has(name) || scope.getAsJsonArray(name).contains(new com.google.gson.JsonPrimitive("java"));
            if (java) {
                expectedNames.add(name);
            }
        }

        Map<String, String> sdk = new HashMap<>();
        for (Field field : Messages.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType() == String.class) {
                sdk.put(field.getName().toLowerCase(Locale.ROOT), (String) field.get(null));
            }
        }

        assertThat(new TreeSet<>(sdk.keySet())).as("exactly the shared messages that apply to Java")
                .isEqualTo(expectedNames);
        for (String name : expectedNames) {
            String expected = messages.get(name).getAsString().replaceAll("\\{[a-z]+}", "SAMPLE");
            assertThat(String.format(sdk.get(name), "SAMPLE")).as(name).isEqualTo(expected);
        }
    }
}
