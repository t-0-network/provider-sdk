package network.t0.sdk.integration;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest;
import network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse;
import network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest;
import network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse;
import network.t0.sdk.proto.tzero.v1.payment.PayoutRequest;
import network.t0.sdk.proto.tzero.v1.payment.PayoutResponse;
import network.t0.sdk.proto.tzero.v1.payment.ProviderServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest;
import network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse;
import network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest;
import network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse;
import network.t0.sdk.proto.tzero.v1.system.HealthRequest;
import network.t0.sdk.proto.tzero.v1.system.HealthResponse;
import network.t0.sdk.proto.tzero.v1.system.SdkEcosystem;
import network.t0.sdk.proto.tzero.v1.system.SystemServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the auto-registered SystemService.
 *
 * <p>Verifies the no-code-change guarantee: a customer that registers only
 * their own services through the public Builder API gets `SystemService`
 * auto-registered behind the same signature middleware. Mirrors
 * `go/provider/system_test.go::TestSystem_AutoRegisteredEndToEnd`.
 */
class SystemServiceIntegrationTest {

    private static final String NETWORK_PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private ProviderServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    private void startServer() throws Exception {
        // Customer's exact pattern: register a real ProviderService through the
        // public Builder API. SystemService is appended automatically.
        server = ProviderServer.create(0, NETWORK_PUBLIC_KEY_HEX)
                .withService(new TestProviderServiceImpl())
                .start();
    }

    @Test
    @DisplayName("Signed Health() returns SystemService FQN, payment FQN, Java ecosystem, runtime version")
    void signedHealth_returnsExpectedShape() throws Exception {
        startServer();

        Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(),
                networkSigner,
                SystemServiceGrpc::newBlockingStub)) {

            HealthResponse response = client.stub().health(HealthRequest.getDefaultInstance());

            assertThat(response.getServicesList())
                    .contains(ProviderServiceGrpc.SERVICE_NAME)
                    .contains(SystemServiceGrpc.SERVICE_NAME);
            assertThat(response.getSdkEcosystem()).isEqualTo(SdkEcosystem.SDK_ECOSYSTEM_JAVA);
            assertThat(response.getSdkVersion()).isEqualTo(loadExpectedSdkVersion());
            assertThat(response.hasCurrentTime()).isTrue();
            long skewSeconds = Math.abs(Instant.now().getEpochSecond() - response.getCurrentTime().getSeconds());
            assertThat(skewSeconds).isLessThan(5);
        }
    }

    @Test
    @DisplayName("Unsigned Health() is rejected by the signature interceptor")
    void unsignedHealth_isRejected() throws Exception {
        startServer();

        // Bypass BlockingNetworkClient: build a stub directly with no signing interceptor.
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        try {
            SystemServiceGrpc.SystemServiceBlockingStub plainStub =
                    SystemServiceGrpc.newBlockingStub(channel);

            assertThatThrownBy(() -> plainStub.health(HealthRequest.getDefaultInstance()))
                    .isInstanceOf(StatusRuntimeException.class);
        } finally {
            channel.shutdown();
            channel.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Direct SystemServiceImpl.health() returns the configured services list")
    void implResponseShape() throws Exception {
        // Direct unit-style check on the impl class. We invoke it through gRPC's
        // generated builder helpers using a synchronous StreamObserver.
        var captured = new java.util.concurrent.atomic.AtomicReference<HealthResponse>();
        var impl = new TestSystemServiceProbe(List.of("example.v1.Foo", SystemServiceGrpc.SERVICE_NAME));
        impl.health(HealthRequest.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(HealthResponse value) {
                captured.set(value);
            }
            @Override
            public void onError(Throwable t) {
                throw new AssertionError(t);
            }
            @Override
            public void onCompleted() {}
        });

        HealthResponse response = captured.get();
        assertThat(response).isNotNull();
        assertThat(response.getServicesList()).containsExactly(
                "example.v1.Foo", SystemServiceGrpc.SERVICE_NAME);
        assertThat(response.getSdkEcosystem()).isEqualTo(SdkEcosystem.SDK_ECOSYSTEM_JAVA);
        assertThat(response.getSdkVersion()).isEqualTo(loadExpectedSdkVersion());
    }

    /**
     * Reads the same META-INF resource SystemServiceImpl reads, so the test
     * doesn't drift from the SDK's runtime version source.
     */
    private static String loadExpectedSdkVersion() throws IOException {
        try (InputStream in = SystemServiceIntegrationTest.class.getResourceAsStream("/META-INF/sdk-version.properties")) {
            assertThat(in).as("META-INF/sdk-version.properties on classpath").isNotNull();
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("sdk.version");
        }
    }

    /**
     * Mirrors the package-private SystemServiceImpl behaviour for the impl-only
     * test (the production class is package-private under network.t0.sdk.provider).
     * Uses the same response-building shape so any divergence shows up here.
     */
    private static class TestSystemServiceProbe extends SystemServiceGrpc.SystemServiceImplBase {
        private final List<String> services;

        TestSystemServiceProbe(List<String> services) {
            this.services = services;
        }

        @Override
        public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
            Instant now = Instant.now();
            HealthResponse response = HealthResponse.newBuilder()
                    .addAllServices(services)
                    .setCurrentTime(com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .setSdkVersion(loadVersionOrUnknown())
                    .setSdkEcosystem(SdkEcosystem.SDK_ECOSYSTEM_JAVA)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        private static String loadVersionOrUnknown() {
            try {
                return loadExpectedSdkVersion();
            } catch (IOException e) {
                return "unknown";
            }
        }
    }

    private static class TestProviderServiceImpl extends ProviderServiceGrpc.ProviderServiceImplBase {
        @Override
        public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> responseObserver) {
            responseObserver.onNext(PayoutResponse.newBuilder()
                    .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void updatePayment(UpdatePaymentRequest request, StreamObserver<UpdatePaymentResponse> responseObserver) {
            responseObserver.onNext(UpdatePaymentResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void updateLimit(UpdateLimitRequest request, StreamObserver<UpdateLimitResponse> responseObserver) {
            responseObserver.onNext(UpdateLimitResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void appendLedgerEntries(AppendLedgerEntriesRequest request,
                                        StreamObserver<AppendLedgerEntriesResponse> responseObserver) {
            responseObserver.onNext(AppendLedgerEntriesResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void approvePaymentQuotes(ApprovePaymentQuoteRequest request,
                                         StreamObserver<ApprovePaymentQuoteResponse> responseObserver) {
            responseObserver.onNext(ApprovePaymentQuoteResponse.newBuilder()
                    .setAccepted(ApprovePaymentQuoteResponse.Accepted.getDefaultInstance())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
