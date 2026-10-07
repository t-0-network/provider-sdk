package network.t0.sdk.provider;

import network.t0.sdk.common.Messages;
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
 * UNIMPLEMENTED: it is server-streaming, and this server verifies the signature
 * of unary calls only.
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
    static final String SDK_VERSION = loadSdkVersion();

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
                    .withDescription(String.format(Messages.UNKNOWN_SERVICE, request.getService()))
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(SERVING);
        responseObserver.onCompleted();
    }

    /** Stamps the SDK identity onto every Check reply, and nothing else's. */
    static ServerInterceptor sdkIdentityInterceptor(String versionOverride) {
        String effectiveVersion = versionOverride != null ? versionOverride : SDK_VERSION;
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                if (!HealthGrpc.getCheckMethod().getFullMethodName()
                        .equals(call.getMethodDescriptor().getFullMethodName())) {
                    return next.startCall(call, headers);
                }
                return next.startCall(new io.grpc.ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    private boolean headersSent;

                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        headersSent = true;
                        responseHeaders.put(SDK_ECOSYSTEM_HEADER, SDK_ECOSYSTEM);
                        responseHeaders.put(SDK_VERSION_HEADER, effectiveVersion);
                        super.sendHeaders(responseHeaders);
                    }

                    @Override
                    public void close(Status status, Metadata trailers) {
                        // NOT_FOUND sends no headers of its own; the identity goes on it too.
                        if (!headersSent) {
                            sendHeaders(new Metadata());
                        }
                        super.close(status, trailers);
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
