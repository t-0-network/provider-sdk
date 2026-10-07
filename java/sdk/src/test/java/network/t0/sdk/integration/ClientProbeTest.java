package network.t0.sdk.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.AsyncNetworkClient;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.network.FutureNetworkClient;
import network.t0.sdk.network.NetworkClient;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Java column of the shared client behavior: every case of client_cases in
 * cross_test/test_vectors.json, made by each of the three Java clients over gRPC against
 * {@code go_helper client-probe}, which checks each request it gets.
 */
class ClientProbeTest {

    // Gradle runs tests with CWD = the module directory (java/sdk).
    private static final File CROSS_TEST = new File(System.getProperty("user.dir"), "../../cross_test");
    private static final File GO_HELPER = new File(CROSS_TEST, "go_helper/go_helper");
    private static final File VECTORS = new File(CROSS_TEST, "test_vectors.json");

    /** How long a test waits for an async or future call to end: longer than any deadline a case sets. */
    private static final long WAIT_SECONDS = 120;

    @ParameterizedTest(name = "{0}")
    @EnumSource(Client.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void sharedClientCases(Client client) throws Exception {
        if (!GO_HELPER.exists()) {
            if (System.getenv("CI") != null) {
                throw new AssertionError("Go helper binary required in CI but not found at " + GO_HELPER);
            }
            assumeTrue(false, "Go helper not found at " + GO_HELPER);
        }
        JsonObject vectors = JsonParser.parseString(Files.readString(VECTORS.toPath())).getAsJsonObject();
        // Java's client takes a signer, not a hex key: a hex-key case and a factory case are the same.
        Signer signer = Signer.fromHex(vectors.getAsJsonObject("keys").get("private_key").getAsString());
        Signer impostor = Signer.fromHex(vectors.getAsJsonObject("impostor_keys").get("private_key").getAsString());

        Process probe = new ProcessBuilder(GO_HELPER.getCanonicalPath(), "client-probe", "--sdk", "java",
                "--vectors", VECTORS.getCanonicalPath())
                .start();
        // Its PASS and FAIL lines, shown when a case fails.
        StringBuffer log = new StringBuffer();
        Thread logReader = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(probe.getErrorStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> log.append(line).append('\n'));
            } catch (Exception ignored) {
                // The probe was killed.
            }
        });
        logReader.start();
        try {
            String ready = new BufferedReader(new InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            assertThat(ready).as("first line of client-probe").startsWith("READY ");
            String baseUrl = ready.substring("READY ".length()).strip();

            SoftAssertions softly = new SoftAssertions();
            for (JsonElement element : vectors.getAsJsonArray("client_cases")) {
                JsonObject c = element.getAsJsonObject();
                String name = c.get("name").getAsString();
                JsonObject expect = c.getAsJsonObject("expect");
                DigestSigner caseSigner = c.has("custom_signer")
                        ? customSigner(impostor, c.getAsJsonObject("custom_signer"))
                        : signer;
                Outcome got = client.call(baseUrl + "/" + name, caseSigner, c);
                softly.assertThat(got.code()).as("%s with %s: %s%nclient-probe log:%n%s", name, client, got.detail(), log)
                        .isEqualTo(expect.get("code").getAsString());
                if (expect.has("message")) {
                    softly.assertThat(got.detail()).as("%s with %s: message", name, client)
                            .isEqualTo(expect.get("message").getAsString());
                }
            }
            softly.assertAll();
        } finally {
            probe.destroy();
            probe.waitFor(10, TimeUnit.SECONDS);
            logReader.join(10_000);
        }
    }

    /** How a call ended: "ok" for a SERVING reply, else the error code; and its message. */
    private record Outcome(String code, String detail) {}

    /** A custom signer: the impostor key's signature and key, changed as the case says. */
    private static DigestSigner customSigner(Signer impostor, JsonObject change) {
        return digest -> {
            if (change.has("error")) {
                throw new IllegalStateException(change.get("error").getAsString());
            }
            SignResult signed = impostor.sign(digest);
            byte[] signature = signed.getSignature();
            if (change.has("signature")) {
                signature = switch (change.get("signature").getAsString()) {
                    case "r_s" -> Arrays.copyOf(signature, 64);
                    case "v_plus_27" -> {
                        signature[64] += 27;
                        yield signature;
                    }
                    case "first_63_bytes" -> Arrays.copyOf(signature, 63);
                    default -> throw new AssertionError("unknown signature change " + change);
                };
            }
            byte[] publicKey = change.has("public_key")
                    ? HexUtils.hexToBytes(change.get("public_key").getAsString())
                    : signed.getPublicKey();
            return new SignResult(signature, publicKey);
        };
    }

    /**
     * Each client the Java SDK ships, making one unary Health/Check as the case says: built with the case's
     * client timeout through the client's public factory, and with its per-call deadline on the stub.
     */
    enum Client {
        BLOCKING {
            @Override
            Outcome call(String baseUrl, DigestSigner signer, JsonObject c) {
                BlockingNetworkClient<HealthGrpc.HealthBlockingStub> client = c.has("client_timeout_ms")
                        ? BlockingNetworkClient.create(baseUrl, signer, HealthGrpc::newBlockingStub,
                                clientTimeout(c), NetworkClient.DEFAULT_STREAM_TIMEOUT)
                        : BlockingNetworkClient.create(baseUrl, signer, HealthGrpc::newBlockingStub);
                try (client) {
                    HealthGrpc.HealthBlockingStub stub = c.has("call_timeout_ms")
                            ? client.stub(callTimeoutMs(c), TimeUnit.MILLISECONDS)
                            : client.stub();
                    return outcome(() -> stub.check(HealthCheckRequest.getDefaultInstance()));
                }
            }
        },
        ASYNC {
            @Override
            Outcome call(String baseUrl, DigestSigner signer, JsonObject c) {
                AsyncNetworkClient<HealthGrpc.HealthStub> client = c.has("client_timeout_ms")
                        ? AsyncNetworkClient.create(baseUrl, signer, HealthGrpc::newStub,
                                clientTimeout(c), NetworkClient.DEFAULT_STREAM_TIMEOUT)
                        : AsyncNetworkClient.create(baseUrl, signer, HealthGrpc::newStub);
                // The wait stays inside the try: closing the client cancels a call still in flight.
                try (client) {
                    HealthGrpc.HealthStub stub = c.has("call_timeout_ms")
                            ? client.stub(callTimeoutMs(c), TimeUnit.MILLISECONDS)
                            : client.stub();
                    return outcome(() -> {
                        CompletableFuture<HealthCheckResponse> reply = new CompletableFuture<>();
                        stub.check(HealthCheckRequest.getDefaultInstance(), new StreamObserver<>() {
                            @Override
                            public void onNext(HealthCheckResponse response) {
                                reply.complete(response);
                            }

                            @Override
                            public void onError(Throwable t) {
                                reply.completeExceptionally(t);
                            }

                            @Override
                            public void onCompleted() {
                                reply.completeExceptionally(new AssertionError("completed without a reply"));
                            }
                        });
                        return reply.get(WAIT_SECONDS, TimeUnit.SECONDS);
                    });
                }
            }
        },
        FUTURE {
            @Override
            Outcome call(String baseUrl, DigestSigner signer, JsonObject c) {
                FutureNetworkClient<HealthGrpc.HealthFutureStub> client = c.has("client_timeout_ms")
                        ? FutureNetworkClient.create(baseUrl, signer, HealthGrpc::newFutureStub,
                                clientTimeout(c), NetworkClient.DEFAULT_STREAM_TIMEOUT)
                        : FutureNetworkClient.create(baseUrl, signer, HealthGrpc::newFutureStub);
                // The wait stays inside the try: closing the client cancels a call still in flight.
                try (client) {
                    HealthGrpc.HealthFutureStub stub = c.has("call_timeout_ms")
                            ? client.stub(callTimeoutMs(c), TimeUnit.MILLISECONDS)
                            : client.stub();
                    return outcome(() -> stub.check(HealthCheckRequest.getDefaultInstance())
                            .get(WAIT_SECONDS, TimeUnit.SECONDS));
                }
            }
        };

        abstract Outcome call(String baseUrl, DigestSigner signer, JsonObject c);

        private static Duration clientTimeout(JsonObject c) {
            return Duration.ofMillis(c.get("client_timeout_ms").getAsLong());
        }

        private static long callTimeoutMs(JsonObject c) {
            return c.get("call_timeout_ms").getAsLong();
        }
    }

    /**
     * How the call ended. A blocking call throws its StatusRuntimeException; an async or future call ends with
     * it as the cause of the ExecutionException. Any other end is reported as it is, so it fails every case.
     */
    private static Outcome outcome(Callable<HealthCheckResponse> call) {
        Throwable error;
        try {
            HealthCheckResponse response = call.call();
            return response.getStatus() == HealthCheckResponse.ServingStatus.SERVING
                    ? new Outcome("ok", "")
                    : new Outcome(response.getStatus().name(), "");
        } catch (ExecutionException e) {
            error = e.getCause();
        } catch (TimeoutException e) {
            return new Outcome("no end within " + WAIT_SECONDS + " s", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        } catch (Exception e) {
            error = e;
        }
        if (error instanceof StatusRuntimeException e) {
            // The message is compared where the case gives one, and shows why the probe refused a request.
            return new Outcome(e.getStatus().getCode().name().toLowerCase(Locale.ROOT),
                    String.valueOf(e.getStatus().getDescription()));
        }
        return new Outcome("not a status error", String.valueOf(error));
    }
}
