package network.t0.sdk.integration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.provider.Validate;
import network.t0.sdk.proto.tzero.v1.payment.PayoutRequest;
import network.t0.sdk.proto.tzero.v1.payment.PayoutResponse;
import network.t0.sdk.proto.tzero.v1.payment.ProviderServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every response a {@link ProviderServer} sends is validated: an invalid one reaches the caller as
 * INTERNAL "response validation failed: <field path>: <message>[; …]", with one ERROR log line.
 */
class ResponseValidationIntegrationTest {

    private static final String NETWORK_PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private final AtomicReference<PayoutResponse> nextResponse = new AtomicReference<>();
    private volatile boolean checkInHandler;
    private final AtomicReference<RuntimeException> handlerFailure = new AtomicReference<>();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ResponseValidationIntegrationTest");
    private ProviderServer server;

    @BeforeEach
    void setUp() throws Exception {
        appender.start();
        logger.addAppender(appender);
        server = ProviderServer.create(0, NETWORK_PUBLIC_KEY_HEX)
                .withService(new ProviderServiceGrpc.ProviderServiceImplBase() {
                    @Override
                    public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> observer) {
                        // The handler goes on as usual after a refused response.
                        if (checkInHandler) {
                            // Thrown out of the handler: the SDK maps it to the same reply.
                            observer.onNext(Validate.check(nextResponse.get()));
                            observer.onCompleted();
                            return;
                        }
                        try {
                            observer.onNext(nextResponse.get());
                            observer.onCompleted();
                        } catch (RuntimeException e) {
                            handlerFailure.set(e);
                        }
                    }
                })
                .withLogger(logger)
                .withSdkVersion("9.9.9-test")
                .start();
    }

    @AfterEach
    void tearDown() {
        server.close();
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("An invalid nested field is refused with INTERNAL and its full path")
    void invalidNestedField_isRefused() throws Exception {
        nextResponse.set(PayoutResponse.newBuilder()
                .setFailed(PayoutResponse.Failed.newBuilder().setDetails("x".repeat(1025)))
                .build());

        assertRefused("failed.details: must be at most 1024 characters");
    }

    @Test
    @DisplayName("A missing required oneof is refused with INTERNAL")
    void missingOneof_isRefused() throws Exception {
        nextResponse.set(PayoutResponse.getDefaultInstance());

        assertRefused("result: exactly one field is required in oneof");
    }

    @Test
    @DisplayName("Validate.check in the handler gives the same reply and log line")
    void validateCheckInHandler_givesSameReply() throws Exception {
        checkInHandler = true;
        nextResponse.set(PayoutResponse.newBuilder()
                .setFailed(PayoutResponse.Failed.newBuilder().setDetails("x".repeat(1025)))
                .build());

        assertRefused("failed.details: must be at most 1024 characters");
    }

    @Test
    @DisplayName("A valid response is sent")
    void validResponse_isSent() throws Exception {
        PayoutResponse valid = PayoutResponse.newBuilder()
                .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                .build();
        nextResponse.set(valid);

        try (var client = providerClient()) {
            assertThat(client.stub().payOut(PayoutRequest.getDefaultInstance())).isEqualTo(valid);
        }
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("Health responses pass the same validation")
    void healthResponse_isSent() throws Exception {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(),
                Signer.fromHex(NETWORK_PRIVATE_KEY),
                HealthGrpc::newBlockingStub)) {
            assertThat(client.stub().check(HealthCheckRequest.getDefaultInstance()).getStatus())
                    .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        }
        assertThat(appender.list).isEmpty();
    }

    private void assertRefused(String violations) throws Exception {
        try (var client = providerClient()) {
            assertThatThrownBy(() -> client.stub().payOut(PayoutRequest.getDefaultInstance()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                        assertThat(e.getStatus().getDescription())
                                .isEqualTo("response validation failed: " + violations);
                    });
        }
        assertThat(handlerFailure.get()).isNull();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getMessage()).isEqualTo("response validation failed");
        Map<String, String> kv = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(p -> p.key, p -> String.valueOf(p.value)));
        assertThat(kv).containsEntry("rpc_method", "tzero.v1.payment.ProviderService/PayOut")
                .containsEntry("response_type", "tzero.v1.payment.PayoutResponse")
                .containsEntry("violations", violations)
                .containsEntry("sdk_version", "9.9.9-test");
    }

    private BlockingNetworkClient<ProviderServiceGrpc.ProviderServiceBlockingStub> providerClient() {
        return BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(),
                Signer.fromHex(NETWORK_PRIVATE_KEY),
                ProviderServiceGrpc::newBlockingStub);
    }
}
