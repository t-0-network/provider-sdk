package network.t0.sdk.provider;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * The health service the transport mounts on every server it builds — see
 * {@code docs/HEALTH_SERVICE.md}.
 *
 * <p>Reports SERVING for the services registered on this server and NOT_FOUND
 * for anything else. The set is frozen at construction; nothing is computed per
 * request. {@code watch} is left at {@link HealthGrpc.HealthImplBase}'s
 * UNIMPLEMENTED: it is server-streaming, and the body-hash signature scheme
 * these servers run behind has no story for streams.
 */
final class HealthServiceImpl extends HealthGrpc.HealthImplBase {

    /**
     * Headers carrying the identity of the SDK answering the probe. They ride on
     * the health response and nowhere else: {@code HealthCheckResponse} has a
     * single status field and {@code Check} names its service in the request, so
     * the contract itself has no room for this.
     */
    static final Metadata.Key<String> SDK_ECOSYSTEM_HEADER =
            Metadata.Key.of("t0-sdk-ecosystem", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> SDK_VERSION_HEADER =
            Metadata.Key.of("t0-sdk-version", Metadata.ASCII_STRING_MARSHALLER);

    private static final String SDK_ECOSYSTEM = "java";
    private static final String SDK_VERSION = loadSdkVersion();

    private static final HealthCheckResponse SERVING = HealthCheckResponse.newBuilder()
            .setStatus(HealthCheckResponse.ServingStatus.SERVING)
            .build();

    private final Set<String> registered;

    HealthServiceImpl(Set<String> registered) {
        this.registered = Set.copyOf(registered);
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        // An empty service name asks about the process as a whole, which is up if
        // this handler is running at all.
        if (!request.getService().isEmpty() && !registered.contains(request.getService())) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("unknown service '" + request.getService() + "'")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(SERVING);
        responseObserver.onCompleted();
    }

    /** Stamps the SDK identity onto responses from this service only. */
    static ServerInterceptor sdkIdentityInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                return next.startCall(new io.grpc.ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        responseHeaders.put(SDK_ECOSYSTEM_HEADER, SDK_ECOSYSTEM);
                        responseHeaders.put(SDK_VERSION_HEADER, SDK_VERSION);
                        super.sendHeaders(responseHeaders);
                    }
                }, headers);
            }
        };
    }

    /**
     * The version ships as a classpath resource rather than a constant because it
     * has to survive jar shading, which loses {@code META-INF/MANIFEST.MF} Maven
     * metadata. Consumers who repackage with relocation rules need to keep
     * {@code META-INF/} intact.
     */
    private static String loadSdkVersion() {
        try (InputStream in = HealthServiceImpl.class.getResourceAsStream("/META-INF/sdk-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("sdk.version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
