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
import io.grpc.SynchronizationContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
 * 5 minutes, which includes the wait for the first message. A deadline the caller sets on a call or on
 * its {@link Context} replaces the default, shorter or longer. Streaming calls are signed over their
 * first request message only; bidirectional streams fail with {@code UNIMPLEMENTED}.
 * See {@code docs/STREAMING.md}.
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
    protected static final String DEFAULT_ENDPOINT = "https://api.t-0.network";

    /**
     * Default deadline for unary calls.
     */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Default deadline for client- and server-streaming calls, including the wait for the first message.
     */
    protected static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(5);

    /** The longest timeout accepted, 2^31 - 1 ms (about 24.8 days). */
    static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

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
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or {@code null} for "https://api.t-0.network"
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
        EndpointInfo endpointInfo = parseEndpoint(endpoint);
        if (signer == null) {
            throw new IllegalArgumentException("signer must not be null");
        }
        DefaultDeadlineInterceptor deadlines = new DefaultDeadlineInterceptor(timeout, streamTimeout);

        OkHttpChannelBuilder builder = OkHttpChannelBuilder
                .forAddress(endpointInfo.host(), endpointInfo.port())
                // Not more often than grpc servers allow by default (every 5 min), or they close the connection.
                .keepAliveTime(5, TimeUnit.MINUTES)
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
     * Returns {@code value} if it is a positive duration of at most {@link #MAX_TIMEOUT}.
     *
     * @throws IllegalArgumentException otherwise, naming {@code option}
     */
    static Duration checkTimeout(String option, Duration value) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    option + " must be a positive duration of at most " + MAX_TIMEOUT.toMillis() + " ms");
        }
        return value;
    }

    /**
     * Checks a per-call timeout as {@link #checkTimeout(String, Duration)} does.
     *
     * @throws IllegalArgumentException if {@code unit} is null or the timeout is out of range
     */
    static void checkTimeout(String option, long value, TimeUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        // toNanos saturates, so a huge value stays above the bound instead of overflowing.
        checkTimeout(option, value <= 0 ? Duration.ZERO : Duration.ofNanos(unit.toNanos(value)));
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

    // A host is an IPv4 address, an IPv6 address in brackets, or a name: labels of letters, digits and
    // inner '-', joined by '.', the last one starting with a letter.
    // Octets without leading zeros ("01" could be read as octal).
    private static final Pattern IPV4 = Pattern.compile("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}");
    private static final Pattern HOST_NAME = Pattern.compile(
            "([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)*[A-Za-z]([A-Za-z0-9-]*[A-Za-z0-9])?");
    private static final Pattern IPV6_LITERAL = Pattern.compile("\\[[0-9A-Fa-f:.]+]");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

    /**
     * Parses a base URL into its components.
     *
     * @param endpoint the endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"): a host, an
     *                 optional port from 1 to 65535 and an optional trailing {@code /}, with no path, query
     *                 or fragment; {@code null} for {@value #DEFAULT_ENDPOINT}
     * @return the parsed endpoint information
     * @throws IllegalArgumentException if the base URL is empty or not valid
     */
    protected static EndpointInfo parseEndpoint(String endpoint) {
        if (endpoint == null) {
            endpoint = DEFAULT_ENDPOINT;
        }
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException("base URL is not set");
        }
        // A value without "://" is read as https, so "host" and "host:port" work as they always did.
        if (!endpoint.contains("://")) {
            endpoint = "https://" + endpoint;
        }
        int schemeEnd = endpoint.indexOf("://");
        String scheme = schemeEnd < 0 ? "" : endpoint.substring(0, schemeEnd);
        boolean usePlaintext = "http".equalsIgnoreCase(scheme);
        if (!usePlaintext && !"https".equalsIgnoreCase(scheme)) {
            throw invalidBaseUrl();
        }
        // Calls go to <base URL>/<service>/<method>, so a path, query or fragment could only be dropped:
        // after one trailing '/', whatever the host and port patterns do not match is refused.
        String authority = endpoint.substring(schemeEnd + 3);
        if (authority.endsWith("/")) {
            authority = authority.substring(0, authority.length() - 1);
        }
        // The port follows the last ':' outside an IPv6 literal's brackets.
        int colon = authority.lastIndexOf(':');
        if (colon < authority.lastIndexOf(']')) {
            colon = -1;
        }
        String host = colon < 0 ? authority : authority.substring(0, colon);
        boolean ipv4 = IPV4.matcher(host).matches();
        if (ipv4) {
            for (String octet : host.split("\\.")) {
                if (Integer.parseInt(octet) > 255) {
                    throw invalidBaseUrl();
                }
            }
        } else if (!HOST_NAME.matcher(host).matches() && !IPV6_LITERAL.matcher(host).matches()) {
            throw invalidBaseUrl();
        }
        int port = usePlaintext ? 80 : 443;
        if (colon >= 0) {
            String digits = authority.substring(colon + 1);
            if (!PORT.matcher(digits).matches()) {
                throw invalidBaseUrl();
            }
            port = Integer.parseInt(digits);
            if (port < 1 || port > 65535) {
                throw invalidBaseUrl();
            }
        }
        // The check grpc makes when it builds the channel ("[:::]", "a..b", "-foo" fail it): a host it
        // cannot take is refused here with our message, not later with grpc's.
        try {
            new URI(null, null, host, port, null, null, null);
        } catch (URISyntaxException e) {
            throw invalidBaseUrl();
        }
        return new EndpointInfo(host, port, usePlaintext);
    }

    private static IllegalArgumentException invalidBaseUrl() {
        return new IllegalArgumentException("base URL is not valid");
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
     * or its deadline or context ends first (see {@code docs/STREAMING.md}). Bidirectional streams and
     * calls with a compressor are refused with {@code UNIMPLEMENTED} before anything is sent.
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

        // Deadlines of calls still waiting for their first message; a started call enforces its own.
        // One thread for every call in the JVM, so it only hands the start on to CALLBACK_EXECUTOR.
        private static final ScheduledExecutorService DEADLINE_TIMER = newDeadlineTimer();
        // Runs the first onReady of calls whose CallOptions have no executor, and the unsigned start of
        // calls whose deadline or context ends first. That start may run the caller's listener inline,
        // which must not hold up the shared timer or the thread that cancelled the context.
        private static final ExecutorService CALLBACK_EXECUTOR = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "t0-network-client-callback");
            thread.setDaemon(true);
            return thread;
        });

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

            // The network accepts no bidi streams, and with the deferred start a bidi caller that
            // awaits a response before sending would hang: fail fast.
            if (method.getType() == MethodDescriptor.MethodType.BIDI_STREAMING) {
                return new RefusedCall<>(Status.UNIMPLEMENTED.withDescription("bidirectional streams are not supported"),
                        callOptions);
            }
            // The signature covers the message as serialized here, and a compressor would change the
            // bytes on the wire after that, so the network would refuse the call: refuse it first.
            String compressor = callOptions.getCompressor();
            if (compressor != null && !"identity".equals(compressor)) {
                return new RefusedCall<>(Status.UNIMPLEMENTED.withDescription("compressed requests are not supported"),
                        callOptions);
            }

            // Create a method descriptor that accepts raw bytes for the request.
            // This allows us to send pre-serialized bytes without re-encoding.
            MethodDescriptor<byte[], RespT> rawMethod = method.toBuilder(
                    ByteArrayMarshaller.INSTANCE,
                    method.getResponseMarshaller()
            ).build();

            // Create the underlying call with the raw method descriptor
            // The caller's context, whose end before the first message starts rawCall unsigned.
            Context context = Context.current();
            // rawCall is created in a child of it: cancel() before the first message cancels the child,
            // so that rawCall starts already cancelled, closes its listener and sends nothing.
            Context.CancellableContext callContext = context.withCancellation();
            ClientCall<byte[], RespT> rawCall;
            Context previous = callContext.attach();
            try {
                rawCall = next.newCall(rawMethod, callOptions);
            } finally {
                callContext.detach(previous);
            }

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
                private Thread starter; // the thread in rawCall.start(), while starting
                private int pendingRequests = 0;
                private Thread firstSender; // sending the signed first message, until it is out
                private boolean open = false; // started, and the first message, if it started the call, is out
                private final List<Runnable> held = new ArrayList<>(); // callbacks raised meanwhile
                // Where the listener is called when this wrapper calls it on its own: the caller's executor.
                private final Executor callExecutor =
                        callOptions.getExecutor() != null ? callOptions.getExecutor() : CALLBACK_EXECUTOR;
                private ScheduledFuture<?> deadlineTimer;
                private final Context.CancellationListener contextListener = cancelled -> startUnsigned();
                // The listener's callbacks, one at a time: its onReady before the first message (see
                // start()) and rawCall's. One that throws cancels the call.
                private final SynchronizationContext callbacks = new SynchronizationContext((thread, e) -> {
                    log.warn("Call listener threw", e);
                    cancel("Call listener threw", e);
                });

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // Delay start until we have the first message and can compute the signature.
                    // grpc enforces the deadline and the context only from rawCall's start: until then,
                    // whichever ends first starts rawCall unsigned, and grpc fails it without sending.
                    synchronized (lock) {
                        this.responseListener = responseListener;
                        this.headers = headers;
                        // Under the lock, so that no start (which removes the listener) can come first
                        // and leave the listener on a long-lived context. CALLBACK_EXECUTOR runs it later.
                        context.addListener(contextListener, CALLBACK_EXECUTOR);
                        Deadline deadline = callOptions.getDeadline();
                        if (deadline != null) {
                            deadlineTimer = deadline.runOnExpiration(
                                    () -> CALLBACK_EXECUTOR.execute(this::startUnsigned), DEADLINE_TIMER);
                        }
                    }

                    // The call is ready for its first message, and only that message starts rawCall: a
                    // sender that sends only on onReady needs this one, or both wait for ever.
                    callExecutor.execute(() -> deliver(() -> {
                        boolean waiting;
                        synchronized (lock) {
                            waiting = !started && !starting;
                        }
                        if (waiting) {
                            responseListener.onReady();
                        }
                    }));
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

                    // CRITICAL: Send the EXACT bytes we signed, not the original message.
                    // This prevents double-serialization which would produce different bytes.
                    // Only the first message is signed; later stream messages go out as-is.
                    if (claimStart(messageBytes)) {
                        // rawCall may call the listener back before the signed message is out, inside
                        // start() on this thread (a direct executor) or on another: every such callback is
                        // held until then (see deliver), so that nothing a listener sends overtakes it.
                        synchronized (lock) {
                            firstSender = Thread.currentThread();
                        }
                        try {
                            startRawCall(false);
                            rawCall.sendMessage(messageBytes);
                        } finally {
                            openAndRelease();
                        }
                        return;
                    }
                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    // If no message was sent, start with empty body signature
                    if (claimStart(new byte[0])) {
                        startRawCall(true);
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
                    // An unstarted call never notifies its listener: start it (unsigned, in its
                    // cancelled context, so nothing is sent) so the listener still gets
                    // onClose(CANCELLED). Before start() there is no listener.
                    boolean startCalled;
                    synchronized (lock) {
                        startCalled = responseListener != null;
                    }
                    if (startCalled && claimStart(null)) {
                        callContext.cancel(Status.CANCELLED
                                .withDescription(message)
                                .withCause(cause)
                                .asRuntimeException());
                        startRawCall(true);
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
                        if (!started && !starting) {
                            if (signed != null) {
                                addSignatureHeaders(signed, clock.millis());
                            }
                            starting = true;
                            starter = Thread.currentThread();
                            return true;
                        }
                        // grpc may run a listener inline in rawCall.start(): that thread must not wait for itself.
                        if (starter == Thread.currentThread() || firstSender == Thread.currentThread()) {
                            return false;
                        }
                        // Until the first message is out, so that nothing another thread sends overtakes it.
                        while (!open) {
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
                        starter = Thread.currentThread();
                    }
                    startRawCall(true);
                }

                // Outside the lock: request() from another thread must not block on it meanwhile. Opens the
                // call to the threads waiting in claimStart, unless a first message still has to go out.
                private void startRawCall(boolean openWhenStarted) {
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
                    // rawCall runs its callbacks in callContext, which cancel() may have cancelled: the
                    // caller's listener runs in the caller's context, so that a call it makes there
                    // (a retry, say) does not end at once as cancelled.
                    Listener<RespT> inCallerContext = new Listener<RespT>() {
                        @Override
                        public void onHeaders(Metadata responseHeaders) {
                            deliver(() -> listener.onHeaders(responseHeaders));
                        }

                        @Override
                        public void onMessage(RespT message) {
                            deliver(() -> listener.onMessage(message));
                        }

                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            deliver(() -> listener.onClose(status, trailers));
                        }

                        @Override
                        public void onReady() {
                            deliver(listener::onReady);
                        }
                    };
                    // Started in callContext too, so a call that is created only now cannot open a
                    // stream in whatever context this thread has.
                    Context previous = callContext.attach();
                    try {
                        rawCall.start(inCallerContext, startHeaders);
                    } finally {
                        callContext.detach(previous);
                        synchronized (lock) {
                            started = true;
                            starting = false;
                            starter = null;
                            open = openWhenStarted;
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

                // A listener callback, in the caller's context. While the signed first message is pending it
                // is held apart from `callbacks`, where a drain already under way on another thread would
                // run it at once; openAndRelease queues it once the message is out. Queued under the lock,
                // so held and later callbacks keep their order.
                private void deliver(Runnable callback) {
                    Runnable task = context.wrap(callback);
                    synchronized (lock) {
                        if (firstSender != null) {
                            held.add(task);
                            return;
                        }
                        callbacks.executeLater(task);
                    }
                    callbacks.drain();
                }

                // The signed first message is out (or failed): open the call to the threads waiting in
                // claimStart, and run the callbacks held meanwhile, in order, before any later one. They
                // run on the call's executor, as they would have without the hold, not on the sender's
                // thread (with a direct executor that is the sender's thread).
                private void openAndRelease() {
                    boolean released;
                    synchronized (lock) {
                        firstSender = null;
                        open = true;
                        released = !held.isEmpty();
                        held.forEach(callbacks::executeLater);
                        held.clear();
                        lock.notifyAll();
                    }
                    if (released) {
                        callExecutor.execute(callbacks::drain);
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
         * Handed out for calls the SDK does not send: closes its listener with {@code status} once started,
         * before anything is sent, and ignores everything else.
         */
        private static final class RefusedCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

            private final Status status;
            private final Executor executor;
            private final Context context = Context.current();

            RefusedCall(Status status, CallOptions callOptions) {
                this.status = status;
                this.executor = callOptions.getExecutor() != null ? callOptions.getExecutor() : CALLBACK_EXECUTOR;
            }

            // On the call's executor, as for any other call: an async caller's onError must not run
            // inside the call that started it.
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
            this.timeout = checkTimeout("timeout", timeout);
            this.streamTimeout = checkTimeout("streamTimeout", streamTimeout);
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
