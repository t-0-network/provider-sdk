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
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A signer that fails ends the call with Internal "signing the request failed: <cause>", and nothing is sent,
 * with every client the SDK ships. The future client has no streaming methods, so it is checked on unary calls only.
 */
class SigningFailureTest {

    private static final HealthCheckRequest REQUEST = HealthCheckRequest.getDefaultInstance();

    /** How long a test waits for an async or future call to end. */
    private static final long WAIT_SECONDS = 30;

    private static final DigestSigner FAILING_SIGNER = new DigestSigner() {
        @Override
        public SignResult sign(byte[] digest) {
            throw new IllegalStateException("key store offline");
        }

        @Override
        public byte[] getPublicKey() {
            return Signer.fromHex("6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8").getPublicKey();
        }
    };

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
        String endpoint = "http://localhost:" + server.getPort();
        switch (kind) {
            case BLOCKING -> {
                try (var client = BlockingNetworkClient.create(endpoint, FAILING_SIGNER, HealthGrpc::newBlockingStub)) {
                    assertSigningFailed(() -> client.stub().check(REQUEST));
                    // The client stays usable: the next call fails the same way, not with a stale state.
                    assertSigningFailed(() -> client.stub().check(REQUEST));
                }
            }
            case ASYNC -> {
                try (var client = AsyncNetworkClient.create(endpoint, FAILING_SIGNER, HealthGrpc::newStub)) {
                    assertSigningFailed(() -> awaitEnd(observer -> client.stub().check(REQUEST, observer)));
                    assertSigningFailed(() -> awaitEnd(observer -> client.stub().check(REQUEST, observer)));
                }
            }
            case FUTURE -> {
                try (var client = FutureNetworkClient.create(endpoint, FAILING_SIGNER, HealthGrpc::newFutureStub)) {
                    assertSigningFailed(() -> client.stub().check(REQUEST).get(WAIT_SECONDS, TimeUnit.SECONDS));
                    assertSigningFailed(() -> client.stub().check(REQUEST).get(WAIT_SECONDS, TimeUnit.SECONDS));
                }
            }
        }
        assertThat(received).hasValue(0);
    }

    @Test
    @DisplayName("A server-streaming call fails with Internal and is not sent (blocking)")
    void serverStreamingCall() {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newBlockingStub)) {
            assertSigningFailed(() -> client.stub().watch(REQUEST).hasNext());
        }
        assertThat(received).hasValue(0);
    }

    @Test
    @DisplayName("A server-streaming call fails with Internal and is not sent (async)")
    void asyncServerStreamingCall() {
        try (var client = AsyncNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newStub)) {
            assertSigningFailed(() -> awaitEnd(observer -> client.stub().watch(REQUEST, observer)));
        }
        assertThat(received).hasValue(0);
    }

    /** The clients the SDK ships. */
    enum Client { BLOCKING, ASYNC, FUTURE }

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
     * The call fails with Internal "signing the request failed: <cause>". A blocking call throws the
     * StatusRuntimeException; an async or future call ends with it as the cause of the ExecutionException.
     */
    private static void assertSigningFailed(ThrowingCallable call) {
        Throwable thrown = catchThrowable(call);
        Throwable error = thrown instanceof ExecutionException ? thrown.getCause() : thrown;
        assertThat(error)
                .as("how the call ended")
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription())
                            .isEqualTo(String.format(Messages.SIGNING_FAILED, "key store offline"))
                            .isEqualTo("signing the request failed: key store offline");
                    assertThat(e.getStatus().getCause()).isInstanceOf(IllegalStateException.class);
                });
    }
}
