package network.t0.sdk.network;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractFutureStub;
import network.t0.sdk.crypto.DigestSigner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A gRPC client for asynchronous calls with ListenableFuture and automatic request signing.
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
 * try (var client = FutureNetworkClient.create(
 *         "https://api.t-0.network",
 *         signer,
 *         NetworkServiceGrpc::newFutureStub)) {
 *     ListenableFuture<UpdateQuoteResponse> future = client.stub().updateQuote(request);
 *     UpdateQuoteResponse response = future.get();
 * }
 * }</pre>
 *
 * @param <S> the future stub type
 * @see BlockingNetworkClient
 * @see AsyncNetworkClient
 */
public final class FutureNetworkClient<S extends AbstractFutureStub<S>> extends NetworkClient {

    private final S stub;

    private FutureNetworkClient(ManagedChannel channel, Channel interceptedChannel, S stub) {
        super(channel, interceptedChannel);
        this.stub = stub;
    }

    /**
     * Creates a new FutureNetworkClient for the given endpoint and stub type.
     *
     * <p>Default deadlines: 15 seconds for unary calls, 5 minutes for client- and server-streaming calls.
     *
     * @param endpoint    the T-0 Network base URL with an http or https scheme, or {@code null} for "https://api.t-0.network"
     * @param signer      the signer to use for signing requests
     * @param stubFactory the stub factory (e.g., {@code NetworkServiceGrpc::newFutureStub})
     * @param <S>         the future stub type
     * @return a new FutureNetworkClient instance
     * @throws IllegalArgumentException if the endpoint, signer or stub factory is invalid
     */
    public static <S extends AbstractFutureStub<S>> FutureNetworkClient<S> create(
            String endpoint,
            DigestSigner signer,
            Function<Channel, S> stubFactory) {
        return create(endpoint, signer, stubFactory, DEFAULT_TIMEOUT, DEFAULT_STREAM_TIMEOUT);
    }

    /**
     * Creates a new FutureNetworkClient with separate default deadlines for unary and streaming calls.
     *
     * <p>See {@code docs/STREAMING.md}.
     *
     * @param endpoint      the T-0 Network base URL with an http or https scheme, or {@code null} for "https://api.t-0.network"
     * @param signer        the signer to use for signing requests
     * @param stubFactory   the stub factory (e.g., {@code NetworkServiceGrpc::newFutureStub})
     * @param timeout       the default deadline for unary calls
     * @param streamTimeout the default deadline for client- and server-streaming calls, including
     *                      the wait for the first message
     * @param <S>           the future stub type
     * @return a new FutureNetworkClient instance
     * @throws IllegalArgumentException if the endpoint, signer or stub factory is invalid, or a timeout is
     *                                  not a positive duration of at most 2147483647 ms
     */
    public static <S extends AbstractFutureStub<S>> FutureNetworkClient<S> create(
            String endpoint,
            DigestSigner signer,
            Function<Channel, S> stubFactory,
            Duration timeout,
            Duration streamTimeout) {
        if (stubFactory == null) {
            throw new IllegalArgumentException("stubFactory must not be null");
        }
        ChannelPair pair = createChannel(endpoint, signer, timeout, streamTimeout);
        S stub;
        try {
            stub = stubFactory.apply(pair.interceptedChannel());
        } catch (Throwable e) { // also an undeclared checked exception
            pair.channel().shutdownNow(); // nobody else holds the channel to close it
            throw e;
        }
        return new FutureNetworkClient<>(pair.channel(), pair.interceptedChannel(), stub);
    }

    /**
     * Returns the future stub for the configured service.
     *
     * @return the future stub with signing interceptor applied
     */
    public S stub() {
        return stub;
    }

    /**
     * Returns a future stub with a custom deadline for this call.
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
