package network.t0.sdk.network;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.common.Messages;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A signer that fails, or returns output that fails the check, ends the call with Internal "signing the request
 * failed: <cause>", and nothing is sent, with every client the SDK ships. The future client has no streaming
 * methods, so it is checked on unary calls only.
 */
class SigningFailureTest {

    private static final HealthCheckRequest REQUEST = HealthCheckRequest.getDefaultInstance();

    /** How long a test waits for an async or future call to end. */
    private static final long WAIT_SECONDS = 30;

    private static final String FAILED = "signing the request failed: key store offline";

    private static final Signer SIGNER = Signer.fromHex("6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8");

    private static final DigestSigner FAILING_SIGNER = new DigestSigner() {
        @Override
        public SignResult sign(byte[] digest) {
            throw new IllegalStateException("key store offline");
        }

        @Override
        public byte[] getPublicKey() {
            return SIGNER.getPublicKey();
        }
    };

    /**
     * A custom signer whose output fails the check, and how the call ends. As in every SDK, a signer that returns
     * nothing fails the signature check; SignResult refuses a null signature or key itself, inside the signer.
     */
    enum BadOutput {
        NULL_RESULT(digest -> null,
                Messages.SIGNER_SIGNATURE_INVALID, "signing the request failed: signature must be 64 or 65 bytes"),
        NULL_SIGNATURE(digest -> new SignResult(null, SIGNER.sign(digest).getPublicKey()),
                Messages.SIGNER_SIGNATURE_INVALID, "signing the request failed: signature must be 64 or 65 bytes"),
        NULL_PUBLIC_KEY(digest -> new SignResult(SIGNER.sign(digest).getSignature(), null),
                Messages.SIGNER_PUBLIC_KEY_INVALID,
                "signing the request failed: public key must be 65 bytes, uncompressed");

        final DigestSigner signer;
        final String cause;
        final String description;

        BadOutput(DigestSigner signer, String cause, String description) {
            this.signer = signer;
            this.cause = cause;
            this.description = description;
        }
    }

