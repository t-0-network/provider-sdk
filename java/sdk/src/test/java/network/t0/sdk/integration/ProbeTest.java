package network.t0.sdk.integration;

import io.grpc.stub.StreamObserver;
import network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest;
import network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse;
import network.t0.sdk.proto.tzero.v1.payment.PayoutRequest;
import network.t0.sdk.proto.tzero.v1.payment.PayoutResponse;
import network.t0.sdk.proto.tzero.v1.payment.ProviderServiceGrpc;
import network.t0.sdk.provider.ProviderServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Java column of the shared server behavior: every case of server_cases in
 * cross_test/test_vectors.json, sent by {@code go_helper probe} over gRPC (the protocol the Java
 * server serves) to a {@link ProviderServer} with its defaults.
 */
class ProbeTest {

    // Gradle runs tests with CWD = the module directory (java/sdk).
    private static final File CROSS_TEST = new File(System.getProperty("user.dir"), "../../cross_test");
    private static final File GO_HELPER = new File(CROSS_TEST, "go_helper/go_helper");
    private static final File VECTORS = new File(CROSS_TEST, "test_vectors.json");

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void sharedServerCases() throws Exception {
        if (!GO_HELPER.exists()) {
            if (System.getenv("CI") != null) {
                throw new AssertionError("Go helper binary required in CI but not found at " + GO_HELPER);
            }
            assumeTrue(false, "Go helper not found at " + GO_HELPER);
        }
        String vectors = java.nio.file.Files.readString(VECTORS.toPath());
        String networkPublicKey = "0x" + com.google.gson.JsonParser.parseString(vectors)
                .getAsJsonObject().getAsJsonObject("keys").get("public_key").getAsString();

        // Health is mounted by the SDK on every server. ApprovePaymentQuotes and PayOut answer with
        // responses that fail validation: the response-invalid cases. ApprovePaymentQuotes's oneof
        // result is required, and PayOut's failed.details is at most 1024 characters.
        ProviderServer server = ProviderServer.create(0, networkPublicKey)
                .withService(new ProviderServiceGrpc.ProviderServiceImplBase() {
                    @Override
                    public void approvePaymentQuotes(ApprovePaymentQuoteRequest request,
                                                     StreamObserver<ApprovePaymentQuoteResponse> observer) {
                        observer.onNext(ApprovePaymentQuoteResponse.getDefaultInstance());
                        observer.onCompleted();
                    }

                    @Override
                    public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> observer) {
                        observer.onNext(PayoutResponse.newBuilder()
                                .setFailed(PayoutResponse.Failed.newBuilder().setDetails("x".repeat(1025)))
                                .build());
                        observer.onCompleted();
                    }
                })
                .start();
        try {
            Process probe = new ProcessBuilder(GO_HELPER.getCanonicalPath(), "probe",
                    "http://127.0.0.1:" + server.getPort(), "--sdk", "java", "--protocol", "grpc",
                    "--vectors", VECTORS.getCanonicalPath())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(probe.waitFor(240, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.exitValue()).as(output).isZero();
        } finally {
            server.close();
        }
    }
}
