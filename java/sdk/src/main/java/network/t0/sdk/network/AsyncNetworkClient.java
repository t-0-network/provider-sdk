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
     * <p>Default deadlines: 15 seconds for unary calls, 5 minutes for client- and server-streaming calls.
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
        return create(endpoint, signer, stubFactory, DEFAULT_TIMEOUT, DEFAULT_STREAM_TIMEOUT);
    }

    /**
     * Creates a new AsyncNetworkClient with separate default deadlines for unary and streaming calls.
     *
     * <p>See {@code docs/STREAMING.md}.
     *
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer        the signer to use for signing requests
     * @param stubFactory   the stub factory (e.g., {@code NetworkServiceGrpc::newStub})
     * @param timeout       the default deadline for unary calls
     * @param streamTimeout the default deadline for client- and server-streaming calls, including
     *                      the wait for the first message
     * @param <S>           the async stub type
     * @return a new AsyncNetworkClient instance
     * @throws IllegalArgumentException if the endpoint or signer is invalid, or a timeout is not a
     *                                  positive duration of at most 2147483647 ms
     */
    public static <S extends AbstractAsyncStub<S>> AsyncNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory,
            Duration timeout,
            Duration streamTimeout) {
        ChannelPair pair = createChannel(endpoint, signer, timeout, streamTimeout);
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
     * @throws IllegalArgumentException if unit is null, or the timeout is not a positive duration of at
     *                                  most 2147483647 ms
     */
    public S stub(long timeout, TimeUnit unit) {
        checkTimeout("timeout", timeout, unit);
        return stub.withDeadlineAfter(timeout, unit);
    }
}
