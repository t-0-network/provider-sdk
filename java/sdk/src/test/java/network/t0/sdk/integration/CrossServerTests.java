package network.t0.sdk.integration;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
        int port = findFreePort();

        Process goServer = new ProcessBuilder(GO_HELPER, "serve", String.valueOf(port), "0x" + PUBLIC_KEY)
                .redirectErrorStream(true)
                .start();

        try {
            waitForPort(port, 10_000);

            try (var client = BlockingNetworkClient.create(
                    "http://localhost:" + port,
                    Signer.fromHex(PRIVATE_KEY),
                    HealthGrpc::newBlockingStub)) {

                HealthCheckResponse response = client.stub()
                        .check(HealthCheckRequest.newBuilder().setService(HealthGrpc.SERVICE_NAME).build());

                assertThat(response.getStatus())
                        .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
            }
        } finally {
            goServer.destroyForcibly();
            goServer.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void javaClient_goServer_payOut() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();

        Process goServer = new ProcessBuilder(GO_HELPER, "serve", String.valueOf(port), "0x" + PUBLIC_KEY)
                .redirectErrorStream(true)
                .start();

        try {
            waitForPort(port, 10_000);

            try (var client = BlockingNetworkClient.create(
                    "http://localhost:" + port,
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
            goServer.destroyForcibly();
            goServer.waitFor(5, TimeUnit.SECONDS);
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

    // --- Streaming: Java client -> Go server over gRPC (h2c) ---
    //
    // go_helper serves test.v1.StreamTest (cross_test/stream_test.proto) behind a verifier that
    // checks the signature over the first request message only, as the T-0 Network does, and
    // answers 401 (UNAUTHENTICATED) otherwise. Java signs that message without its gRPC prefix.
    // The methods are built by hand and called through the SDK's channel, so the signing and
    // default-deadline interceptors apply.

    private static final MethodDescriptor<StringValue, StringValue> CLIENT_STREAM =
            streamTestMethod(MethodType.CLIENT_STREAMING, "ClientStream");
    private static final MethodDescriptor<StringValue, StringValue> SERVER_STREAM =
            streamTestMethod(MethodType.SERVER_STREAMING, "ServerStream");

    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();
        Process goServer = startGoServer(port);

        try (var client = streamClient(port, PRIVATE_KEY)) {
            CompletableFuture<String> result = new CompletableFuture<>();
            StreamObserver<StringValue> requests = ClientCalls.asyncClientStreamingCall(
                    client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));
            requests.onNext(StringValue.of("m1"));
            requests.onNext(StringValue.of("m2"));
            requests.onNext(StringValue.of("m3"));
            requests.onCompleted();

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("m1,m2,m3");
        } finally {
            stop(goServer);
        }
    }

    /** BlockingClientCall.write waits for isReady() before each message, the first one included. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_blockingWrite() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();
        Process goServer = startGoServer(port);

        try (var client = streamClient(port, PRIVATE_KEY)) {
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

    /** A sender that only sends while isReady() holds: directly for the first message, then on onReady. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_readinessDrivenSender() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();
        Process goServer = startGoServer(port);

        try (var client = streamClient(port, PRIVATE_KEY)) {
            ReadinessDrivenSender sender = new ReadinessDrivenSender(List.of("m1", "m2", "m3"));
            ClientCalls.asyncClientStreamingCall(client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), sender);
            sender.drain();

            assertThat(sender.result.get(10, TimeUnit.SECONDS)).isEqualTo("m1,m2,m3");
        } finally {
            stop(goServer);
        }
    }

    @Test
    @Timeout(30)
    void javaClient_goServer_serverStream() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();
        Process goServer = startGoServer(port);

        try (var client = streamClient(port, PRIVATE_KEY)) {
            Iterator<StringValue> replies = ClientCalls.blockingServerStreamingCall(
                    client.getChannel(), SERVER_STREAM, CallOptions.DEFAULT, StringValue.of("hello"));
            List<String> received = new ArrayList<>();
            replies.forEachRemaining(reply -> received.add(reply.getValue()));

            assertThat(received).containsExactly("hello", "hello", "hello");
        } finally {
            stop(goServer);
        }
    }

    /** The verifier is not a pass-through: a stream signed by an unknown key is refused. */
    @Test
    @Timeout(30)
    void javaClient_goServer_clientStream_unknownKeyIsRejected() throws Exception {
        skipOrFailIfNoHelper();
        int port = findFreePort();
        Process goServer = startGoServer(port);

        String otherPrivateKey = "0000000000000000000000000000000000000000000000000000000000000001";
        try (var client = streamClient(port, otherPrivateKey)) {
            CompletableFuture<String> result = new CompletableFuture<>();
            StreamObserver<StringValue> requests = ClientCalls.asyncClientStreamingCall(
                    client.getChannel().newCall(CLIENT_STREAM, CallOptions.DEFAULT), resultObserver(result));
            requests.onNext(StringValue.of("m1"));
            requests.onCompleted();

            ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
            assertThat(io.grpc.Status.fromThrowable(thrown.getCause()).getCode())
                    .isEqualTo(io.grpc.Status.Code.UNAUTHENTICATED);
        } finally {
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

    private static Process startGoServer(int port) throws Exception {
        Process goServer = new ProcessBuilder(GO_HELPER, "serve", String.valueOf(port), "0x" + PUBLIC_KEY)
                .redirectErrorStream(true)
                .start();
        try {
            waitForPort(port, 10_000);
        } catch (Exception e) {
            stop(goServer);
            throw e;
        }
        return goServer;
    }

    private static void stop(Process goServer) throws InterruptedException {
        goServer.destroyForcibly();
        goServer.waitFor(5, TimeUnit.SECONDS);
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

    /**
     * Sends only while the call reports ready: the flow-control pattern of the gRPC examples.
     * {@link #drain()} runs once from the caller for the first message and then on each onReady.
     */
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
