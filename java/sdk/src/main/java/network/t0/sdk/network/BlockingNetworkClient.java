package network.t0.sdk.network;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.Signer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A gRPC client for synchronous/blocking calls with automatic request signing.
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
 * try (var client = BlockingNetworkClient.create(
 *         "https://api.t-0.network",
 *         signer,
 *         NetworkServiceGrpc::newBlockingStub)) {
 *     client.stub().updateQuote(request);
 * }
 *
 * // Create a client for ProviderService
 * try (var client = BlockingNetworkClient.create(
 *         "https://api.t-0.network",
 *         signer,
 *         ProviderServiceGrpc::newBlockingStub)) {
 *     client.stub().payOut(request);
 * }
 * }</pre>
 *
 * @param <S> the blocking stub type
 * @see AsyncNetworkClient
 * @see FutureNetworkClient
 */
public final class BlockingNetworkClient<S extends AbstractBlockingStub<S>> extends NetworkClient {

    private final S stub;

    private BlockingNetworkClient(ManagedChannel channel, Channel interceptedChannel, S stub) {
        super(channel, interceptedChannel);
        this.stub = stub;
    }

    /**
     * Creates a new BlockingNetworkClient for the given endpoint and stub type.
     *
     * <p>Default deadlines: 15 seconds for unary calls, 5 minutes for client- and server-streaming calls.
     *
     * @param endpoint    the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or {@code null} for "https://api.t-0.network"
     * @param signer      the signer to use for signing requests
     * @param stubFactory the stub factory (e.g., {@code NetworkServiceGrpc::newBlockingStub})
     * @param <S>         the blocking stub type
     * @return a new BlockingNetworkClient instance
     * @throws IllegalArgumentException if the endpoint, signer or stub factory is invalid
     */
    public static <S extends AbstractBlockingStub<S>> BlockingNetworkClient<S> create(
            String endpoint,
            DigestSigner signer,
            Function<Channel, S> stubFactory) {
        return create(endpoint, signer, stubFactory, DEFAULT_TIMEOUT, DEFAULT_STREAM_TIMEOUT);
    }

    /**
     * Creates a new BlockingNetworkClient with a default deadline in seconds for unary calls; streaming calls
     * get the default stream timeout of 5 minutes.
     *
     * @param endpoint       the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or {@code null} for "https://api.t-0.network"
     * @param signer         the signer to use for signing requests
     * @param stubFactory    the stub factory (e.g., {@code NetworkServiceGrpc::newBlockingStub})
     * @param timeoutSeconds the default deadline for unary calls, in seconds
     * @param <S>            the blocking stub type
     * @return a new BlockingNetworkClient instance
     * @throws IllegalArgumentException if the endpoint, signer or stub factory is invalid, or the timeout is not
     *                                  a positive duration of at most 2147483647 ms
     * @deprecated Use {@link #create(String, DigestSigner, Function, Duration, Duration)}, which also sets the
     *             stream timeout.
     */
    @Deprecated
    public static <S extends AbstractBlockingStub<S>> BlockingNetworkClient<S> create(
            String endpoint,
            Signer signer,
            Function<Channel, S> stubFactory,
            int timeoutSeconds) {
        return create(endpoint, signer, stubFactory, Duration.ofSeconds(timeoutSeconds), DEFAULT_STREAM_TIMEOUT);
    }

    /**
     * Creates a new BlockingNetworkClient with separate default deadlines for unary and streaming calls.
     *
     * <p>See {@code docs/STREAMING.md}.
     *
     * @param endpoint      the T-0 Network endpoint (e.g., "https://api.t-0.network" or "api.t-0.network:443"), or {@code null} for "https://api.t-0.network"
     * @param signer        the signer to use for signing requests
     * @param stubFactory   the stub factory (e.g., {@code NetworkServiceGrpc::newBlockingStub})
     * @param timeout       the default deadline for unary calls
     * @param streamTimeout the default deadline for client- and server-streaming calls, including
     *                      the wait for the first message
     * @param <S>           the blocking stub type
     * @return a new BlockingNetworkClient instance
     * @throws IllegalArgumentException if the endpoint, signer or stub factory is invalid, or a timeout is
     *                                  not a positive duration of at most 2147483647 ms
     */
    public static <S extends AbstractBlockingStub<S>> BlockingNetworkClient<S> create(
            String endpoint,
            DigestSigner signer,
            Function<Channel, S> stubFactory,
            Duration timeout,
            Duration streamTimeout) {
        if (stubFactory == null) {
            throw new IllegalArgumentException("stubFactory must not be null");
        }
        ChannelPair pair = createChannel(endpoint, signer, timeout, streamTimeout);
        S stub = stubFactory.apply(pair.interceptedChannel());
        return new BlockingNetworkClient<>(pair.channel(), pair.interceptedChannel(), stub);
    }

    /**
     * Returns the blocking stub for the configured service.
     *
     * @return the blocking stub with signing interceptor applied
     */
    public S stub() {
        return stub;
    }

    /**
     * Returns a blocking stub with a custom deadline for this call.
     *
     * <p>This is useful when different operations require different timeouts,
     * for example:
     * <pre>{@code
     * // Quick operation with short timeout
     * client.stub(5, TimeUnit.SECONDS).getStatus(request);
     *
     * // Long operation with extended timeout
     * client.stub(2, TimeUnit.MINUTES).processLargeFile(request);
     * }</pre>
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
