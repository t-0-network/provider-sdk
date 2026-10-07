package network.t0.sdk.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.network.NetworkClient;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Java column of the shared client behavior: every case of client_cases in
 * cross_test/test_vectors.json, made by the Java client over gRPC against
 * {@code go_helper client-probe}, which checks each request it gets.
 */
class ClientProbeTest {

    // Gradle runs tests with CWD = the module directory (java/sdk).
    private static final File CROSS_TEST = new File(System.getProperty("user.dir"), "../../cross_test");
    private static final File GO_HELPER = new File(CROSS_TEST, "go_helper/go_helper");
    private static final File VECTORS = new File(CROSS_TEST, "test_vectors.json");

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void sharedClientCases() throws Exception {
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
                Outcome got = call(baseUrl + "/" + name, caseSigner, c);
                softly.assertThat(got.code()).as("%s: %s%nclient-probe log:%n%s", name, got.detail(), log)
                        .isEqualTo(expect.get("code").getAsString());
                if (expect.has("message")) {
                    softly.assertThat(got.detail()).as("%s: message", name)
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

    /** One unary Health/Check, as the case says. */
    private static Outcome call(String baseUrl, DigestSigner signer, JsonObject c) {
        BlockingNetworkClient<HealthGrpc.HealthBlockingStub> client = c.has("client_timeout_ms")
                ? BlockingNetworkClient.create(baseUrl, signer, HealthGrpc::newBlockingStub,
                        Duration.ofMillis(c.get("client_timeout_ms").getAsLong()), NetworkClient.DEFAULT_STREAM_TIMEOUT)
                : BlockingNetworkClient.create(baseUrl, signer, HealthGrpc::newBlockingStub);
        try (client) {
            HealthGrpc.HealthBlockingStub stub = client.stub();
            if (c.has("call_timeout_ms")) {
                stub = stub.withDeadlineAfter(c.get("call_timeout_ms").getAsLong(), TimeUnit.MILLISECONDS);
            }
            HealthCheckResponse response = stub.check(HealthCheckRequest.getDefaultInstance());
            return response.getStatus() == HealthCheckResponse.ServingStatus.SERVING
                    ? new Outcome("ok", "")
                    : new Outcome(response.getStatus().name(), "");
        } catch (StatusRuntimeException e) {
            // The message is compared where the case gives one, and shows why the probe refused a request.
            return new Outcome(e.getStatus().getCode().name().toLowerCase(Locale.ROOT),
                    String.valueOf(e.getStatus().getDescription()));
        }
    }
}