    private final AtomicInteger received = new AtomicInteger();
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0)
                .addService(new HealthGrpc.HealthImplBase() {
                    @Override
                    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                        received.incrementAndGet();
                        observer.onNext(HealthCheckResponse.getDefaultInstance());
                        observer.onCompleted();
                    }

                    @Override
                    public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                        received.incrementAndGet();
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
    }

    @AfterEach
    void tearDown() {
        server.shutdownNow();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Client.class)
    @DisplayName("A unary call fails with Internal and is not sent")
    void unaryCall(Client kind) {
        assertThat(FAILED).isEqualTo(String.format(Messages.SIGNING_FAILED, "key store offline"));
        assertUnaryCallsFail(kind, FAILING_SIGNER, FAILED, IllegalStateException.class);
        assertThat(received).hasValue(0);
    }

    @Test
    @DisplayName("A server-streaming call fails with Internal and is not sent (blocking)")
    void serverStreamingCall() {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newBlockingStub)) {
            assertSigningFailed(() -> client.stub().watch(REQUEST).hasNext(), FAILED, IllegalStateException.class);
        }
        assertThat(received).hasValue(0);
    }

    @Test
    @DisplayName("A server-streaming call fails with Internal and is not sent (async)")
    void asyncServerStreamingCall() {
        try (var client = AsyncNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newStub)) {
            assertSigningFailed(() -> awaitEnd(observer -> client.stub().watch(REQUEST, observer)),
                    FAILED, IllegalStateException.class);
        }
        assertThat(received).hasValue(0);
    }

    static Stream<Arguments> clientsAndBadOutputs() {
        return Arrays.stream(Client.values())
                .flatMap(kind -> Arrays.stream(BadOutput.values()).map(output -> Arguments.of(kind, output)));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("clientsAndBadOutputs")
    @DisplayName("A signer output that fails the check fails a unary call with Internal, and it is not sent")
    void unaryCallWithBadOutput(Client kind, BadOutput output) {
        assertThat(output.description).isEqualTo(String.format(Messages.SIGNING_FAILED, output.cause));
        assertUnaryCallsFail(kind, output.signer, output.description, IllegalArgumentException.class);
        assertThat(received).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BadOutput.class)
    @DisplayName("A signer output that fails the check fails a server-streaming call with Internal, and it is not sent")
    void serverStreamingCallWithBadOutput(BadOutput output) {
        String endpoint = "http://localhost:" + server.getPort();
        try (var client = BlockingNetworkClient.create(endpoint, output.signer, HealthGrpc::newBlockingStub)) {
            assertSigningFailed(() -> client.stub().watch(REQUEST).hasNext(),
                    output.description, IllegalArgumentException.class);
        }
        try (var client = AsyncNetworkClient.create(endpoint, output.signer, HealthGrpc::newStub)) {
            assertSigningFailed(() -> awaitEnd(observer -> client.stub().watch(REQUEST, observer)),
                    output.description, IllegalArgumentException.class);
        }
        assertThat(received).hasValue(0);
    }

    /** The clients the SDK ships. */
    enum Client { BLOCKING, ASYNC, FUTURE }

    /** Makes two unary calls with a client of this kind, each failing with Internal {@code description}. */
    private void assertUnaryCallsFail(Client kind, DigestSigner signer, String description,
                                      Class<? extends Throwable> causeType) {
        String endpoint = "http://localhost:" + server.getPort();
        switch (kind) {
            case BLOCKING -> {
                try (var client = BlockingNetworkClient.create(endpoint, signer, HealthGrpc::newBlockingStub)) {
                    assertSigningFailed(() -> client.stub().check(REQUEST), description, causeType);
                    // The client stays usable: the next call fails the same way, not with a stale state.
                    assertSigningFailed(() -> client.stub().check(REQUEST), description, causeType);
                }
            }
            case ASYNC -> {
                try (var client = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub)) {
                    assertSigningFailed(() -> awaitEnd(observer -> client.stub().check(REQUEST, observer)),
                            description, causeType);
                    assertSigningFailed(() -> awaitEnd(observer -> client.stub().check(REQUEST, observer)),
                            description, causeType);
                }
            }
            case FUTURE -> {
                try (var client = FutureNetworkClient.create(endpoint, signer, HealthGrpc::newFutureStub)) {
                    assertSigningFailed(() -> client.stub().check(REQUEST).get(WAIT_SECONDS, TimeUnit.SECONDS),
                            description, causeType);
                    assertSigningFailed(() -> client.stub().check(REQUEST).get(WAIT_SECONDS, TimeUnit.SECONDS),
                            description, causeType);
                }
            }
        }
    }

    /** Starts an async call on the observer it is given. */
    private interface AsyncCall {
        void start(StreamObserver<HealthCheckResponse> observer);
    }

    /** Makes an async call and waits for its end; an error ends it with an ExecutionException. */
    private static void awaitEnd(AsyncCall call) throws Exception {
        CompletableFuture<Void> end = new CompletableFuture<>();
        call.start(new StreamObserver<>() {
            @Override
            public void onNext(HealthCheckResponse response) {
            }

            @Override
            public void onError(Throwable t) {
                end.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                end.complete(null);
            }
        });
        end.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * The call fails with Internal {@code description}, "signing the request failed: <cause>", caused by a
     * {@code causeType}. A blocking call throws the StatusRuntimeException; an async or future call ends with it
     * as the cause of the ExecutionException.
     */
    private static void assertSigningFailed(ThrowingCallable call, String description,
                                            Class<? extends Throwable> causeType) {
        Throwable thrown = catchThrowable(call);
        Throwable error = thrown instanceof ExecutionException ? thrown.getCause() : thrown;
        assertThat(error)
                .as("how the call ended")
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription()).isEqualTo(description);
                    assertThat(e.getStatus().getCause()).isInstanceOf(causeType);
                });
    }
}
