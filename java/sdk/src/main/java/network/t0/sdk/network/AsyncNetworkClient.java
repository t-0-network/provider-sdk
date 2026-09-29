package network.t0.sdk.network;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractAsyncStub;
import network.t0.sdk.crypto.Signer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A gRPC client for asynchronous calls with StreamObserver and automatic request signing.
 *
 * <p>This client automatically signs all outgoing requests using the provided signer,
 * matching the signature scheme expected by the T-0 Network.
 *
 * <p>The client is service-agnostic - you provide the stub factory at creation time.
 *
 * <p>The client implements {@link java.io.Closeable} and should be closed when no longer needed
 * to release resources (thread pools, connections).
 *
 * <p>Example usage:
 * <pre>{@code
 * Signer signer = Signer.fromHex(privateKeyHex);
 *
 * // Create a client for NetworkService
 * try (var client = AsyncNetworkClient.create(
 *         "https://api.t-0.network",
 *         signer,
 *         NetworkServiceGrpc::newStub)) {
 *     client.stub().updateQuote(request, new StreamObserver<>() {
 *         @Override
 *         public void onNext(UpdateQuoteResponse response) { }
 *         @Override
 *         public void onError(Throwable t) { }
 *         @Override
 *         public void onCompleted() { }
 *     });
 * }
 * }</pre>
 *
 * @param <S> the async stub type
 * @see BlockingNetworkClient
 * @see FutureNetworkClient
 */
public final class AsyncNetworkClient<S extends AbstractAsyncStub<S>> extends NetworkClient {

    private final S stub;

    private AsyncNetworkClient(ManagedChannel channel, Channel interceptedChannel, S stub) {
        super(channel, interceptedChannel);
        this.stub = stub;
    }

    /**
     * Creates a new AsyncNetworkClient for the given endpoint and stub type.
     *
     * <p>Unary calls get a default deadline of {@value #DEFAULT_TIMEOUT_SECONDS} seconds; streaming
     * calls get none.
     *
     * @param endpoint    the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer      the signer to use for signing requests
     * @param stubFactory the stub factory (e.g., {@code NetworkServiceGrpc::newStub})
     * @param <S>         the async stub type
     * @return a new AsyncNetworkClient instance
     * @throws IllegalArgumentException if the endpoint or signer is invalid
     */
    public static <S extends AbstractAsyncStub<S>> AsyncNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory) {
        return create(endpoint, signer, stubFactory, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new AsyncNetworkClient for the given endpoint and stub type.
     *
     * <p>Unary calls without a deadline of their own get one of {@code timeoutSeconds}; streaming
     * calls get none.
     *
     * @param endpoint       the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer         the signer to use for signing requests
     * @param stubFactory    the stub factory (e.g., {@code NetworkServiceGrpc::newStub})
     * @param timeoutSeconds the default deadline in seconds for unary calls; must be positive
     * @param <S>            the async stub type
     * @return a new AsyncNetworkClient instance
     * @throws IllegalArgumentException if the endpoint or signer is invalid, or timeoutSeconds is not positive
     */
    public static <S extends AbstractAsyncStub<S>> AsyncNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory,
            int timeoutSeconds) {
        return create(endpoint, signer, stubFactory, Duration.ofSeconds(timeoutSeconds), null);
    }

    /**
     * Creates a new AsyncNetworkClient with separate default deadlines for unary and streaming calls.
     *
     * <p>Each call without a deadline of its own gets one when it is created: {@code unaryTimeout}
     * for unary calls, {@code streamTimeout} for client-, server- and bidi-streaming calls. A stream
     * can run as long as an upload or a download takes, so {@code null} or {@link Duration#ZERO}
     * means no stream deadline; bound a stream with {@link #stub(long, TimeUnit)} instead.
     *
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer        the signer to use for signing requests
     * @param stubFactory   the stub factory (e.g., {@code NetworkServiceGrpc::newStub})
     * @param unaryTimeout  the default deadline for unary calls; must be positive
     * @param streamTimeout the default deadline for streaming calls; {@code null} or zero for none
     * @param <S>           the async stub type
     * @return a new AsyncNetworkClient instance
     * @throws IllegalArgumentException if the endpoint or signer is invalid, {@code unaryTimeout} is not
     *                                  positive or {@code streamTimeout} is negative
     */
    public static <S extends AbstractAsyncStub<S>> AsyncNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory,
            Duration unaryTimeout,
            Duration streamTimeout) {
        ChannelPair pair = createChannel(endpoint, signer, unaryTimeout, streamTimeout);
        S stub = stubFactory.apply(pair.interceptedChannel());
        return new AsyncNetworkClient<>(pair.channel(), pair.interceptedChannel(), stub);
    }

    /**
     * Returns the async stub for the configured service.
     *
     * @return the async stub with signing interceptor applied
     */
    public S stub() {
        return stub;
    }

    /**
     * Returns an async stub with a custom deadline for this call.
     *
     * <p>This is useful when different operations require different timeouts.
     *
     * <p>The deadline replaces the client's default deadline for the calls made on this stub.
     *
     * @param timeout the timeout value
     * @param unit    the time unit for the timeout
     * @return a new stub instance with the specified deadline
     * @throws IllegalArgumentException if timeout is not positive or unit is null
     */
    public S stub(long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        return stub.withDeadlineAfter(timeout, unit);
    }
}
