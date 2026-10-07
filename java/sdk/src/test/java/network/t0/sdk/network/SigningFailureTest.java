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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A signer that fails ends the call with Internal "signing the request failed: <cause>", and nothing is sent. */
class SigningFailureTest {

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

    @Test
    @DisplayName("A unary call fails with Internal and is not sent")
    void unaryCall() {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newBlockingStub)) {
            assertSigningFailed(() -> client.stub().check(HealthCheckRequest.getDefaultInstance()));
            // The client stays usable: the next call fails the same way, not with a stale state.
            assertSigningFailed(() -> client.stub().check(HealthCheckRequest.getDefaultInstance()));
        }
        assertThat(received).hasValue(0);
    }

    @Test
    @DisplayName("A server-streaming call fails with Internal and is not sent")
    void serverStreamingCall() {
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(), FAILING_SIGNER, HealthGrpc::newBlockingStub)) {
            assertSigningFailed(() -> client.stub().watch(HealthCheckRequest.getDefaultInstance()).hasNext());
        }
        assertThat(received).hasValue(0);
    }

    private static void assertSigningFailed(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription())
                            .isEqualTo(String.format(Messages.SIGNING_FAILED, "key store offline"))
                            .isEqualTo("signing the request failed: key store offline");
                    assertThat(e.getStatus().getCause()).isInstanceOf(IllegalStateException.class);
                });
    }
}
