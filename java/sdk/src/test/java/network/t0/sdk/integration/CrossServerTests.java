package network.t0.sdk.integration;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
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
                        .check(HealthCheckRequest.getDefaultInstance());

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
                    "0x" + PUBLIC_KEY,
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
