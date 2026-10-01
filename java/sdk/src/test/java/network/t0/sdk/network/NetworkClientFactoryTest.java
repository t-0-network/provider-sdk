package network.t0.sdk.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.grpc.health.v1.HealthGrpc;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests what the clients' {@code create} methods accept and refuse, before any call is made. */
class NetworkClientFactoryTest {

    private static final Signer SIGNER =
            Signer.fromHex("6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8");

    @Test
    @DisplayName("A null base URL selects https://api.t-0.network")
    void nullBaseUrlSelectsTheDefault() {
        assertThat(NetworkClient.parseEndpoint(null))
                .isEqualTo(new NetworkClient.EndpointInfo("api.t-0.network", 443, false));
        try (var client = BlockingNetworkClient.create(null, SIGNER, HealthGrpc::newBlockingStub)) {
            assertThat(client.getChannel().authority()).isEqualTo("api.t-0.network:443");
        }
    }

    /** The {@code base_url_parsing} rows of cross_test/test_vectors.json: name, input, valid, error. */
    static Stream<Arguments> baseUrlParsing() throws IOException {
        // Gradle runs tests with CWD = project root (java/sdk/)
        String json = Files.readString(Path.of("../../cross_test/test_vectors.json"));
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("base_url_parsing").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(row -> Arguments.of(row.get("name").getAsString(), row.get("input").getAsString(),
                        row.get("valid").getAsBoolean(), row.get("error").getAsString()));
    }

    @ParameterizedTest(name = "{0}: \"{1}\"")
    @MethodSource("baseUrlParsing")
    @DisplayName("Every base_url_parsing vector is accepted, or refused with its error")
    void baseUrlParsingVectors(String name, String input, boolean valid, String error) {
        if (valid) {
            BlockingNetworkClient.create(input, SIGNER, HealthGrpc::newBlockingStub).close();
        } else {
            assertThatThrownBy(() -> BlockingNetworkClient.create(input, SIGNER, HealthGrpc::newBlockingStub))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage(error);
        }
    }

    @Test
    @DisplayName("A null stub factory or signer is refused before a channel exists")
    void nullArgumentsAreRefused() {
        String endpoint = "http://localhost:1";
        Duration timeout = Duration.ofSeconds(15);
        Duration streamTimeout = Duration.ofMinutes(5);
        assertThatThrownBy(() -> BlockingNetworkClient.create(endpoint, SIGNER, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("stubFactory must not be null");
        assertThatThrownBy(() -> AsyncNetworkClient.create(endpoint, SIGNER, null, timeout, streamTimeout))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("stubFactory must not be null");
        assertThatThrownBy(() -> FutureNetworkClient.create(endpoint, SIGNER, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("stubFactory must not be null");
        assertThatThrownBy(() -> BlockingNetworkClient.create(endpoint, null, HealthGrpc::newBlockingStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("signer must not be null");
    }
}
