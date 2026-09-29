package network.t0.sdk.network;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

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
            "https://api.t-0.network,        api.t-0.network, 443,  false",
            "http://localhost:8080,          localhost,       8080, true",
            "http://127.0.0.1:1234,          127.0.0.1,       1234, true",
            "HTTPS://api.t-0.network:8443/v1, api.t-0.network, 8443, false",
            "http://[::1]:8080,              [::1],           8080, true",
            "http://localhost,               localhost,       80,   true"})
    @DisplayName("An http or https base URL gives its host, its port or the scheme's, and TLS for https")
    void validBaseUrls(String endpoint, String host, int port, boolean plaintext) {
        assertThat(NetworkClient.parseEndpoint(endpoint)).isEqualTo(new NetworkClient.EndpointInfo(host, port, plaintext));
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
    @ValueSource(strings = {"http://my_host:8080", "api.t-0.network", "api.t-0.network:443", "ftp://h", "http://", "http://:8080",
            "http:foo", "http://h:99999", "http://h:0", "not a url", "https://", "http:///path",
            "https://api t-0.network", " ", "http://h:", "http://user@h"})
    @DisplayName("A base URL without an http or https scheme or without a host is refused, not repaired")
    void invalidBaseUrlIsRefused(String endpoint) {
        assertThatThrownBy(() -> NetworkClient.parseEndpoint(endpoint))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not valid");
        assertThatThrownBy(() -> BlockingNetworkClient.create(endpoint, SIGNER, HealthGrpc::newBlockingStub))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("base URL is not valid");
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

    @Test
    @DisplayName("A stub factory that throws leaves no channel open, in every client")
    void throwingStubFactoryShutsTheChannelDown() {
        IllegalStateException failure = new IllegalStateException("no stub");
        AtomicReference<Channel> built = new AtomicReference<>();
        String endpoint = "http://localhost:1";

        assertThatThrownBy(() -> BlockingNetworkClient.<HealthGrpc.HealthBlockingStub>create(endpoint, SIGNER,
                channel -> { built.set(channel); throw failure; })).isSameAs(failure);
        assertShutDown(built.get());
        assertThatThrownBy(() -> AsyncNetworkClient.<HealthGrpc.HealthStub>create(endpoint, SIGNER,
                channel -> { built.set(channel); throw failure; })).isSameAs(failure);
        assertShutDown(built.get());
        assertThatThrownBy(() -> FutureNetworkClient.<HealthGrpc.HealthFutureStub>create(endpoint, SIGNER,
                channel -> { built.set(channel); throw failure; })).isSameAs(failure);
        assertShutDown(built.get());

        // Also for a checked exception the factory throws without declaring it.
        IOException checked = new IOException("no stub");
        assertThatThrownBy(() -> BlockingNetworkClient.<HealthGrpc.HealthBlockingStub>create(endpoint, SIGNER,
                channel -> { built.set(channel); return NetworkClientFactoryTest.<RuntimeException, HealthGrpc.HealthBlockingStub>sneakyThrow(checked); }))
                .isSameAs(checked);
        assertShutDown(built.get());
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, S> S sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }

    /** A call on a shut-down channel fails at once, naming the shutdown, without trying to connect. */
    private static void assertShutDown(Channel channel) {
        assertThatThrownBy(() -> HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                        assertThat(e.getStatus().getDescription()).contains("shutdown"));
    }

    @Test
    @DisplayName("Any DigestSigner can sign, and its hex getters derive from its public key")
    void anyDigestSignerIsAccepted() {
        DigestSigner custom = new DigestSigner() {
            @Override
            public SignResult sign(byte[] digest) {
                return SIGNER.sign(digest);
            }

            @Override
            public byte[] getPublicKey() {
                return SIGNER.getPublicKey();
            }
        };
        assertThat(custom.getPublicKeyHex()).isEqualTo(SIGNER.getPublicKeyHex());
        assertThat(custom.getPublicKeyHexPrefixed()).isEqualTo(SIGNER.getPublicKeyHexPrefixed());
        try (var client = FutureNetworkClient.create("http://localhost:1", custom, HealthGrpc::newFutureStub)) {
            assertThat(client.stub()).isNotNull();
        }
    }
}
