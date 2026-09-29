package network.t0.sdk.network;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link NetworkClient.DefaultDeadlineInterceptor}: which deadline each call type gets,
 * that a deadline already set is kept, and that the clients' {@code create} overloads apply it.
 */
class DefaultDeadlineInterceptorTest {

    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    @Test
    @DisplayName("Unary calls get the unary deadline")
    void unaryGetsUnaryDeadline() {
        CallOptions options = optionsFor(MethodType.UNARY, Duration.ofSeconds(15), Duration.ofMinutes(5), CallOptions.DEFAULT);

        assertThat(options.getDeadline()).isNotNull();
        assertThat(options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)).isBetween(14_000L, 15_000L);
    }

    @ParameterizedTest
    @EnumSource(value = MethodType.class, names = {"CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING", "UNKNOWN"})
    @DisplayName("Streaming calls get the stream deadline when one is set")
    void streamingGetsStreamDeadline(MethodType type) {
        CallOptions options = optionsFor(type, Duration.ofSeconds(15), Duration.ofMinutes(5), CallOptions.DEFAULT);

        assertThat(options.getDeadline()).isNotNull();
        assertThat(options.getDeadline().timeRemaining(TimeUnit.SECONDS)).isBetween(299L, 300L);
    }

    @ParameterizedTest
    @EnumSource(value = MethodType.class, names = {"CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING"})
    @DisplayName("Streaming calls get no deadline when no stream timeout is set")
    void streamingGetsNoDeadlineByDefault(MethodType type) {
        assertThat(optionsFor(type, Duration.ofSeconds(15), null, CallOptions.DEFAULT).getDeadline()).isNull();
        assertThat(optionsFor(type, Duration.ofSeconds(15), Duration.ZERO, CallOptions.DEFAULT).getDeadline()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MethodType.class, names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING"})
    @DisplayName("A deadline the call already has is kept")
    void existingDeadlineIsKept(MethodType type) {
        Deadline own = Deadline.after(2, TimeUnit.HOURS);

        CallOptions options = optionsFor(type, Duration.ofSeconds(15), Duration.ofMinutes(5),
                CallOptions.DEFAULT.withDeadline(own));

        assertThat(options.getDeadline()).isSameAs(own);
    }

    @Test
    @DisplayName("The deadline is computed for each call when it is created")
    void deadlineIsComputedPerCall() throws InterruptedException {
        CapturingChannel channel = new CapturingChannel();
        Channel intercepted = ClientInterceptors.intercept(channel,
                new NetworkClient.DefaultDeadlineInterceptor(Duration.ofSeconds(15), null));

        intercepted.newCall(method(MethodType.UNARY), CallOptions.DEFAULT);
        Deadline first = channel.options.getDeadline();
        Thread.sleep(20);
        intercepted.newCall(method(MethodType.UNARY), CallOptions.DEFAULT);
        Deadline second = channel.options.getDeadline();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("Timeouts are validated: unary must be positive, stream must not be negative")
    void timeoutsAreValidated() {
        assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(Duration.ofSeconds(-1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(Duration.ofSeconds(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamTimeout");

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        assertThatThrownBy(() -> BlockingNetworkClient.create("http://localhost:1", signer, HealthGrpc::newBlockingStub, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AsyncNetworkClient.create("http://localhost:1", signer, HealthGrpc::newStub,
                Duration.ofSeconds(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FutureNetworkClient.create("http://localhost:1", signer, HealthGrpc::newFutureStub,
                Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The clients apply the deadlines to real calls; the server never answers. */
    @Nested
    @DisplayName("Through the clients")
    class ThroughTheClients {

        @Test
        @DisplayName("create(..., timeoutSeconds) bounds unary calls")
        void timeoutSecondsBoundsUnaryCalls() throws Exception {
            Server server = silentHealthServer();
            try (var client = BlockingNetworkClient.create("http://localhost:" + server.getPort(),
                    Signer.fromHex(PRIVATE_KEY_HEX), HealthGrpc::newBlockingStub, 1)) {

                long start = System.nanoTime();
                assertThatThrownBy(() -> client.stub().check(HealthCheckRequest.getDefaultInstance()))
                        .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
            } finally {
                server.shutdownNow();
            }
        }

        @Test
        @DisplayName("The stream timeout, not the unary one, bounds server streams")
        void streamTimeoutBoundsStreams() throws Exception {
            Server server = silentHealthServer();
            String endpoint = "http://localhost:" + server.getPort();
            Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
            try (var unaryOnly = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub,
                         Duration.ofMillis(500), null);
                 var withStreamTimeout = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub,
                         Duration.ofSeconds(30), Duration.ofMillis(500))) {

                CompletableFuture<Status> unbounded = watch(unaryOnly.stub());
                CompletableFuture<Status> bounded = watch(withStreamTimeout.stub());

                assertThat(bounded.get(10, TimeUnit.SECONDS).getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
                // Well past the 500 ms unary timeout, the stream without a stream timeout is still open.
                Thread.sleep(1_000);
                assertThat(unbounded).isNotDone();

                // End the open stream so the clients close without waiting for it.
                server.shutdownNow();
                assertThat(unbounded.get(10, TimeUnit.SECONDS).getCode()).isNotEqualTo(Status.Code.DEADLINE_EXCEEDED);
            } finally {
                server.shutdownNow();
            }
        }

        private CompletableFuture<Status> watch(HealthGrpc.HealthStub stub) {
            CompletableFuture<Status> closed = new CompletableFuture<>();
            stub.watch(HealthCheckRequest.getDefaultInstance(), new StreamObserver<>() {
                @Override
                public void onNext(HealthCheckResponse value) {
                }

                @Override
                public void onError(Throwable t) {
                    closed.complete(Status.fromThrowable(t));
                }

                @Override
                public void onCompleted() {
                    closed.complete(Status.OK);
                }
            });
            return closed;
        }

        /** A health server that accepts every call and never answers. */
        private Server silentHealthServer() throws Exception {
            return NettyServerBuilder.forPort(0)
                    .addService(new HealthGrpc.HealthImplBase() {
                        @Override
                        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                        }

                        @Override
                        public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                        }
                    })
                    .build()
                    .start();
        }
    }

    // ==================== Helpers ====================

    private static CallOptions optionsFor(MethodType type, Duration unaryTimeout, Duration streamTimeout,
                                          CallOptions callOptions) {
        CapturingChannel channel = new CapturingChannel();
        ClientInterceptors.intercept(channel, new NetworkClient.DefaultDeadlineInterceptor(unaryTimeout, streamTimeout))
                .newCall(method(type), callOptions);
        return channel.options;
    }

    private static MethodDescriptor<StringValue, StringValue> method(MethodType type) {
        return MethodDescriptor.<StringValue, StringValue>newBuilder()
                .setType(type)
                .setFullMethodName("test.v1.StreamTest/" + type)
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .build();
    }

    /** Keeps the call options of the last call; the calls themselves do nothing. */
    private static final class CapturingChannel extends Channel {
        CallOptions options;

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
            this.options = callOptions;
            return new ClientCall<>() {
                @Override public void start(Listener<RespT> responseListener, Metadata headers) {}
                @Override public void request(int numMessages) {}
                @Override public void cancel(String message, Throwable cause) {}
                @Override public void halfClose() {}
                @Override public void sendMessage(ReqT message) {}
            };
        }

        @Override
        public String authority() {
            return "fake";
        }
    }
}
