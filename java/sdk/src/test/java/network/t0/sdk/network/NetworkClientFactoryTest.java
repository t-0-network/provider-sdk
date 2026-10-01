package network.t0.sdk.network;

import io.grpc.health.v1.HealthGrpc;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

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

    @ParameterizedTest
    @CsvSource({
            "https://api.t-0.network,                 api.t-0.network, 443,  false, ''",
            "https://api.t-0.network/,                api.t-0.network, 443,  false, ''",
            "http://localhost:8080,                   localhost,       8080, true,  ''",
            "http://127.0.0.1:1234,                   127.0.0.1,       1234, true,  ''",
            "http://[::1]:8080,                       [::1],           8080, true,  ''",
            "api.t-0.network,                         api.t-0.network, 443,  false, ''",
            "api.t-0.network:443,                     api.t-0.network, 443,  false, ''",
            "https://api.t-0.network/v1,              api.t-0.network, 443,  false, v1",
            "https://api.t-0.network/v1/,             api.t-0.network, 443,  false, v1",
            "https://api.t-0.network/sda/payments/t0, api.t-0.network, 443,  false, sda/payments/t0",
            "HTTPS://api.t-0.network,                 api.t-0.network, 443,  false, ''",
            "http://[::1],                            [::1],           80,   true,  ''",
            "http://[::ffff:1.2.3.4]:8080,            [::ffff:1.2.3.4], 8080, true, ''",
            "https://xn--bcher-kva.example,           xn--bcher-kva.example, 443, false, ''"})
    @DisplayName("A valid base URL gives its host, its port or the scheme's, TLS for https, and its path")
    void validBaseUrls(String endpoint, String host, int port, boolean plaintext, String pathPrefix) {
        var endpointInfo = new NetworkClient.EndpointInfo(host, port, plaintext);
        assertThat(NetworkClient.parseEndpoint(endpoint)).isEqualTo(endpointInfo);
        assertThat(NetworkClient.parseBaseUrl(endpoint)).isEqualTo(new NetworkClient.BaseUrl(endpointInfo, pathPrefix));
        try (var client = BlockingNetworkClient.create(endpoint, SIGNER, HealthGrpc::newBlockingStub)) {
            assertThat(client.getChannel().authority()).endsWith(":" + port);
        }
    }

    @Test
    @DisplayName("An empty base URL is refused with \"base URL is not set\" by every client")
    void emptyBaseUrlIsRefused() {
        assertThatThrownBy(() -> BlockingNetworkClient.create("", SIGNER, HealthGrpc::newBlockingStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not set");
        assertThatThrownBy(() -> AsyncNetworkClient.create("", SIGNER, HealthGrpc::newStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not set");
        assertThatThrownBy(() -> FutureNetworkClient.create("", SIGNER, HealthGrpc::newFutureStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not set");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://h", "http://", "http://user@h", "http://my_host:8080",
            "https://api.t-0.network?x", "http://h:0", "http://h:99999", "http://1.2.3", "http://h:080", "http://h\t",
            "https://api.t-0.network//", "https://api.t-0.network/v1//", "https://api.t-0.network/a//b",
            "https://api.t-0.network/v1/..", "https://api.t-0.network/v%31", "https://api.t-0.network/v1?x",
            "http://[::1%1]", "http://[v1.fe]", "http://h.", "http://01.2.3.4",
            "http://1abc"})
    @DisplayName("A base URL that is not valid is refused with \"base URL is not valid\"")
    void invalidBaseUrlIsRefused(String endpoint) {
        assertThatThrownBy(() -> NetworkClient.parseEndpoint(endpoint))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not valid");
        assertThatThrownBy(() -> BlockingNetworkClient.create(endpoint, SIGNER, HealthGrpc::newBlockingStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not valid");
    }

    @Test
    @DisplayName("A path of many segments is checked without recursion")
    void longPathIsAccepted() {
        String path = "/a".repeat(100_000);
        assertThat(NetworkClient.parseBaseUrl("https://api.t-0.network" + path).pathPrefix())
                .isEqualTo(path.substring(1));
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
