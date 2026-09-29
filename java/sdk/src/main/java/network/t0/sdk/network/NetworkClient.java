package network.t0.sdk.network;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
 * <p>Unary calls get a default deadline of {@value #DEFAULT_TIMEOUT_SECONDS} seconds, streaming calls
 * none. Client- and server-streaming calls are signed over their first request message only;
 * bidirectional streams fail with {@code UNIMPLEMENTED}. See {@code docs/java/STREAMING.md}.
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
     * @param interceptedChannel the channel with the signing and default-deadline interceptors applied
     */
    protected NetworkClient(ManagedChannel channel, Channel interceptedChannel) {
        this.channel = channel;
        this.interceptedChannel = interceptedChannel;
    }

    /**
     * Result of creating a channel pair.
     *
     * @param channel            the underlying managed channel
     * @param interceptedChannel the channel with the signing and default-deadline interceptors applied
     */
    protected record ChannelPair(ManagedChannel channel, Channel interceptedChannel) {}

    /**
     * Creates a channel pair for the given endpoint with the signing and default-deadline interceptors.
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
        // Validates the timeouts before there is a channel to shut down.
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

        // The last listed runs first: the deadline is set before the signing interceptor creates the call.
        Channel interceptedChannel = ClientInterceptors.intercept(
                channel, new SigningClientInterceptor(signer, Clock.systemUTC()), deadlines);

        return new ChannelPair(channel, interceptedChannel);
    }

    /**
     * Returns the underlying gRPC channel with the signing and default-deadline interceptors applied.
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
     * <p>Only the first request message is signed, without its 5-byte gRPC prefix (this interceptor
     * sits above the framer); later stream messages are sent unsigned. The underlying call starts on
     * that first message, since the headers must be complete by then, or unsigned when it is cancelled
     * or its deadline or context ends first. Bidirectional streams are refused with
     * {@code UNIMPLEMENTED}. See {@code docs/java/STREAMING.md}.
     *
     * <p>This class is thread-safe. Each call to {@link #interceptCall} creates
     * independent state for that specific call.
     */
    static class SigningClientInterceptor implements ClientInterceptor {

        private static final Metadata.Key<String> SIGNATURE_KEY =
                Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> PUBLIC_KEY_KEY =
                Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER);
        private static final Metadata.Key<String> SIGNATURE_TIMESTAMP_KEY =
                Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER);

        // Deadlines of calls still waiting for their first message; grpc keeps the ones of started calls.
        private static final ScheduledExecutorService DEADLINE_TIMER = newDeadlineTimer();

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

            // The network accepts no bidi streams, and with the deferred start a bidi caller that
            // awaits a response before sending would hang: fail fast.
            if (method.getType() == MethodDescriptor.MethodType.BIDI_STREAMING) {
                return new BidiNotSupportedCall<>();
            }

            // Create a method descriptor that accepts raw bytes for the request.
            // This allows us to send pre-serialized bytes without re-encoding.
            MethodDescriptor<byte[], RespT> rawMethod = method.toBuilder(
                    ByteArrayMarshaller.INSTANCE,
                    method.getResponseMarshaller()
            ).build();

            // Create the underlying call with the raw method descriptor
            ClientCall<byte[], RespT> rawCall = next.newCall(rawMethod, callOptions);
            // The context rawCall was created in, which it watches from its start.
            Context context = Context.current();

            // Extend ClientCall directly instead of ForwardingClientCall to avoid
            // the delegate() issue. ForwardingClientCall requires delegate() to return
            // a ClientCall with matching type parameters, but rawCall is ClientCall<byte[], RespT>
            // while we need to return ClientCall<ReqT, RespT>.
            return new ClientCall<ReqT, RespT>() {

                // Guards the start of rawCall and the hand-off of request() calls made before it. The
                // first of sendMessage, halfClose and cancel to find the call unstarted claims the start
                // (starting); the others wait until it has started. cancel() may come from another thread.
                private final Object lock = new Object();
                private Listener<RespT> responseListener;
                private Metadata headers;
                private volatile boolean started = false;
                private boolean starting = false;
                private int pendingRequests = 0;
                private ScheduledFuture<?> deadlineTimer;
                private final Context.CancellationListener contextListener = cancelled -> startUnsigned();

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Delay start until we have the first message and can compute the signature.
                    // grpc enforces the deadline and the context only from rawCall's start: until then,
                    // whichever ends first starts rawCall unsigned, and grpc fails it without sending.
                    synchronized (lock) {
                        this.responseListener = responseListener;
                        this.headers = headers;
                        Deadline deadline = callOptions.getDeadline();
                        if (deadline != null) {
                            deadlineTimer = deadline.runOnExpiration(this::startUnsigned, DEADLINE_TIMER);
                        }
                    }
                    context.addListener(contextListener, Runnable::run);
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

                    // Only the first message is signed; later stream messages go out as-is.
                    if (claimStart(messageBytes)) {
                        startRawCall();
                    }

                    // CRITICAL: Send the EXACT bytes we signed, not the original message.
                    // This prevents double-serialization which would produce different bytes.
                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    // If no message was sent, start with empty body signature
                    if (claimStart(new byte[0])) {
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
                    // An unstarted call never notifies its listener: start it (unsigned) so the
                    // listener still gets onClose(CANCELLED). Before start() there is no listener.
                    boolean startCalled;
                    synchronized (lock) {
                        startCalled = responseListener != null;
                    }
                    if (startCalled && claimStart(null)) {
                        startRawCall();
                    }
                    rawCall.cancel(message, cause);
                }

                @Override
                public boolean isReady() {
                    // Unstarted, the call can never become ready: report ready so that
                    // isReady-gated senders send the first message, which starts it.
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

                /**
                 * Returns true if the caller is to start rawCall, with the headers signed over
                 * {@code signed} (unsigned if null). Otherwise rawCall has started, if need be after
                 * waiting for the thread that claimed it.
                 */
                private boolean claimStart(byte[] signed) {
                    synchronized (lock) {
                        if (started) {
                            return false;
                        }
                        if (!starting) {
                            if (signed != null) {
                                addSignatureHeaders(signed, clock.millis());
                            }
                            starting = true;
                            return true;
                        }
                        while (!started) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw Status.CANCELLED
                                        .withDescription("interrupted while waiting for the call to start")
                                        .withCause(e)
                                        .asRuntimeException();
                            }
                        }
                        return false;
                    }
                }

                // The deadline or the context ended before the first message; a started call has its own.
                private void startUnsigned() {
                    synchronized (lock) {
                        if (started || starting) {
                            return;
                        }
                        starting = true;
                    }
                    startRawCall();
                }

                // Outside the lock: request() from another thread must not block on it meanwhile.
                private void startRawCall() {
                    Listener<RespT> listener;
                    Metadata startHeaders;
                    ScheduledFuture<?> timer;
                    synchronized (lock) {
                        listener = responseListener;
                        startHeaders = headers;
                        timer = deadlineTimer;
                    }
                    if (timer != null) {
                        timer.cancel(false);
                    }
                    context.removeListener(contextListener);
                    int requests;
                    try {
                        rawCall.start(listener, startHeaders);
                    } finally {
                        synchronized (lock) {
                            started = true;
                            starting = false;
                            requests = pendingRequests;
                            pendingRequests = 0;
                            lock.notifyAll();
                        }
                    }
                    // Flush any pending request() calls that happened before start
                    if (requests > 0) {
                        rawCall.request(requests);
                    }
                }

                // Under the lock, before start: once started, the headers belong to the transport.
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

        /**
         * Handed out for bidi streams: closes its listener on start, as grpc-java calls that fail
         * to start do, and ignores everything else.
         */
        private static final class BidiNotSupportedCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                responseListener.onClose(
                        Status.UNIMPLEMENTED.withDescription("bidirectional streams are not supported"),
                        new Metadata());
            }

            @Override
            public void request(int numMessages) {
            }

            @Override
            public void cancel(String message, Throwable cause) {
            }

            @Override
            public void halfClose() {
            }

            @Override
            public void sendMessage(ReqT message) {
            }

            @Override
            public boolean isReady() {
                return false;
            }
        }
    }

    private static ScheduledExecutorService newDeadlineTimer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "t0-network-client-deadline");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    // --- Default deadlines ---

    /**
     * gRPC client interceptor that gives each call without a deadline one when the call is created:
     * the unary timeout for unary calls, the stream timeout (possibly none) for all others.
     * See {@code docs/java/STREAMING.md}.
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
