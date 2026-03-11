package network.t0.sdk.network;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.okhttp.OkHttpChannelBuilder;
import network.t0.sdk.common.Headers;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for gRPC clients with automatic request signing.
 *
 * <p>This client automatically signs all outgoing requests using the provided signer,
 * matching the signature scheme expected by the T-0 Network.
 *
 * <p>The client implements {@link Closeable} and should be closed when no longer needed
 * to release resources (thread pools, connections).
 *
 * <p>Use one of the concrete implementations:
 * <ul>
 *   <li>{@link BlockingNetworkClient} - for synchronous/blocking calls</li>
 *   <li>{@link AsyncNetworkClient} - for asynchronous calls with StreamObserver</li>
 *   <li>{@link FutureNetworkClient} - for asynchronous calls with ListenableFuture</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Signer signer = Signer.fromHex(privateKeyHex);
 * try (var client = BlockingNetworkClient.create(
 *         "https://api.t-0.network",
 *         signer,
 *         NetworkServiceGrpc::newBlockingStub)) {
 *     client.stub().updateQuote(request);
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b> Client instances are thread-safe. The underlying gRPC channel
 * and stubs support concurrent use from multiple threads. The signing interceptor creates
 * independent state for each call.
 *
 * @see BlockingNetworkClient
 * @see AsyncNetworkClient
 * @see FutureNetworkClient
 */
public abstract class NetworkClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(NetworkClient.class);

    /**
     * Default timeout in seconds for requests.
     */
    protected static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * The underlying gRPC managed channel.
     */
    protected final ManagedChannel channel;

    /**
     * The channel with signing interceptor applied.
     */
    protected final Channel interceptedChannel;

    /**
     * Creates a new NetworkClient with the given channels.
     *
     * @param channel            the underlying managed channel
     * @param interceptedChannel the channel with signing interceptor applied
     */
    protected NetworkClient(ManagedChannel channel, Channel interceptedChannel) {
        this.channel = channel;
        this.interceptedChannel = interceptedChannel;
    }

    /**
     * Result of creating a channel pair.
     *
     * @param channel            the underlying managed channel
     * @param interceptedChannel the channel with signing interceptor applied
     */
    protected record ChannelPair(ManagedChannel channel, Channel interceptedChannel) {}

    /**
     * Creates a channel pair for the given endpoint with signing interceptor.
     *
     * @param endpoint       the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer         the signer to use for signing requests
     * @param timeoutSeconds the timeout in seconds for requests
     * @return a ChannelPair containing the managed channel and intercepted channel
     * @throws IllegalArgumentException if the endpoint or signer is invalid
     */
    protected static ChannelPair createChannel(String endpoint, Signer signer, int timeoutSeconds) {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be null or empty");
        }
        if (signer == null) {
            throw new IllegalArgumentException("signer must not be null");
        }

        EndpointInfo endpointInfo = parseEndpoint(endpoint);

        OkHttpChannelBuilder builder = OkHttpChannelBuilder
                .forAddress(endpointInfo.host(), endpointInfo.port())
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS);

        if (endpointInfo.usePlaintext()) {
            builder.usePlaintext();
        }

        ManagedChannel channel = builder.build();

        Channel interceptedChannel = ClientInterceptors.intercept(
                channel, new SigningClientInterceptor(signer, Clock.systemUTC()));

        return new ChannelPair(channel, interceptedChannel);
    }

    /**
     * Returns the underlying gRPC channel with signing interceptor applied.
     *
     * @return the intercepted channel that signs all outgoing requests
     */
    public Channel getChannel() {
        return interceptedChannel;
    }

    /**
     * Initiates an orderly shutdown of the client.
     * Existing RPCs will continue, but new RPCs will be rejected.
     */
    public void shutdown() {
        channel.shutdown();
    }

    /**
     * Initiates a forceful shutdown of the client.
     * All RPCs will be cancelled immediately.
     */
    public void shutdownNow() {
        channel.shutdownNow();
    }

    /**
     * Waits for the client to become terminated.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return true if the client terminated, false if the timeout was reached
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return channel.awaitTermination(timeout, unit);
    }

    /**
     * Returns true if the client has been shut down.
     *
     * @return true if shut down
     */
    public boolean isShutdown() {
        return channel.isShutdown();
    }

    /**
     * Returns true if the client has terminated.
     *
     * @return true if terminated
     */
    public boolean isTerminated() {
        return channel.isTerminated();
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

    // --- Endpoint parsing ---

    /**
     * Parsed endpoint information.
     */
    protected record EndpointInfo(String host, int port, boolean usePlaintext) {}

    /**
     * Parses an endpoint string into its components.
     *
     * @param endpoint the endpoint string
     * @return the parsed endpoint information
     */
    protected static EndpointInfo parseEndpoint(String endpoint) {
        String normalizedEndpoint = endpoint;

        // Add scheme if missing for URI parsing
        if (!normalizedEndpoint.contains("://")) {
            normalizedEndpoint = "https://" + normalizedEndpoint;
        }

        try {
            URI uri = new URI(normalizedEndpoint);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("endpoint must have a valid host: " + endpoint);
            }

            boolean usePlaintext = "http".equalsIgnoreCase(uri.getScheme());
            int defaultPort = usePlaintext ? 80 : 443;
            int port = uri.getPort() != -1 ? uri.getPort() : defaultPort;

            return new EndpointInfo(host, port, usePlaintext);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid endpoint format: " + endpoint, e);
        }
    }

    // --- Signing interceptor ---

    /**
     * gRPC client interceptor that signs outgoing requests.
     *
     * <p>CRITICAL: This interceptor serializes the message ONCE and sends the exact
     * same bytes that were signed. This is essential because protobuf serialization
     * is not deterministic - re-serializing a message may produce different bytes.
     *
     * <p>The implementation uses {@link ByteArrayMarshaller} to send pre-serialized
     * bytes, avoiding double-encoding that would break signature verification.
     *
     * <p>This class is thread-safe. Each call to {@link #interceptCall} creates
     * independent state for that specific call.
     */
    static class SigningClientInterceptor implements ClientInterceptor {

        private final Signer signer;
        private final Clock clock;

        /**
         * Creates a new signing interceptor.
         *
         * @param signer the signer to use for signing requests
         * @param clock  the clock to use for timestamp generation
         */
        SigningClientInterceptor(Signer signer, Clock clock) {
            this.signer = signer;
            this.clock = clock;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {

            // Create a method descriptor that accepts raw bytes for the request.
            // This allows us to send pre-serialized bytes without re-encoding.
            MethodDescriptor<byte[], RespT> rawMethod = method.toBuilder(
                    ByteArrayMarshaller.INSTANCE,
                    method.getResponseMarshaller()
            ).build();

            // Create the underlying call with the raw method descriptor
            ClientCall<byte[], RespT> rawCall = next.newCall(rawMethod, callOptions);

            // Extend ClientCall directly instead of ForwardingClientCall to avoid
            // the delegate() issue. ForwardingClientCall requires delegate() to return
            // a ClientCall with matching type parameters, but rawCall is ClientCall<byte[], RespT>
            // while we need to return ClientCall<ReqT, RespT>.
            return new ClientCall<ReqT, RespT>() {

                private Listener<RespT> responseListener;
                private Metadata headers;
                private boolean started = false;
                private int pendingRequests = 0;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Delay start until we have the message and can compute signature
                    this.responseListener = responseListener;
                    this.headers = headers;
                }

                @Override
                public void sendMessage(ReqT message) {
                    // Serialize the message ONCE to get the exact bytes we will sign and send
                    byte[] messageBytes;
                    try (InputStream stream = method.getRequestMarshaller().stream(message)) {
                        messageBytes = stream.readAllBytes();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to serialize message for signing", e);
                    }

                    // Sign the exact bytes we will send
                    long timestampMs = clock.millis();
                    addSignatureHeaders(messageBytes, timestampMs);

                    // Now start the actual call with signed headers
                    if (!started) {
                        rawCall.start(responseListener, headers);
                        started = true;
                        // Flush any pending request() calls that happened before start
                        if (pendingRequests > 0) {
                            rawCall.request(pendingRequests);
                            pendingRequests = 0;
                        }
                    }

                    // CRITICAL: Send the EXACT bytes we signed, not the original message.
                    // This prevents double-serialization which would produce different bytes.
                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    // If no message was sent, start with empty body signature
                    if (!started) {
                        long timestampMs = clock.millis();
                        addSignatureHeaders(new byte[0], timestampMs);
                        rawCall.start(responseListener, headers);
                        started = true;
                        // Flush any pending request() calls
                        if (pendingRequests > 0) {
                            rawCall.request(pendingRequests);
                            pendingRequests = 0;
                        }
                    }
                    rawCall.halfClose();
                }

                @Override
                public void request(int numMessages) {
                    // Buffer requests if the call hasn't started yet
                    if (started) {
                        rawCall.request(numMessages);
                    } else {
                        pendingRequests += numMessages;
                    }
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    rawCall.cancel(message, cause);
                }

                @Override
                public boolean isReady() {
                    return rawCall.isReady();
                }

                @Override
                public void setMessageCompression(boolean enabled) {
                    rawCall.setMessageCompression(enabled);
                }

                @Override
                public Attributes getAttributes() {
                    return rawCall.getAttributes();
                }

                private void addSignatureHeaders(byte[] messageBytes, long timestampMs) {
                    byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);
                    byte[] digest = Keccak256.hash(messageBytes, timestampBytes);
                    SignResult signResult = signer.sign(digest);

                    headers.put(
                            Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER),
                            signResult.getSignatureHex());
                    headers.put(
                            Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER),
                            signResult.getPublicKeyHex());
                    headers.put(
                            Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER),
                            String.valueOf(timestampMs));

                    log.trace("Signed request: timestamp={}, signature={}", timestampMs, signResult.getSignatureHex());
                }
            };
        }
    }
}
