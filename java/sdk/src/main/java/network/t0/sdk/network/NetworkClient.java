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
import java.time.Duration;
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
 * <p><b>Deadlines:</b> every call without a deadline of its own gets one when it is created:
 * {@value #DEFAULT_TIMEOUT_SECONDS} seconds for unary calls by default, none for streaming calls.
 * The {@code create} overloads of the concrete clients set both; {@code stub(timeout, unit)} sets
 * a deadline for the calls of one stub.
 *
 * <p><b>Streaming calls:</b> for client- and server-streaming calls the signature covers only the
 * first request message; later messages are sent as-is. A call starts when its first message is
 * sent, and its {@code isReady()} reports {@code true} until then.
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
     * Default deadline in seconds for unary calls. Streaming calls get no deadline by default.
     */
    protected static final int DEFAULT_TIMEOUT_SECONDS = 15;

    /**
     * The underlying gRPC managed channel.
     */
    protected final ManagedChannel channel;

    /**
     * The channel with the signing and default-deadline interceptors applied.
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
     * @param timeoutSeconds the default deadline in seconds for unary calls; streaming calls get none
     * @return a ChannelPair containing the managed channel and intercepted channel
     * @throws IllegalArgumentException if the endpoint or signer is invalid, or timeoutSeconds is not positive
     */
    protected static ChannelPair createChannel(String endpoint, Signer signer, int timeoutSeconds) {
        return createChannel(endpoint, signer, Duration.ofSeconds(timeoutSeconds), null);
    }

    /**
     * Creates a channel pair for the given endpoint with the signing and default-deadline interceptors.
     *
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer        the signer to use for signing requests
     * @param unaryTimeout  the default deadline for unary calls; must be positive
     * @param streamTimeout the default deadline for streaming calls; {@code null} or zero for none
     * @return a ChannelPair containing the managed channel and intercepted channel
     * @throws IllegalArgumentException if the endpoint or signer is invalid, {@code unaryTimeout} is not
     *                                  positive or {@code streamTimeout} is negative
     */
    protected static ChannelPair createChannel(
            String endpoint, Signer signer, Duration unaryTimeout, Duration streamTimeout) {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be null or empty");
        }
        if (signer == null) {
            throw new IllegalArgumentException("signer must not be null");
        }
        // Validates the timeouts before a channel exists that would have to be shut down.
        DefaultDeadlineInterceptor deadlines = new DefaultDeadlineInterceptor(unaryTimeout, streamTimeout);

        EndpointInfo endpointInfo = parseEndpoint(endpoint);

        OkHttpChannelBuilder builder = OkHttpChannelBuilder
                .forAddress(endpointInfo.host(), endpointInfo.port())
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS);

        if (endpointInfo.usePlaintext()) {
            builder.usePlaintext();
        }

        ManagedChannel channel = builder.build();

        // The last interceptor runs first: the deadline is set before the signing interceptor
        // creates the underlying call.
        Channel interceptedChannel = ClientInterceptors.intercept(
                channel, new SigningClientInterceptor(signer, Clock.systemUTC()), deadlines);

        return new ChannelPair(channel, interceptedChannel);
    }

    /**
     * Returns the underlying gRPC channel with the signing and default-deadline interceptors applied.
     *
     * <p>Calls made on it directly, for example with a hand-built {@link MethodDescriptor}, are
     * signed and get the default deadlines like the stub's calls.
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
     * <p>CRITICAL: This interceptor serializes each message ONCE and sends the exact
     * same bytes that were signed. This is essential because protobuf serialization
     * is not deterministic - re-serializing a message may produce different bytes.
     *
     * <p>The implementation uses {@link ByteArrayMarshaller} to send pre-serialized
     * bytes, avoiding double-encoding that would break signature verification.
     *
     * <p><b>What is signed:</b> the first request message only - the whole request of a unary or
     * server-streaming call, the first message of a client stream. The interceptor sits above the
     * gRPC framer, so it signs the marshalled message without the 5-byte gRPC prefix; the network
     * accepts that through its unframed fallback. Later messages of a stream are sent as-is: they
     * are not signed and do not touch the headers, which went out when the call started. A call
     * half-closed before any message signs empty bytes. Bidirectional streams are not supported.
     *
     * <p><b>Deferred start:</b> the underlying call starts when the first message (or
     * {@code halfClose()}) arrives, because the signature headers must be complete before it
     * starts. Until then:
     * <ul>
     *   <li>{@code isReady()} returns {@code true}, so isReady-gated senders such as
     *       {@code BlockingClientCall.write} send the first message instead of waiting on a call
     *       that cannot start without it. {@code onReady()} is first delivered after that message,
     *       so a sender driven only by onReady callbacks has to send its first message directly.</li>
     *   <li>{@code request()} is buffered and passed on when the call starts.</li>
     *   <li>{@code cancel()} starts the underlying call and cancels it at once: a call that was
     *       never started never notifies its listener, so without this the listener would not get
     *       {@code onClose(CANCELLED)}.</li>
     * </ul>
     *
     * <p>This class is thread-safe. Each call to {@link #interceptCall} creates
     * independent state for that specific call. As for any {@link ClientCall}, the methods of the
     * returned call are expected to be called serially, except {@code request()}, which may be
     * called from any thread.
     */
    static class SigningClientInterceptor implements ClientInterceptor {

        private static final Metadata.Key<String> SIGNATURE_KEY =
                Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> PUBLIC_KEY_KEY =
                Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> SIGNATURE_TIMESTAMP_KEY =
                Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER);

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

                // Guards the hand-off of buffered request() calls, which may come from any thread.
                private final Object lock = new Object();
                private Listener<RespT> responseListener;
                private Metadata headers;
                private volatile boolean started = false;
                private int pendingRequests = 0;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Delay start until we have the first message and can compute the signature
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

                    if (!started) {
                        // First message: sign the exact bytes we will send, then start the
                        // actual call with the signed headers.
                        addSignatureHeaders(messageBytes, clock.millis());
                        startRawCall();
                    }
                    // Later messages of a stream are sent as-is: the signature covers the first
                    // message only, and the headers already went out when the call started.

                    // CRITICAL: Send the EXACT bytes we signed, not the original message.
                    // This prevents double-serialization which would produce different bytes.
                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    // If no message was sent, start with empty body signature
                    if (!started) {
                        addSignatureHeaders(new byte[0], clock.millis());
                        startRawCall();
                    }
                    rawCall.halfClose();
                }

                @Override
                public void request(int numMessages) {
                    // Buffer requests if the call hasn't started yet
                    if (!started) {
                        synchronized (lock) {
                            if (!started) {
                                pendingRequests += numMessages;
                                return;
                            }
                        }
                    }
                    rawCall.request(numMessages);
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    // An unstarted call never calls its listener, so cancelling it alone would
                    // leave the listener without onClose. Start it first (nothing was signed yet,
                    // and the cancellation follows at once) so the listener gets onClose(CANCELLED)
                    // on the call's executor, as for any other cancelled call.
                    if (!started && responseListener != null) {
                        startRawCall();
                    }
                    rawCall.cancel(message, cause);
                }

                @Override
                public boolean isReady() {
                    // Before the first message the call cannot start, so it cannot become ready:
                    // report ready so an isReady-gated sender sends that message.
                    return !started || rawCall.isReady();
                }

                @Override
                public void setMessageCompression(boolean enabled) {
                    rawCall.setMessageCompression(enabled);
                }

                @Override
                public Attributes getAttributes() {
                    return rawCall.getAttributes();
                }

                private void startRawCall() {
                    rawCall.start(responseListener, headers);
                    int requests;
                    synchronized (lock) {
                        started = true;
                        requests = pendingRequests;
                        pendingRequests = 0;
                    }
                    // Flush any pending request() calls that happened before start
                    if (requests > 0) {
                        rawCall.request(requests);
                    }
                }

                // Called once, before the call starts: the headers are handed to the underlying
                // call at start and must not change afterwards.
                private void addSignatureHeaders(byte[] messageBytes, long timestampMs) {
                    byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);
                    byte[] digest = Keccak256.hash(messageBytes, timestampBytes);
                    SignResult signResult = signer.sign(digest);

                    // Metadata.put appends: replace, so each header has exactly one value.
                    headers.discardAll(SIGNATURE_KEY);
                    headers.discardAll(PUBLIC_KEY_KEY);
                    headers.discardAll(SIGNATURE_TIMESTAMP_KEY);
                    headers.put(SIGNATURE_KEY, signResult.getSignatureHex());
                    headers.put(PUBLIC_KEY_KEY, signResult.getPublicKeyHex());
                    headers.put(SIGNATURE_TIMESTAMP_KEY, String.valueOf(timestampMs));

                    log.trace("Signed request: timestamp={}, signature={}", timestampMs, signResult.getSignatureHex());
                }
            };
        }
    }

    // --- Default deadlines ---

    /**
     * gRPC client interceptor that gives each call a deadline unless it already has one.
     *
     * <p>Unary calls get the unary timeout. Streaming calls (client, server and bidi streaming,
     * and calls of unknown type) get the stream timeout, or no deadline when none is set: a stream
     * can run as long as an upload or a download takes. The deadline is computed when each call is
     * created, never once for a stored stub. A deadline the call already has, for example from
     * {@code stub(timeout, unit)} or {@code withDeadlineAfter} on a stub, is kept.
     *
     * <p>This class is thread-safe: it is immutable.
     */
    static final class DefaultDeadlineInterceptor implements ClientInterceptor {

        private final Duration unaryTimeout;
        private final Duration streamTimeout; // null: streaming calls get no deadline

        /**
         * Creates a new default-deadline interceptor.
         *
         * @param unaryTimeout  the deadline for unary calls; must be positive
         * @param streamTimeout the deadline for streaming calls; {@code null} or zero for none
         * @throws IllegalArgumentException if {@code unaryTimeout} is not positive or
         *                                  {@code streamTimeout} is negative
         */
        DefaultDeadlineInterceptor(Duration unaryTimeout, Duration streamTimeout) {
            if (unaryTimeout == null || unaryTimeout.isNegative() || unaryTimeout.isZero()) {
                throw new IllegalArgumentException("unaryTimeout must be positive");
            }
            if (streamTimeout != null && streamTimeout.isNegative()) {
                throw new IllegalArgumentException("streamTimeout must not be negative");
            }
            this.unaryTimeout = unaryTimeout;
            this.streamTimeout = streamTimeout == null || streamTimeout.isZero() ? null : streamTimeout;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {
            if (callOptions.getDeadline() == null) {
                Duration timeout = method.getType() == MethodDescriptor.MethodType.UNARY
                        ? unaryTimeout
                        : streamTimeout;
                if (timeout != null) {
                    callOptions = callOptions.withDeadlineAfter(saturatedNanos(timeout), TimeUnit.NANOSECONDS);
                }
            }
            return next.newCall(method, callOptions);
        }

        private static long saturatedNanos(Duration duration) {
            try {
                return duration.toNanos();
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE; // Deadline clamps it further
            }
        }
    }
}
