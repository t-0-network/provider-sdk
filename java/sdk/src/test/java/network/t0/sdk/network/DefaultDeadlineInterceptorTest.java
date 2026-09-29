package network.t0.sdk.network;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link NetworkClient.DefaultDeadlineInterceptor}: which deadline each call type gets,
 * that a deadline the caller set is kept, the bounds of the timeouts, and that the clients apply them.
 */
class DefaultDeadlineInterceptorTest {

    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "context-deadlines");
        thread.setDaemon(true);
        return thread;
    });
    private static final Metadata.Key<String> CASE = Metadata.Key.of("x-case", Metadata.ASCII_STRING_MARSHALLER);

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
    @EnumSource(value = MethodType.class, names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING"})
    @DisplayName("A deadline the call already has is kept")
    void existingDeadlineIsKept(MethodType type) {
        Deadline own = Deadline.after(2, TimeUnit.HOURS);

        CallOptions options = optionsFor(type, Duration.ofSeconds(15), Duration.ofMinutes(5),
                CallOptions.DEFAULT.withDeadline(own));

        assertThat(options.getDeadline()).isSameAs(own);
    }

    @ParameterizedTest
    @EnumSource(value = MethodType.class, names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING"})
    @DisplayName("A deadline on the caller's Context takes the place of the default")
    void contextDeadlineIsKept(MethodType type) throws Exception {
        Context.CancellableContext context = Context.current().withDeadlineAfter(2, TimeUnit.HOURS, SCHEDULER);
        try {
            CallOptions options = context.call(() -> optionsFor(type, Duration.ofSeconds(15), Duration.ofMinutes(5),
                    CallOptions.DEFAULT));

            assertThat(options.getDeadline()).isNull();
        } finally {
            context.cancel(null);
        }
    }

    @Test
    @DisplayName("The deadline is computed for each call when it is created")
    void deadlineIsComputedPerCall() throws InterruptedException {
        CapturingChannel channel = new CapturingChannel();
        Channel intercepted = ClientInterceptors.intercept(channel,
                new NetworkClient.DefaultDeadlineInterceptor(Duration.ofSeconds(15), Duration.ofMinutes(5)));

        intercepted.newCall(method(MethodType.UNARY), CallOptions.DEFAULT);
        Deadline first = channel.options.getDeadline();
        Thread.sleep(20);
        intercepted.newCall(method(MethodType.UNARY), CallOptions.DEFAULT);
        Deadline second = channel.options.getDeadline();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("A timeout must be a positive duration of at most 2147483647 ms")
    void timeoutsAreValidated() {
        Duration ok = Duration.ofSeconds(1);
        for (Duration bad : new Duration[] {null, Duration.ZERO, Duration.ofMillis(-1),
                Duration.ofMillis(2147483648L), Duration.ofMillis(2147483647L).plusNanos(1)}) {
            assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(bad, ok))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeout must be a positive duration of at most 2147483647 ms");
            assertThatThrownBy(() -> new NetworkClient.DefaultDeadlineInterceptor(ok, bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("streamTimeout must be a positive duration of at most 2147483647 ms");
        }
        Duration max = Duration.ofMillis(2147483647L);
        new NetworkClient.DefaultDeadlineInterceptor(max, max);
        new NetworkClient.DefaultDeadlineInterceptor(Duration.ofNanos(1), Duration.ofNanos(1));
    }

    @Test
    @DisplayName("Every client's create() refuses a bad timeout before it builds a channel")
    void clientsRefuseBadTimeouts() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        String endpoint = "http://localhost:1";
        assertThatThrownBy(() -> BlockingNetworkClient.create(endpoint, signer, HealthGrpc::newBlockingStub,
                Duration.ZERO, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be a positive duration of at most 2147483647 ms");
        assertThatThrownBy(() -> AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub,
                Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("streamTimeout must be a positive duration of at most 2147483647 ms");
        assertThatThrownBy(() -> FutureNetworkClient.create(endpoint, signer, HealthGrpc::newFutureStub,
                Duration.ofSeconds(1), Duration.ofDays(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("streamTimeout must be a positive duration of at most 2147483647 ms");
    }

    @Test
    @DisplayName("A per-call timeout from stub(timeout, unit) has the same bounds")
    void perCallTimeoutsAreValidated() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        String endpoint = "http://localhost:1";
        try (var blocking = BlockingNetworkClient.create(endpoint, signer, HealthGrpc::newBlockingStub);
             var async = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub);
             var future = FutureNetworkClient.create(endpoint, signer, HealthGrpc::newFutureStub)) {
            for (long bad : new long[] {0, -1, 2147483648L, Long.MAX_VALUE}) {
                String message = "timeout must be a positive duration of at most 2147483647 ms";
                assertThatThrownBy(() -> blocking.stub(bad, TimeUnit.MILLISECONDS)).hasMessage(message);
                assertThatThrownBy(() -> async.stub(bad, TimeUnit.MILLISECONDS)).hasMessage(message);
                assertThatThrownBy(() -> future.stub(bad, TimeUnit.MILLISECONDS)).hasMessage(message);
            }
            assertThatThrownBy(() -> blocking.stub(Long.MAX_VALUE, TimeUnit.DAYS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> blocking.stub(1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("unit must not be null");
            assertThat(blocking.stub(2147483647L, TimeUnit.MILLISECONDS).getCallOptions().getDeadline()).isNotNull();
            assertThat(async.stub(1, TimeUnit.NANOSECONDS).getCallOptions().getDeadline()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Through the clients")
    class ThroughTheClients {

        @Test
        @DisplayName("The default create() sends unary calls a 15 s deadline and server streams 5 min")
        void defaultCreateDeadlinesReachTheServer() throws Exception {
            // Milliseconds left on each call's deadline when it reaches the server, by method.
            Map<String, Optional<Long>> remainingMs = new ConcurrentHashMap<>();
            ServerInterceptor recorder = new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                    // The server turns the grpc-timeout header into the deadline of the call's context.
                    remainingMs.put(call.getMethodDescriptor().getBareMethodName(),
                            Optional.ofNullable(Context.current().getDeadline())
                                    .map(deadline -> deadline.timeRemaining(TimeUnit.MILLISECONDS)));
                    return next.startCall(call, headers);
                }
            };
            Server server = NettyServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(new HealthGrpc.HealthImplBase() {
                        @Override
                        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                            observer.onNext(HealthCheckResponse.getDefaultInstance());
                            observer.onCompleted();
                        }

                        @Override
                        public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                            observer.onNext(HealthCheckResponse.getDefaultInstance());
                            observer.onCompleted();
                        }
                    }, recorder))
                    .build()
                    .start();
            try (var client = BlockingNetworkClient.create("http://localhost:" + server.getPort(),
                    Signer.fromHex(PRIVATE_KEY_HEX), HealthGrpc::newBlockingStub)) {

                client.stub().check(HealthCheckRequest.getDefaultInstance());
                client.stub().watch(HealthCheckRequest.getDefaultInstance()).forEachRemaining(response -> { });
            } finally {
                server.shutdownNow();
            }

            assertThat(remainingMs.get("Check")).hasValueSatisfying(ms -> assertThat(ms).isBetween(14_000L, 15_000L));
            assertThat(remainingMs.get("Watch")).hasValueSatisfying(ms -> assertThat(ms).isBetween(299_000L, 300_000L));
        }

        @Test
        @DisplayName("The timeout bounds unary calls")
        void timeoutBoundsUnaryCalls() throws Exception {
            Server server = silentHealthServer();
            try (var client = BlockingNetworkClient.create("http://localhost:" + server.getPort(),
                    Signer.fromHex(PRIVATE_KEY_HEX), HealthGrpc::newBlockingStub, Duration.ofSeconds(1),
                    Duration.ofMinutes(5))) {

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
            try (var shortUnary = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub,
                         Duration.ofMillis(500), Duration.ofSeconds(30));
                 var shortStream = AsyncNetworkClient.create(endpoint, signer, HealthGrpc::newStub,
                         Duration.ofSeconds(30), Duration.ofMillis(500))) {

                CompletableFuture<Status> unbounded = watch(shortUnary.stub());
                CompletableFuture<Status> bounded = watch(shortStream.stub());

                assertThat(bounded.get(10, TimeUnit.SECONDS).getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
                // Past the 500 ms unary timeout, the stream with a 30 s stream timeout is still open.
                Thread.sleep(1_000);
                assertThat(unbounded).isNotDone();

                // End the open stream so the clients close without waiting for it.
                server.shutdownNow();
                assertThat(unbounded.get(10, TimeUnit.SECONDS).getCode()).isNotEqualTo(Status.Code.DEADLINE_EXCEEDED);
            } finally {
                server.shutdownNow();
            }
        }

        @Test
        @DisplayName("A Context deadline replaces the default when it reaches the server, longer or shorter")
        void contextDeadlineReachesTheServer() throws Exception {
            Map<String, Long> remainingMs = new ConcurrentHashMap<>();
            ServerInterceptor recorder = new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                    remainingMs.put(headers.get(CASE), Context.current().getDeadline().timeRemaining(TimeUnit.MILLISECONDS));
                    return next.startCall(call, headers);
                }
            };
            Server server = NettyServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(new HealthGrpc.HealthImplBase() {
                        @Override
                        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                            observer.onNext(HealthCheckResponse.getDefaultInstance());
                            observer.onCompleted();
                        }

                        @Override
                        public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> observer) {
                            observer.onNext(HealthCheckResponse.getDefaultInstance());
                            observer.onCompleted();
                        }
                    }, recorder))
                    .build()
                    .start();
            try (var client = BlockingNetworkClient.create("http://localhost:" + server.getPort(),
                    Signer.fromHex(PRIVATE_KEY_HEX), HealthGrpc::newBlockingStub)) {
                withContextDeadline(Duration.ofSeconds(60), () -> tagged(client.stub(), "unary-longer")
                        .check(HealthCheckRequest.getDefaultInstance()));
                withContextDeadline(Duration.ofSeconds(5), () -> tagged(client.stub(), "unary-shorter")
                        .check(HealthCheckRequest.getDefaultInstance()));
                withContextDeadline(Duration.ofMinutes(10), () -> tagged(client.stub(), "stream-longer")
                        .watch(HealthCheckRequest.getDefaultInstance()).forEachRemaining(response -> { }));
                withContextDeadline(Duration.ofSeconds(30), () -> tagged(client.stub(), "stream-shorter")
                        .watch(HealthCheckRequest.getDefaultInstance()).forEachRemaining(response -> { }));
            } finally {
                server.shutdownNow();
            }

            assertThat(remainingMs.get("unary-longer")).isBetween(55_000L, 60_000L);
            assertThat(remainingMs.get("unary-shorter")).isBetween(1_000L, 5_000L);
            assertThat(remainingMs.get("stream-longer")).isBetween(595_000L, 600_000L);
            assertThat(remainingMs.get("stream-shorter")).isBetween(25_000L, 30_000L);
        }

        private void withContextDeadline(Duration deadline, Runnable call) {
            Context.CancellableContext context =
                    Context.current().withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS, SCHEDULER);
            try {
                context.run(call);
            } finally {
                context.cancel(null);
            }
        }

        private HealthGrpc.HealthBlockingStub tagged(HealthGrpc.HealthBlockingStub stub, String name) {
            Metadata headers = new Metadata();
            headers.put(CASE, name);
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
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

    private static CallOptions optionsFor(MethodType type, Duration timeout, Duration streamTimeout,
                                          CallOptions callOptions) {
        CapturingChannel channel = new CapturingChannel();
        ClientInterceptors.intercept(channel, new NetworkClient.DefaultDeadlineInterceptor(timeout, streamTimeout))
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
