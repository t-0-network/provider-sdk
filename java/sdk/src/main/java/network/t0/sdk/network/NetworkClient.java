package network.t0.sdk.network;

import network.t0.sdk.common.Messages;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.okhttp.OkHttpChannelBuilder;
import network.t0.sdk.common.Headers;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.DigestSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
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
 * <p>The signer may be any {@link network.t0.sdk.crypto.DigestSigner} whose {@code sign()} returns
 * quickly and does not block on network I/O (it runs while the call's lock is held); {@code Signer}
 * holds the key in memory.
 *
 * <p>Unary calls get a default deadline of 15 seconds, client- and server-streaming calls one of
 * 5 minutes, counted from when the call is created. A call starts with its first message, so a deadline
 * that passed before then fails the call when that message, or the half-close, comes. A deadline the
 * caller sets on a call or on its {@link Context} replaces the default, shorter or longer. Streaming
 * calls are signed over their first request message only; bidirectional streams and calls with a
 * non-identity compressor fail with {@code UNIMPLEMENTED}. See {@code docs/STREAMING.md}.
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

    /** The base URL used when the caller passes {@code null}. */
    public static final String DEFAULT_BASE_URL = "https://api.t-0.network";

    /**
     * The base URL used when the caller passes {@code null}.
     *
     * @deprecated Use {@link #DEFAULT_BASE_URL}.
     */
    @Deprecated
    protected static final String DEFAULT_ENDPOINT = DEFAULT_BASE_URL;

    /**
     * Default deadline for unary calls.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Default deadline for client- and server-streaming calls, counted from when the call is created.
     */
    public static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(5);

    /** The longest timeout and stream timeout a client accepts. */
    public static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

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
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or {@code null} for "https://api.t-0.network";
     *                      a path in it prefixes every call (see {@link #parseBaseUrl(String)})
     * @param signer        the signer to use for signing requests
     * @param timeout       the default deadline for unary calls
     * @param streamTimeout the default deadline for client- and server-streaming calls
     * @return a ChannelPair containing the managed channel and intercepted channel
     * @throws IllegalArgumentException if the endpoint or signer is invalid, or a timeout is not a positive
     *                                  duration of at most 2147483647 ms
     */
    protected static ChannelPair createChannel(
            String endpoint, DigestSigner signer, Duration timeout, Duration streamTimeout) {
        // Everything is checked before there is a channel to shut down.
        BaseUrl baseUrl = parseBaseUrl(endpoint);
        EndpointInfo endpointInfo = baseUrl.endpoint();
        if (signer == null) {
            throw new IllegalArgumentException(Messages.SIGNER_NULL);
        }
        DefaultDeadlineInterceptor deadlines = new DefaultDeadlineInterceptor(timeout, streamTimeout);

        OkHttpChannelBuilder builder = OkHttpChannelBuilder
                .forAddress(endpointInfo.host(), endpointInfo.port());

        if (endpointInfo.usePlaintext()) {
            builder.usePlaintext();
        }

        ManagedChannel channel = builder.build();

        // Below the other interceptors, so that they see the method as generated.
        Channel prefixed = baseUrl.pathPrefix().isEmpty()
                ? channel
                : ClientInterceptors.intercept(channel, new PathPrefixInterceptor(baseUrl.pathPrefix()));
        // The last listed runs first: the deadline is set before the signing interceptor creates the call.
        Channel interceptedChannel = ClientInterceptors.intercept(
                prefixed, new SigningClientInterceptor(signer, Clock.systemUTC()), deadlines);

        return new ChannelPair(channel, interceptedChannel);
    }

    /**
     * Returns {@code value} if it is a positive duration of at most {@link #MAX_TIMEOUT}.
     *
     * @throws IllegalArgumentException with {@code message} otherwise
     */
    static Duration checkTimeout(String message, Duration value) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
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
     * A checked base URL: the endpoint, and the path that prefixes every call.
     *
     * @param pathPrefix the path without its leading and trailing {@code /} (e.g. {@code "v1"} or
     *                   {@code "a/b"}), or {@code ""} for none
     */
    record BaseUrl(EndpointInfo endpoint, String pathPrefix) {}

    /**
     * Parses a base URL into its components.
     *
     * @param endpoint the endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or
     *                 {@code null} for {@value #DEFAULT_BASE_URL}; see {@link #parseBaseUrl(String)}
     * @return the parsed endpoint information
     * @throws IllegalArgumentException if the base URL is empty or not valid
     */
    protected static EndpointInfo parseEndpoint(String endpoint) {
        return parseBaseUrl(endpoint).endpoint();
    }

    /**
     * Parses and checks a base URL.
     *
     * @param endpoint an http or https URL with a host, an optional port from 1 to 65535 and an optional
     *                 path, and no user info, query or fragment; without {@code ://} it is read as https
     *                 ({@code "api.t-0.network:443"}); {@code null} for {@value #DEFAULT_BASE_URL}. The path
     *                 prefixes every call ({@code https://host/v1} calls {@code https://host/v1/<service>/<method>})
     * @throws IllegalArgumentException if the base URL is empty or not valid
     */
    static BaseUrl parseBaseUrl(String endpoint) {
        if (endpoint == null) {
            endpoint = DEFAULT_BASE_URL;
        }
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException(Messages.BASE_URL_NOT_SET);
        }
        if (!endpoint.contains("://")) {
            endpoint = "https://" + endpoint;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw invalidBaseUrl();
        }
        boolean usePlaintext = "http".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort();
        if ((!usePlaintext && !"https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || port == 0 || port > 65535) {
            throw invalidBaseUrl();
        }
        if (port < 0) {
            port = usePlaintext ? 80 : 443;
        }
        // The path without its leading '/' and one trailing '/'.
        String path = uri.getRawPath();
        int end = path.endsWith("/") ? path.length() - 1 : path.length();
        String pathPrefix = end > 0 ? path.substring(1, end) : "";
        return new BaseUrl(new EndpointInfo(uri.getHost(), port, usePlaintext), pathPrefix);
    }

    private static IllegalArgumentException invalidBaseUrl() {
        return new IllegalArgumentException(Messages.BASE_URL_NOT_VALID);
    }

    // --- Path prefix interceptor ---

    /**
     * gRPC client interceptor that sends each call to {@code /<prefix>/<service>/<method>}: grpc-java sends
     * {@code "/" + fullMethodName} as the {@code :path}, and ignores any path in the channel's address.
     */
    static final class PathPrefixInterceptor implements ClientInterceptor {

        private final String prefix;

        /**
         * @param prefix the path without its leading and trailing {@code /}, such as {@code "v1"}
         */
        PathPrefixInterceptor(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return next.newCall(
                    method.toBuilder().setFullMethodName(prefix + "/" + method.getFullMethodName()).build(),
                    callOptions);
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
     * that first message, since the headers must be complete by then, or on a half-close without one,
     * signed over empty bytes. A call cancelled before then sends nothing (see {@code docs/STREAMING.md}).
     * Bidirectional streams and calls with a non-identity compressor are refused with
     * {@code UNIMPLEMENTED} before anything is sent; a call whose signer fails ends with
     * {@code INTERNAL} "signing the request failed: &lt;cause&gt;", and sends nothing.
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

        private final DigestSigner signer;
        private final Clock clock;

        /**
         * Creates a new signing interceptor.
         *
         * @param signer the signer to use for signing requests
         * @param clock  the clock to use for timestamp generation
         */
        SigningClientInterceptor(DigestSigner signer, Clock clock) {
            this.signer = signer;
            this.clock = clock;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {

            // Where this wrapper calls the listener on its own: the call's executor, or in place.
            Executor executor = callOptions.getExecutor() != null ? callOptions.getExecutor() : Runnable::run;

            // The network accepts no bidi streams, and with the deferred start a bidi caller that
            // awaits a response before sending would hang: fail fast.
            if (method.getType() == MethodDescriptor.MethodType.BIDI_STREAMING) {
                return new RefusedCall<>(Status.UNIMPLEMENTED.withDescription(Messages.BIDI_NOT_SUPPORTED),
                        executor);
            }
            // The signature covers the message as serialized here, and a non-identity compressor would
            // change the bytes on the wire after that, so the network would refuse the call: refuse it first.
            String compressor = callOptions.getCompressor();
            if (compressor != null && !"identity".equals(compressor)) {
                return new RefusedCall<>(Status.UNIMPLEMENTED.withDescription(Messages.COMPRESSED_NOT_SUPPORTED),
                        executor);
            }

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

                // Held from rawCall.start() until the signed first message is sent, so that no message
                // from another thread reaches the wire before it.
                private final Object lock = new Object();
                private Listener<RespT> responseListener;
                private Metadata headers;
                private boolean started; // rawCall started, with the first message sent if there is one
                private boolean cancelled; // before rawCall started
                private boolean signingFailed; // the call was closed before rawCall started, and sent nothing
                private int pendingRequests;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Delay start until we have the first message and can compute the signature.
                    synchronized (lock) {
                        this.responseListener = responseListener;
                        this.headers = headers;
                    }
                    // The call is ready for its first message, and only that message starts rawCall: a
                    // sender that sends only on onReady needs this one, or both wait for ever.
                    executor.execute(Context.current().wrap(() -> {
                        synchronized (lock) {
                            if (started || cancelled) {
                                return;
                            }
                        }
                        responseListener.onReady();
                    }));
                }

                @Override
                public void sendMessage(ReqT message) {
                    // Serialize the message ONCE to get the exact bytes we will sign and send
                    byte[] messageBytes;
                    try (InputStream stream = method.getRequestMarshaller().stream(message)) {
                        messageBytes = stream.readAllBytes();
                    } catch (IOException e) {
                        throw new RuntimeException(Messages.MESSAGE_SERIALIZATION_FAILED, e);
                    }

                    // CRITICAL: Send the EXACT bytes we signed, not the original message.
                    // This prevents double-serialization which would produce different bytes.
                    // Only the first message is signed; later stream messages go out as-is.
                    Status failure;
                    synchronized (lock) {
                        if (signingFailed) {
                            return;
                        }
                        if (started || cancelled) {
                            failure = null;
                        } else {
                            failure = startSigned(messageBytes);
                            if (failure == null) {
                                rawCall.sendMessage(messageBytes);
                                started = true;
                                return;
                            }
                        }
                    }
                    if (failure != null) {
                        responseListener.onClose(failure, new Metadata());
                        return;
                    }
                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    Status failure = null;
                    synchronized (lock) {
                        if (signingFailed) {
                            return;
                        }
                        if (!started && !cancelled) {
                            failure = startSigned(new byte[0]); // no message: signed over empty bytes
                            started = failure == null;
                        }
                    }
                    if (failure != null) {
                        responseListener.onClose(failure, new Metadata());
                        return;
                    }
                    rawCall.halfClose();
                }

                @Override
                public void request(int numMessages) {
                    synchronized (lock) {
                        if (!started) {
                            pendingRequests += numMessages; // passed on when rawCall starts
                            return;
                        }
                    }
                    rawCall.request(numMessages);
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    Listener<RespT> unstarted = null;
                    synchronized (lock) {
                        if (!started && !cancelled && !signingFailed) {
                            cancelled = true;
                            unstarted = responseListener; // null before start()
                        }
                    }
                    rawCall.cancel(message, cause);
                    // rawCall never started, so it sent nothing and will not close the listener: close it here.
                    if (unstarted != null) {
                        unstarted.onClose(Status.CANCELLED.withDescription(message).withCause(cause), new Metadata());
                    }
                }

                @Override
                public boolean isReady() {
                    synchronized (lock) {
                        if (!started) {
                            // Ready for the first message, which starts the call.
                            return !cancelled && !signingFailed;
                        }
                    }
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

                // Under the lock: starts rawCall with the headers signed over `signed`, then passes on the
                // request() calls made before. If the signer fails, sends nothing and returns the status
                // to close the call with, outside the lock: Internal, as the failure is not transient.
                private Status startSigned(byte[] signed) {
                    try {
                        addSignatureHeaders(signed, clock.millis());
                    } catch (RuntimeException e) {
                        signingFailed = true;
                        return Status.INTERNAL.withDescription(String.format(Messages.SIGNING_FAILED, e.getMessage()))
                                .withCause(e);
                    }
                    rawCall.start(responseListener, headers);
                    if (pendingRequests > 0) {
                        rawCall.request(pendingRequests);
                    }
                    return null;
                }

                // Under the lock, before start: once started, the headers belong to the transport.
                private void addSignatureHeaders(byte[] messageBytes, long timestampMs) {
                    byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);
                    byte[] digest = Keccak256.hash(messageBytes, timestampBytes);
                    SignResult signResult = signer.sign(digest);
                    if (signResult == null) {
                        // No signature: it fails the signature check, as in every SDK.
                        throw new IllegalArgumentException(Messages.SIGNER_SIGNATURE_INVALID);
                    }

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
         * Handed out for calls the SDK does not send: closes its listener with {@code status} once started,
         * before anything is sent, and ignores everything else.
         */
        private static final class RefusedCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

            private final Status status;
            private final Executor executor;
            private final Context context = Context.current();

            RefusedCall(Status status, Executor executor) {
                this.status = status;
                this.executor = executor;
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                executor.execute(context.wrap(() -> responseListener.onClose(status, new Metadata())));
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

    // --- Default deadlines ---

    /**
     * gRPC client interceptor that gives each call a deadline when it is created, unless the caller set
     * one on the call or on its {@link Context}: the timeout for unary calls, the stream timeout for all
     * others. The caller's own deadline replaces the default, shorter or longer.
     * See {@code docs/STREAMING.md}.
     */
    static final class DefaultDeadlineInterceptor implements ClientInterceptor {

        private final Duration timeout;
        private final Duration streamTimeout;

        /**
         * Creates a new default-deadline interceptor.
         *
         * @param timeout       the deadline for unary calls
         * @param streamTimeout the deadline for client- and server-streaming calls
         * @throws IllegalArgumentException if a timeout is not a positive duration of at most 2147483647 ms
         */
        DefaultDeadlineInterceptor(Duration timeout, Duration streamTimeout) {
            this.timeout = checkTimeout(Messages.TIMEOUT_NOT_VALID, timeout);
            this.streamTimeout = checkTimeout(Messages.STREAM_TIMEOUT_NOT_VALID, streamTimeout);
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {
            // grpc applies the earlier of the two deadlines, so a default set next to a longer Context
            // deadline would cut the caller's deadline short.
            if (callOptions.getDeadline() == null && Context.current().getDeadline() == null) {
                Duration deadline = method.getType() == MethodDescriptor.MethodType.UNARY ? timeout : streamTimeout;
                callOptions = callOptions.withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS);
            }
            return next.newCall(method, callOptions);
        }
    }
}
