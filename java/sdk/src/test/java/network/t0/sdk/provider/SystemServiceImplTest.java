package network.t0.sdk.provider;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the auto-registered {@link SystemServiceImpl}.
 *
 * <p>Mirrors {@code go/provider/system_test.go} one-for-one:
 * <ol>
 *   <li>{@link #implResponseShape_returnsExpectedFields()} — direct unit test of the
 *       package-private impl via {@link StreamObserver} capture.</li>
 *   <li>{@link #autoRegisteredEndToEnd_signedHealthSucceeds()} — full Netty server via
 *       {@link ProviderServer} + signed {@link BlockingNetworkClient} call to
 *       {@code SystemService.Health}; both customer FQN and SystemService FQN must
 *       appear in the response.</li>
 *   <li>{@link #autoRegisteredEndToEnd_unsignedHealthRejected()} — same server, plain
 *       channel without the signing interceptor; must fail with
 *       {@link Status.Code#INVALID_ARGUMENT} (the code returned by
 *       {@link SignatureVerificationInterceptor} for missing headers).</li>
 * </ol>
 *
 * <p>This test lives in the {@code network.t0.sdk.provider} package so it has access to
 * the package-private {@link SystemServiceImpl} constructor — that lets the impl-direct
 * test exercise the real production class instead of a mirror.
 */
class SystemServiceImplTest {

    // Same dev keypair used across the rest of the test suite.
    private static final String NETWORK_PRIVATE_KEY =
            "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_PUBLIC_KEY_HEX =
            "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private ProviderServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    @DisplayName("SystemServiceImpl.health() returns configured services list, Java ecosystem, runtime version, fresh time")
    void implResponseShape_returnsExpectedFields() {
        List<String> services = List.of("example.v1.Foo", SystemServiceGrpc.SERVICE_NAME);
        SystemServiceImpl impl = new SystemServiceImpl(services);

        AtomicReference<HealthResponse> captured = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Instant before = Instant.now();
        impl.health(HealthRequest.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(HealthResponse value) {
                captured.set(value);
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
            }

            @Override
            public void onCompleted() {
                completed.set(true);
            }
        });
        Instant after = Instant.now();

        assertThat(error.get()).isNull();
        assertThat(completed.get()).isTrue();

        HealthResponse response = captured.get();
        assertThat(response).isNotNull();

        // Match Go's `require.Equal(services, resp.Msg.Services)` — exact list equality.
        assertThat(response.getServicesList()).containsExactlyElementsOf(services);
        assertThat(response.getSdkVersion()).isEqualTo(loadExpectedSdkVersion());
        assertThat(response.getSdkEcosystem()).isEqualTo(SdkEcosystem.SDK_ECOSYSTEM_JAVA);
        assertThat(response.hasCurrentTime()).isTrue();

        Instant currentTime = Instant.ofEpochSecond(
                response.getCurrentTime().getSeconds(), response.getCurrentTime().getNanos());
        assertThat(currentTime).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    @DisplayName("Auto-registered SystemService: signed Health() returns customer FQN + system FQN over real Netty server")
    void autoRegisteredEndToEnd_signedHealthSucceeds() throws Exception {
        // Customer's exact pattern: register only the customer service via the public
        // Builder API. SystemService gets auto-appended inside buildGrpcServer().
        server = ProviderServer.create(0, NETWORK_PUBLIC_KEY_HEX)
                .withService(new TestProviderServiceImpl())
                .start();

        Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);
        try (BlockingNetworkClient<SystemServiceGrpc.SystemServiceBlockingStub> client =
                     BlockingNetworkClient.create(
                             "http://localhost:" + server.getPort(),
                             networkSigner,
                             SystemServiceGrpc::newBlockingStub)) {

            HealthResponse response = client.stub().health(HealthRequest.getDefaultInstance());

            assertThat(response.getServicesList())
                    .contains(ProviderServiceGrpc.SERVICE_NAME)
                    .contains(SystemServiceGrpc.SERVICE_NAME);
            assertThat(response.getSdkVersion()).isEqualTo(loadExpectedSdkVersion());
            assertThat(response.getSdkEcosystem()).isEqualTo(SdkEcosystem.SDK_ECOSYSTEM_JAVA);
            assertThat(response.hasCurrentTime()).isTrue();

            long skewSeconds = Math.abs(
                    Instant.now().getEpochSecond() - response.getCurrentTime().getSeconds());
            assertThat(skewSeconds).isLessThan(5);
        }
    }

    @Test
    @DisplayName("Auto-registered SystemService: unsigned Health() rejected with INVALID_ARGUMENT (missing headers)")
    void autoRegisteredEndToEnd_unsignedHealthRejected() throws Exception {
        server = ProviderServer.create(0, NETWORK_PUBLIC_KEY_HEX)
                .withService(new TestProviderServiceImpl())
                .start();

        // Plain channel — no signing interceptor. The server's
        // SignatureVerificationInterceptor must reject the call before it reaches
        // SystemServiceImpl, proving the auto-registered service inherits the
        // signature middleware.
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        try {
            SystemServiceGrpc.SystemServiceBlockingStub plainStub =
                    SystemServiceGrpc.newBlockingStub(channel);

            assertThatThrownBy(() -> plainStub.health(HealthRequest.getDefaultInstance()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, ex ->
                            assertThat(ex.getStatus().getCode())
                                    .isEqualTo(Status.Code.INVALID_ARGUMENT));
        } finally {
            channel.shutdown();
            channel.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * Reads the same {@code META-INF/sdk-version.properties} resource that
     * {@link SystemServiceImpl} reads at runtime, so this test never drifts from
     * whatever the SDK build pins as the runtime version.
     */
    private static String loadExpectedSdkVersion() {
        try (InputStream in = SystemServiceImplTest.class.getResourceAsStream(
                "/META-INF/sdk-version.properties")) {
            assertThat(in).as("META-INF/sdk-version.properties on classpath").isNotNull();
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("sdk.version");
            assertThat(version).as("sdk.version property").isNotNull();
            return version;
        } catch (IOException e) {
            throw new AssertionError("failed to read sdk-version.properties", e);
        }
    }

    /**
     * Minimal customer service stub. All five RPCs return default-instance responses;
     * we only need the binding to register {@code tzero.v1.payment.ProviderService}'s
     * FQN with the server so we can assert it appears in the Health response.
     */
    private static final class TestProviderServiceImpl
            extends ProviderServiceGrpc.ProviderServiceImplBase {

        @Override
        public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> responseObserver) {
            responseObserver.onNext(PayoutResponse.newBuilder()
                    .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void updatePayment(UpdatePaymentRequest request,
                                  StreamObserver<UpdatePaymentResponse> responseObserver) {
            responseObserver.onNext(UpdatePaymentResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void updateLimit(UpdateLimitRequest request,
                                StreamObserver<UpdateLimitResponse> responseObserver) {
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
