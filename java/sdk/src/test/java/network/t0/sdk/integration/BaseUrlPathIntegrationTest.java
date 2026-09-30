package network.t0.sdk.integration;

import io.grpc.HandlerRegistry;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.SignatureVerificationInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A path in the base URL prefixes every call; the signature does not cover the URL. The server records the
 * method each call names, finds the method under the prefix, and verifies the call as ProviderServer does.
 */
class BaseUrlPathIntegrationTest {

    private static final String PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    @ParameterizedTest
    @ValueSource(strings = {"/prefix", "/prefix/", "/sda/payments/t0"})
    @DisplayName("A path in the base URL prefixes every call, with or without a trailing /")
    void pathPrefixesEveryCall(String path) throws Exception {
        String prefix = path.substring(1).replaceAll("/$", "");
        ServerServiceDefinition health = ServerInterceptors.intercept(
                ServerInterceptors.useInputStreamMessages(new ServingHealth().bindService()),
                new SignatureVerificationInterceptor(PUBLIC_KEY_HEX));
        List<String> methods = new CopyOnWriteArrayList<>();
        Server server = NettyServerBuilder.forPort(0)
                .fallbackHandlerRegistry(new HandlerRegistry() {
                    @Override
                    public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
                        methods.add(methodName);
                        return methodName.startsWith(prefix + "/")
                                ? health.getMethod(methodName.substring(prefix.length() + 1))
                                : null;
                    }
                })
                .build()
                .start();
        try (var client = BlockingNetworkClient.create(
                "http://localhost:" + server.getPort() + path, Signer.fromHex(PRIVATE_KEY), HealthGrpc::newBlockingStub)) {
            // SERVING only after the interceptor has verified the signature.
            assertThat(client.stub().check(HealthCheckRequest.getDefaultInstance()).getStatus())
                    .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(methods).containsExactly(prefix + "/grpc.health.v1.Health/Check");
    }

    private static final class ServingHealth extends HealthGrpc.HealthImplBase {
        @Override
        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
            responseObserver.onNext(HealthCheckResponse.newBuilder()
                    .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                    .build());
            responseObserver.onCompleted();
        }
    }
}
