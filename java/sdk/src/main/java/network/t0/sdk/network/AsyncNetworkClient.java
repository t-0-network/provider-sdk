package network.t0.sdk.network;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractAsyncStub;
import network.t0.sdk.crypto.Signer;

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
     * @param endpoint       the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443")
     * @param signer         the signer to use for signing requests
     * @param stubFactory    the stub factory (e.g., {@code NetworkServiceGrpc::newStub})
     * @param timeoutSeconds the timeout in seconds for requests
     * @param <S>            the async stub type
     * @return a new AsyncNetworkClient instance
     * @throws IllegalArgumentException if the endpoint or signer is invalid
     */
    public static <S extends AbstractAsyncStub<S>> AsyncNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory,
            int timeoutSeconds) {
        ChannelPair pair = createChannel(endpoint, signer, timeoutSeconds);
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
