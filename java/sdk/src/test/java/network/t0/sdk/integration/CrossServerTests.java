package network.t0.sdk.integration;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.network.SigningInterceptors;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrossServerTests {

    private static final String PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private static final String GO_HELPER = findGoHelper();

    private static String findGoHelper() {
        File dir = new File(System.getProperty("user.dir"));
        // Gradle sets user.dir to the module directory (java/sdk)
        File repoRoot = dir.getParentFile().getParentFile();
        File helper = new File(repoRoot, "cross_test/go_helper/go_helper");
        if (!helper.exists()) {
            helper = new File(dir.getParentFile(), "cross_test/go_helper/go_helper");
        }
        if (!helper.exists()) {
            helper = new File(dir, "../../cross_test/go_helper/go_helper");
        }
        return helper.exists() ? helper.getAbsolutePath() : null;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitForPort(int port, int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var sock = new java.net.Socket()) {
                sock.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Port " + port + " not ready after " + timeoutMs + "ms");
    }

    private void skipOrFailIfNoHelper() {
        if (GO_HELPER == null) {
            if (System.getenv("CI") != null) {
                throw new AssertionError("Go helper binary required in CI but not found");
            }
            assumeTrue(false, "Go helper not found — skipping cross-language test");
        }
    }

    @Test
    void javaClient_goServer_healthCheck() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try {
            try (var client = BlockingNetworkClient.create(
                    "http://localhost:" + goServer.port(),
                    Signer.fromHex(PRIVATE_KEY),
                    HealthGrpc::newBlockingStub)) {

                HealthCheckResponse response = client.stub()
                        .check(HealthCheckRequest.newBuilder().setService(HealthGrpc.SERVICE_NAME).build());

                assertThat(response.getStatus())
                        .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
            }
        } finally {
            stop(goServer);
        }
    }

    @Test
    void javaClient_goServer_payOut() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try {
            try (var client = BlockingNetworkClient.create(
                    "http://localhost:" + goServer.port(),
                    Signer.fromHex(PRIVATE_KEY),
                    ProviderServiceGrpc::newBlockingStub)) {

                // Send a PayOut missing required fields — Go rejects with
                // INVALID_ARGUMENT (proto validation). Getting INVALID_ARGUMENT
                // (not UNAUTHENTICATED) proves dual-framing signature verification
                // works for non-empty bodies.
                io.grpc.StatusRuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                        io.grpc.StatusRuntimeException.class,
                        () -> client.stub().payOut(PayoutRequest.newBuilder()
                                .setPaymentId(42)
                                .setPayoutId(1)
                                .setCurrency("EUR")
                                .setClientQuoteId("test-quote-1")
                                .setPayInProviderId(1)
                                .build()));
                assertThat(thrown.getStatus().getCode())
                        .as("Signature passed (not UNAUTHENTICATED); validation rejected (INVALID_ARGUMENT)")
                        .isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
            }
        } finally {
            stop(goServer);
        }
    }

    @Test
    void goClient_javaServer_healthCheck() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();

        try (ProviderServer server = ProviderServer.create(port, PUBLIC_KEY)
                .withService(new MinimalProviderService())
                .start()) {

            waitForPort(port, 10_000);

            Process goClient = new ProcessBuilder(
                    GO_HELPER,
                    "call-health",
                    "http://127.0.0.1:" + port,
                    "0x" + PRIVATE_KEY,
                    "--grpc")
                    .redirectErrorStream(false)
                    .start();

            String stdout = new String(goClient.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(goClient.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean exited = goClient.waitFor(15, TimeUnit.SECONDS);

            assertThat(exited).isTrue();
            assertThat(goClient.exitValue())
                    .as("Go health check failed: stdout=%s, stderr=%s", stdout, stderr)
                    .isEqualTo(0);
            assertThat(stdout.toLowerCase()).contains("status=serving");
        }
    }

    @Test
    void goClient_javaServer_payOut() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();

        try (ProviderServer server = ProviderServer.create(port, PUBLIC_KEY)
                .withService(new MinimalProviderService())
                .start()) {

            waitForPort(port, 10_000);

            Process goClient = new ProcessBuilder(
                    GO_HELPER,
                    "call-pay-out",
                    "http://127.0.0.1:" + port,
                    "0x" + PRIVATE_KEY,
                    "--grpc")
                    .redirectErrorStream(false)
                    .start();

            String stdout = new String(goClient.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(goClient.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean exited = goClient.waitFor(15, TimeUnit.SECONDS);

            assertThat(exited).isTrue();
            assertThat(goClient.exitValue())
                    .as("Go PayOut failed: stdout=%s, stderr=%s", stdout, stderr)
                    .isEqualTo(0);
            assertThat(stdout).contains("OK");
        }
    }

    // --- Streaming: Java client -> Go server over gRPC (h2c), see docs/STREAMING.md ---
    // The client sees only UNAUTHENTICATED, whatever the reason: the tests read the helper's log.

    private static final MethodDescriptor<StringValue, StringValue> CLIENT_STREAM =
            streamTestMethod(MethodType.CLIENT_STREAMING, "ClientStream");
    private static final MethodDescriptor<StringValue, StringValue> SERVER_STREAM =
            streamTestMethod(MethodType.SERVER_STREAMING, "ServerStream");

    private static final String CLIENT_STREAM_VERIFIED =
            "/test.v1.StreamTest/ClientStream verified over the first payload";
    private static final String SERVER_STREAM_VERIFIED =
            "/test.v1.StreamTest/ServerStream verified over the first payload";
    private static final String CLIENT_STREAM_REJECTED = "/test.v1.StreamTest/ClientStream rejected: ";

    /** A client that buffered the stream to sign it would time out waiting for the log line. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_firstMessageIsNotBuffered() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            CompletableFuture<String> result = new CompletableFuture<>();
            StreamObserver<StringValue> requests = ClientCalls.asyncClientStreamingCall(
                    client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));
            requests.onNext(StringValue.of("m1"));
            goServer.waitForLog(CLIENT_STREAM_VERIFIED, 0, Duration.ofSeconds(10));

            requests.onNext(StringValue.of("m2"));
            requests.onNext(StringValue.of("m3"));
            requests.onCompleted();

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("m1,m2,m3");
        } finally {
            stop(goServer);
        }
    }

    /** Larger than HTTP/2's initial 64 KiB window, and random so compression cannot shrink it. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_largeFirstMessage() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        byte[] random = new byte[192 * 1024];
        new SecureRandom().nextBytes(random);
        String large = Base64.getEncoder().encodeToString(random); // 256 KiB
        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            CompletableFuture<String> result = sendClientStream(client.getChannel(), List.of(large, "tail"));

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(large + ",tail");
            goServer.waitForLog(CLIENT_STREAM_VERIFIED);
        } finally {
            stop(goServer);
        }
    }

    /** BlockingClientCall.write waits for isReady() before each message, the first one included. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_blockingWrite() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            BlockingClientCall<StringValue, StringValue> call =
                    ClientCalls.blockingClientStreamingCall(client.getChannel(), CLIENT_STREAM, CallOptions.DEFAULT);
            for (String value : List.of("m1", "m2", "m3")) {
                assertThat(call.write(StringValue.of(value), 10, TimeUnit.SECONDS)).isTrue();
            }
            call.halfClose();

            assertThat(call.read(10, TimeUnit.SECONDS).getValue()).isEqualTo("m1,m2,m3");
        } finally {
            stop(goServer);
        }
    }

    /** A sender that sends only on onReady, the first message included (grpc-java's ClientCall example). */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_readinessDrivenSender() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            ReadinessDrivenSender sender = new ReadinessDrivenSender(List.of("m1", "m2", "m3"));
            ClientCalls.asyncClientStreamingCall(client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), sender);

            assertThat(sender.result.get(10, TimeUnit.SECONDS)).isEqualTo("m1,m2,m3");
        } finally {
            stop(goServer);
        }
    }

    @Test
    @Timeout(30)
    void javaClient_goServer_serverStream() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            Iterator<StringValue> replies = ClientCalls.blockingServerStreamingCall(
                    client.getChannel(), SERVER_STREAM, CallOptions.DEFAULT, StringValue.of("hello"));
            List<String> received = new ArrayList<>();
            replies.forEachRemaining(reply -> received.add(reply.getValue()));

            assertThat(received).containsExactly("hello", "hello", "hello");
            goServer.waitForLog(SERVER_STREAM_VERIFIED);
        } finally {
            stop(goServer);
        }
    }

    /** grpc-java enforces a deadline only from the start, which waits for the first message. */
    @Test
    @Timeout(30)
    void javaClient_goServer_streamTimeoutBeforeTheFirstMessage() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = BlockingNetworkClient.create("http://localhost:" + goServer.port(),
                Signer.fromHex(PRIVATE_KEY), HealthGrpc::newBlockingStub, Duration.ofSeconds(15), Duration.ofMillis(50))) {
            CompletableFuture<String> result = new CompletableFuture<>();
            // No message and no half-close: only the stream timeout can end the call.
            ClientCalls.asyncClientStreamingCall(
                    client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));

            ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
            assertThat(Status.fromThrowable(thrown.getCause()).getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
            // Nothing was sent: the helper logs every request it gets for the stream.
            TimeUnit.MILLISECONDS.sleep(200);
            assertThat(goServer.logText()).doesNotContain("/test.v1.StreamTest/ClientStream");
        } finally {
            stop(goServer);
        }
    }

    /** A Context deadline takes the place of the stream timeout, and it too covers the wait. */
    @Test
    @Timeout(30)
    void javaClient_goServer_contextDeadlineBeforeTheFirstMessage() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Context.CancellableContext context = Context.current().withDeadlineAfter(50, TimeUnit.MILLISECONDS, scheduler);

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            CompletableFuture<String> result = new CompletableFuture<>();
            // No message and no half-close: only the Context deadline can end the call.
            context.run(() -> ClientCalls.asyncClientStreamingCall(
                    client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result)));

            ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
            assertThat(Status.fromThrowable(thrown.getCause()).getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
            TimeUnit.MILLISECONDS.sleep(200);
            assertThat(goServer.logText()).doesNotContain("/test.v1.StreamTest/ClientStream");
        } finally {
            context.cancel(null);
            scheduler.shutdownNow();
            stop(goServer);
        }
    }

    /** Over a connection already open, where a started call would put its headers on the wire at once. */
    @Test
    @Timeout(30)
    void javaClient_goServer_cancelBeforeTheFirstMessageSendsNothing() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            assertThat(sendClientStream(client.getChannel(), List.of("m1")).get(10, TimeUnit.SECONDS)).isEqualTo("m1");
            goServer.waitForLog(CLIENT_STREAM_VERIFIED);
            int afterFirstCall = goServer.logLength();

            for (int i = 0; i < 3; i++) {
                CompletableFuture<String> result = new CompletableFuture<>();
                ClientCallStreamObserver<StringValue> requests = (ClientCallStreamObserver<StringValue>)
                        ClientCalls.asyncClientStreamingCall(
                                client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));
                requests.cancel("caller gave up", null);

                ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                        ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
                Status status = Status.fromThrowable(thrown.getCause());
                assertThat(status.getCode()).isEqualTo(Status.Code.CANCELLED);
                assertThat(status.getDescription()).isEqualTo("caller gave up");
            }
            // The helper logs every request it gets for the stream.
            TimeUnit.MILLISECONDS.sleep(500);
            assertThat(goServer.logText().substring(afterFirstCall)).doesNotContain("/test.v1.StreamTest/ClientStream");
        } finally {
            stop(goServer);
        }
    }

    @Test
    @Timeout(30)
    void javaClient_goServer_emptyClientStreamIsRejected() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();

        try (var client = streamClient(goServer.port(), PRIVATE_KEY)) {
            assertUnauthenticated(sendClientStream(client.getChannel(), List.of()));
            goServer.waitForLog(CLIENT_STREAM_REJECTED + "no first message");
        } finally {
            stop(goServer);
        }
    }

    @Test
    @Timeout(30)
    void staleSignatureTimestampIsRejected() throws Exception {
        skipOrFailIfNoHelper();
        GoServer goServer = startGoServer();
        ManagedChannel channel = plainChannel(goServer.port());

        try {
            Clock twoMinutesAgo = Clock.fixed(Instant.now().minus(2, ChronoUnit.MINUTES), ZoneOffset.UTC);
            Channel stale = ClientInterceptors.intercept(channel,
                    SigningInterceptors.withClock(Signer.fromHex(PRIVATE_KEY), twoMinutesAgo));

            assertUnauthenticated(sendClientStream(stale, List.of("m1")));
            goServer.waitForLog(CLIENT_STREAM_REJECTED + "timestamp is outside the allowed time window");
        } finally {
            shutdown(channel);
            stop(goServer);
        }
    }

    private static MethodDescriptor<StringValue, StringValue> streamTestMethod(MethodType type, String name) {
        return MethodDescriptor.<StringValue, StringValue>newBuilder()
                .setType(type)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("test.v1.StreamTest", name))
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .build();
    }

    /** Starts {@code go_helper serve} on a free port, trusting {@link #PUBLIC_KEY}; one per test. */
    private static GoServer startGoServer() throws Exception {
        int port = findFreePort();
        Process process = new ProcessBuilder(GO_HELPER, "serve", String.valueOf(port), "0x" + PUBLIC_KEY)
                .redirectErrorStream(true)
                .start();
        GoServer goServer = new GoServer(process, port);
        try {
            waitForPort(port, 10_000);
        } catch (Exception e) {
            stop(goServer);
            throw e;
        }
        return goServer;
    }

    private static void stop(GoServer goServer) throws InterruptedException {
        goServer.process.destroyForcibly();
        goServer.process.waitFor(5, TimeUnit.SECONDS);
    }

    /**
     * A running {@code go_helper serve} and its merged output, drained by a daemon thread so the
     * process never blocks on a full pipe.
     */
    private static final class GoServer {
        private final Process process;
        private final int port;
        private final StringBuilder log = new StringBuilder(); // guarded by this

        GoServer(Process process, int port) {
            this.process = process;
            this.port = port;
            Thread reader = new Thread(this::readLog, "go_helper-log-" + port);
            reader.setDaemon(true);
            reader.start();
        }

        int port() {
            return port;
        }

        synchronized String logText() {
            return log.toString();
        }

        /** How much has been logged so far: the offset to wait for later lines from. */
        synchronized int logLength() {
            return log.length();
        }

        void waitForLog(String text) throws InterruptedException {
            waitForLog(text, 0, Duration.ofSeconds(10));
        }

        /** Waits until {@code text} appears in the log at or after {@code fromOffset}. */
        synchronized void waitForLog(String text, int fromOffset, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (log.indexOf(text, fromOffset) < 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new AssertionError(
                            "go_helper did not log \"" + text + "\" within " + timeout + "; its log:\n" + log);
                }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }

        private void readLog() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    append(line);
                }
            } catch (IOException e) {
                // The process was stopped.
            }
        }

        private synchronized void append(String line) {
            log.append(line).append('\n');
            notifyAll();
        }
    }

    /** A channel to the helper without the SDK's interceptors, built like the SDK's own. */
    private static ManagedChannel plainChannel(int port) {
        return OkHttpChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    }

    private static void shutdown(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }

    /** Sends the values on a new ClientStream call and half-closes it. */
    private static CompletableFuture<String> sendClientStream(Channel channel, List<String> values) {
        CompletableFuture<String> result = new CompletableFuture<>();
        StreamObserver<StringValue> requests = ClientCalls.asyncClientStreamingCall(
                channel.newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));
        values.forEach(value -> requests.onNext(StringValue.of(value)));
        requests.onCompleted();
        return result;
    }

    private static void assertUnauthenticated(CompletableFuture<String> result) {
        ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertThat(Status.fromThrowable(thrown.getCause()).getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    /** Any SDK client: the streams are called on its channel, not its stub. */
    private static BlockingNetworkClient<HealthGrpc.HealthBlockingStub> streamClient(int port, String privateKey) {
        return BlockingNetworkClient.create(
                "http://localhost:" + port, Signer.fromHex(privateKey), HealthGrpc::newBlockingStub);
    }

    private static StreamObserver<StringValue> resultObserver(CompletableFuture<String> result) {
        return new StreamObserver<>() {
            @Override
            public void onNext(StringValue value) {
                result.complete(value.getValue());
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                result.completeExceptionally(new AssertionError("completed without a reply"));
            }
        };
    }

    /** Sends on each onReady while the call reports ready. */
    private static final class ReadinessDrivenSender implements ClientResponseObserver<StringValue, StringValue> {
        final CompletableFuture<String> result = new CompletableFuture<>();
        private final Iterator<String> values;
        private ClientCallStreamObserver<StringValue> requestStream;
        private boolean completed;

        ReadinessDrivenSender(List<String> values) {
            this.values = values.iterator();
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<StringValue> requestStream) {
            this.requestStream = requestStream;
            requestStream.setOnReadyHandler(this::drain);
        }

        synchronized void drain() {
            while (!completed && requestStream.isReady()) {
                if (values.hasNext()) {
                    requestStream.onNext(StringValue.of(values.next()));
                } else {
                    requestStream.onCompleted();
                    completed = true;
                }
            }
        }

        @Override
        public void onNext(StringValue value) {
            result.complete(value.getValue());
        }

        @Override
        public void onError(Throwable t) {
            result.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
        }
    }

    private static final class MinimalProviderService extends ProviderServiceGrpc.ProviderServiceImplBase {
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
