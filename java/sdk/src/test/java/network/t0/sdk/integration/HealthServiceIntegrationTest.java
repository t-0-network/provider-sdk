package network.t0.sdk.integration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The no-code-change guarantee: a customer who registers only their own services
 * through the public Builder API gets health on the port, behind the same
 * signature middleware. Mirrors {@code go/provider/health_test.go}.
 */
class HealthServiceIntegrationTest {

    private static final String NETWORK_PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private static final Metadata.Key<String> SDK_ECOSYSTEM_HEADER =
            Metadata.Key.of("t0-sdk-ecosystem", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SDK_VERSION_HEADER =
            Metadata.Key.of("t0-sdk-version", Metadata.ASCII_STRING_MARSHALLER);

    private ProviderServer server;

    @BeforeEach
    void setUp() throws Exception {
        // The customer's exact pattern: register a real ProviderService and name
        // nothing else.
        server = ProviderServer.create(0, NETWORK_PUBLIC_KEY_HEX)
                .withService(new TestProviderServiceImpl())
                .start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("Signed Check answers for registered services and refuses the rest")
    void signedCheck_answersForRegisteredServices() throws Exception {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(),
                Signer.fromHex(NETWORK_PRIVATE_KEY),
                HealthGrpc::newBlockingStub)) {

            // The customer's own service, health itself, and the whole-process query.
            for (String service : List.of(ProviderServiceGrpc.SERVICE_NAME, HealthGrpc.SERVICE_NAME, "")) {
                HealthCheckResponse response = client.stub()
                        .check(HealthCheckRequest.newBuilder().setService(service).build());
                assertThat(response.getStatus())
                        .as("service %s", service)
                        .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
            }

            assertThatThrownBy(() -> client.stub().check(HealthCheckRequest.newBuilder()
                    .setService("example.v1.NotRegistered")
                    .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    /**
     * Response headers are the only place the SDK reports what it is: the health
     * contract has a single status field and names its service in the request, so
     * the message itself has no room for this.
     */
    @Test
    @DisplayName("Check response carries the SDK identity headers")
    void checkResponse_carriesSdkIdentityHeaders() throws Exception {
        AtomicReference<Metadata> captured = new AtomicReference<>();

        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(),
                Signer.fromHex(NETWORK_PRIVATE_KEY),
                channel -> HealthGrpc.newBlockingStub(
                        ClientInterceptors.intercept(channel, capturingInterceptor(captured))))) {

            client.stub().check(HealthCheckRequest.getDefaultInstance());
        }

        Metadata headers = captured.get();
        assertThat(headers).isNotNull();
        assertThat(headers.get(SDK_ECOSYSTEM_HEADER)).isEqualTo("java");
        assertThat(headers.get(SDK_VERSION_HEADER)).isEqualTo(loadExpectedSdkVersion());
    }

    /**
     * The probe is signed like every other call the Network makes. Without this the
     * transport would be publishing an unauthenticated endpoint on a partner's port.
     */
    @Test
    @DisplayName("Unsigned Check is rejected by the signature interceptor")
    void unsignedCheck_isRejected() throws Exception {
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        try {
            HealthGrpc.HealthBlockingStub plainStub = HealthGrpc.newBlockingStub(channel);

            assertThatThrownBy(() -> plainStub.check(HealthCheckRequest.getDefaultInstance()))
                    .isInstanceOf(StatusRuntimeException.class);
        } finally {
            channel.shutdown();
            channel.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static ClientInterceptor capturingInterceptor(AtomicReference<Metadata> sink) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        super.start(new ForwardingClientCallListener
                                .SimpleForwardingClientCallListener<>(responseListener) {
                            @Override
                            public void onHeaders(Metadata responseHeaders) {
                                sink.set(responseHeaders);
                                super.onHeaders(responseHeaders);
                            }
                        }, headers);
                    }
                };
            }
        };
    }

    /**
     * Reads the same META-INF resource the SDK reads, so the test does not drift
     * from the SDK's runtime version source.
     */
    private static String loadExpectedSdkVersion() throws IOException {
        try (InputStream in = HealthServiceIntegrationTest.class.getResourceAsStream("/META-INF/sdk-version.properties")) {
            assertThat(in).as("META-INF/sdk-version.properties on classpath").isNotNull();
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("sdk.version");
        }
    }

    /** Registered only so its FQN appears in the registry; never invoked. */
    private static final class TestProviderServiceImpl extends ProviderServiceGrpc.ProviderServiceImplBase {
        @Override
        public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> observer) {
            observer.onNext(PayoutResponse.getDefaultInstance());
            observer.onCompleted();
        }

        @Override
        public void updatePayment(UpdatePaymentRequest request, StreamObserver<UpdatePaymentResponse> observer) {
            observer.onNext(UpdatePaymentResponse.getDefaultInstance());
            observer.onCompleted();
        }

        @Override
        public void updateLimit(UpdateLimitRequest request, StreamObserver<UpdateLimitResponse> observer) {
            observer.onNext(UpdateLimitResponse.getDefaultInstance());
            observer.onCompleted();
        }

        @Override
        public void appendLedgerEntries(AppendLedgerEntriesRequest request,
                                        StreamObserver<AppendLedgerEntriesResponse> observer) {
            observer.onNext(AppendLedgerEntriesResponse.getDefaultInstance());
            observer.onCompleted();
        }

        @Override
        public void approvePaymentQuotes(ApprovePaymentQuoteRequest request,
                                         StreamObserver<ApprovePaymentQuoteResponse> observer) {
            observer.onNext(ApprovePaymentQuoteResponse.getDefaultInstance());
            observer.onCompleted();
        }
    }
}
