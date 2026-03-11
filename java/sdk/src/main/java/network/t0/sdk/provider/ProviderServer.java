package network.t0.sdk.provider;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A gRPC server that handles requests from the T-0 Network with automatic signature verification.
 *
 * <p>The server automatically verifies request signatures from the T-0 Network
 * before passing requests to the provider's service implementation.
 *
 * <p>Example usage:
 * <pre>{@code
 * ProviderServiceGrpc.ProviderServiceImplBase serviceImpl = new MyProviderService();
 *
 * ProviderServer server = ProviderServer.create(8080, networkPublicKeyHex)
 *     .withService(serviceImpl)
 *     .start();
 *
 * // Or use the convenience method for single-service servers:
 * ProviderServer server = ProviderServer.startWith(8080, networkPublicKeyHex, serviceImpl);
 *
 * server.awaitTermination();
 * }</pre>
 *
 */
public final class ProviderServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ProviderServer.class);

    private final Server server;

    private ProviderServer(Server server) {
        this.server = server;
    }

    /**
     * Creates a new builder for ProviderServer with required parameters.
     *
     * @param port             the port to listen on (use 0 for any available port)
     * @param networkPublicKey the T-0 Network public key in hex format (with or without 0x prefix)
     * @return a new Builder instance
     * @throws IllegalArgumentException if port is out of range or networkPublicKey is null/empty
     */
    public static Builder create(int port, String networkPublicKey) {
        return new Builder(port, networkPublicKey);
    }

    /**
     * Convenience method to create and start a server with a single service.
     *
     * @param port             the port to listen on (use 0 for any available port)
     * @param networkPublicKey the T-0 Network public key in hex format (with or without 0x prefix)
     * @param service          the ProviderService implementation
     * @return the started ProviderServer
     * @throws IOException              if the server fails to start
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static ProviderServer startWith(int port, String networkPublicKey, BindableService service) throws IOException {
        return create(port, networkPublicKey)
                .withService(service)
                .start();
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the port number
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * Initiates an orderly shutdown of the server.
     * Existing RPCs will continue, but new RPCs will be rejected.
     */
    public void shutdown() {
        server.shutdown();
    }

    /**
     * Initiates a forceful shutdown of the server.
     * All RPCs will be cancelled immediately.
     */
    public void shutdownNow() {
        server.shutdownNow();
    }

    /**
     * Waits for the server to become terminated.
     *
     * @return this server instance for chaining
     * @throws InterruptedException if interrupted while waiting
     */
    public ProviderServer awaitTermination() throws InterruptedException {
        server.awaitTermination();
        return this;
    }

    /**
     * Waits for the server to become terminated with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return true if the server terminated, false if the timeout was reached
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return server.awaitTermination(timeout, unit);
    }

    /**
     * Returns true if the server has been shut down.
     *
     * @return true if shut down
     */
    public boolean isShutdown() {
        return server.isShutdown();
    }

    /**
     * Returns true if the server has terminated.
     *
     * @return true if terminated
     */
    public boolean isTerminated() {
        return server.isTerminated();
    }

    /**
     * Returns the underlying gRPC Server for advanced use cases.
     *
     * @return the underlying Server
     */
    public Server getServer() {
        return server;
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(5, TimeUnit.SECONDS)) {
                shutdownNow();
                awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builder for creating a ProviderServer with fluent API.
     */
    public static final class Builder {

        private final int port;
        private final String networkPublicKey;
        private final List<BindableService> services = new ArrayList<>();
        private int maxInboundMessageSize = 4 * 1024 * 1024; // 4MB default
        private int maxInboundMetadataSize = 8192; // 8KB default
        private long handshakeTimeout = 120;
        private TimeUnit handshakeTimeoutUnit = TimeUnit.SECONDS;

        private Builder(int port, String networkPublicKey) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 0 and 65535");
            }
            if (networkPublicKey == null || networkPublicKey.isEmpty()) {
                throw new IllegalArgumentException("networkPublicKey must not be null or empty");
            }
            this.port = port;
            this.networkPublicKey = networkPublicKey;
        }

        /**
         * Adds a service to the server.
         *
         * <p>Multiple services can be added by chaining this method.
         *
         * @param service the service implementation
         * @return this builder
         * @throws IllegalArgumentException if service is null
         */
        public Builder withService(BindableService service) {
            if (service == null) {
                throw new IllegalArgumentException("service must not be null");
            }
            this.services.add(service);
            return this;
        }

        /**
         * Sets the maximum inbound message size in bytes.
         *
         * @param bytes the maximum size in bytes
         * @return this builder
         * @throws IllegalArgumentException if bytes is not positive
         */
        public Builder withMaxInboundMessageSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("maxInboundMessageSize must be positive");
            }
            this.maxInboundMessageSize = bytes;
            return this;
        }

        /**
         * Sets the maximum inbound metadata (headers) size in bytes.
         *
         * @param bytes the maximum size in bytes
         * @return this builder
         * @throws IllegalArgumentException if bytes is not positive
         */
        public Builder withMaxInboundMetadataSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("maxInboundMetadataSize must be positive");
            }
            this.maxInboundMetadataSize = bytes;
            return this;
        }

        /**
         * Sets the handshake timeout.
         *
         * @param timeout the timeout value
         * @param unit    the time unit
         * @return this builder
         * @throws IllegalArgumentException if timeout is not positive or unit is null
         */
        public Builder withHandshakeTimeout(long timeout, TimeUnit unit) {
            if (timeout <= 0) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (unit == null) {
                throw new IllegalArgumentException("unit must not be null");
            }
            this.handshakeTimeout = timeout;
            this.handshakeTimeoutUnit = unit;
            return this;
        }

        /**
         * Builds the ProviderServer without starting it.
         *
         * @return the configured ProviderServer (not yet started)
         * @throws IllegalStateException if no services have been added
         */
        public ProviderServer build() {
            if (services.isEmpty()) {
                throw new IllegalStateException("at least one service must be added with withService()");
            }
            return new ProviderServer(buildGrpcServer());
        }

        /**
         * Builds and starts the ProviderServer.
         *
         * <p>This is the recommended way to create a server.
         *
         * @return the started ProviderServer
         * @throws IOException           if the server fails to start
         * @throws IllegalStateException if no services have been added
         */
        public ProviderServer start() throws IOException {
            ProviderServer providerServer = build();
            providerServer.server.start();
            log.info("Provider server started on port {}", providerServer.getPort());
            return providerServer;
        }

        private Server buildGrpcServer() {
            SignatureVerificationInterceptor verificationInterceptor =
                    new SignatureVerificationInterceptor(networkPublicKey);

            NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                    .maxInboundMessageSize(maxInboundMessageSize)
                    .maxInboundMetadataSize(maxInboundMetadataSize)
                    .handshakeTimeout(handshakeTimeout, handshakeTimeoutUnit);

            for (BindableService service : services) {
                ServerServiceDefinition originalDefinition = service.bindService();
                ServerServiceDefinition withInputStream = ServerInterceptors.useInputStreamMessages(originalDefinition);
                ServerServiceDefinition interceptedDefinition =
                        ServerInterceptors.intercept(withInputStream, verificationInterceptor);
                builder.addService(interceptedDefinition);
            }

            return builder.build();
        }
    }

}
