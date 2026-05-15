package network.t0.sdk.provider;

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.proto.tzero.v1.system.HealthRequest;
import network.t0.sdk.proto.tzero.v1.system.HealthResponse;
import network.t0.sdk.proto.tzero.v1.system.SdkEcosystem;
import network.t0.sdk.proto.tzero.v1.system.SystemServiceGrpc;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Auto-registered SystemService implementation. Returns the list of
 * registered RPC services, the server's current time, the SDK version
 * (loaded from META-INF/sdk-version.properties at runtime), and the
 * SDK ecosystem identifier.
 */
final class SystemServiceImpl extends SystemServiceGrpc.SystemServiceImplBase {

    static final String SDK_VERSION = loadSdkVersion();

    private final List<String> services;

    SystemServiceImpl(List<String> services) {
        this.services = Collections.unmodifiableList(services);
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        Instant now = Instant.now();
        HealthResponse response = HealthResponse.newBuilder()
                .addAllServices(services)
                .setCurrentTime(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .setSdkVersion(SDK_VERSION)
                .setSdkEcosystem(SdkEcosystem.SDK_ECOSYSTEM_JAVA)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    static String loadSdkVersion() {
        try (InputStream in = SystemServiceImpl.class.getResourceAsStream("/META-INF/sdk-version.properties")) {
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
