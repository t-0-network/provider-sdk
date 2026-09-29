using Grpc.Core;
using Grpc.Core.Interceptors;
using Grpc.Net.Client;
using T0.ProviderSdk.Crypto;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Factory for auto-signing gRPC clients of the T-0 Network.
/// </summary>
/// <remarks>
/// Every client signs its requests and gives a call without a deadline of its own
/// <see cref="NetworkClientOptions.Timeout"/> (unary) or <see cref="NetworkClientOptions.StreamTimeout"/>
/// (client and server streams). Bidirectional streams are refused. All clients share one connection
/// pool, so a client is cheap to create and needs no disposing. See docs/STREAMING.md.
/// </remarks>
public static class NetworkClient
{
    /// <summary>
    /// Creates a client from a generated gRPC client's constructor, for example
    /// <c>NetworkClient.Create(options, signer, invoker =&gt; new NetworkService.NetworkServiceClient(invoker))</c>.
    /// </summary>
    public static TClient Create<TClient>(
        NetworkClientOptions options,
        ISigner signer,
        Func<CallInvoker, TClient> newClient)
    {
        ArgumentNullException.ThrowIfNull(options);
        ArgumentNullException.ThrowIfNull(signer);
        ArgumentNullException.ThrowIfNull(newClient);

        var httpClient = CreateHttpClient(signer);
        GrpcChannel channel;
        try
        {
            channel = GrpcChannel.ForAddress(options.BaseUrl, new GrpcChannelOptions
            {
                HttpClient = httpClient,
                DisposeHttpClient = true
            });
        }
        catch
        {
            httpClient.Dispose();
            throw;
        }

        try
        {
            return newClient(channel.Intercept(new DefaultDeadlineInterceptor(options)));
        }
        catch
        {
            channel.Dispose();
            throw;
        }
    }

    // One connection pool for the process: a client created per request would otherwise leave a
    // pool of open connections behind it, since nothing disposes a client.
    internal static readonly SocketsHttpHandler SharedTransport = CreateTransport();

    internal static HttpClient CreateHttpClient(ISigner signer)
    {
        // disposeHandler: false, so disposing one client's channel leaves the shared transport open.
        // Deadlines come from the call: HttpClient.Timeout only runs until the response headers,
        // which for a client stream is the whole upload.
        return new HttpClient(CreateSigningHandler(signer), disposeHandler: false)
        {
            Timeout = System.Threading.Timeout.InfiniteTimeSpan
        };
    }

    internal static SigningDelegatingHandler CreateSigningHandler(ISigner signer) =>
        new(signer) { InnerHandler = SharedTransport };

    // Pings find a dead HTTP/2 connection while a call waits on it, such as a stream between messages.
    internal static SocketsHttpHandler CreateTransport() => new()
    {
        KeepAlivePingDelay = TimeSpan.FromMinutes(5),
        KeepAlivePingTimeout = TimeSpan.FromSeconds(10),
        KeepAlivePingPolicy = HttpKeepAlivePingPolicy.WithActiveRequests,
    };

    /// <summary>
    /// Creates a Payment NetworkService client; see <see cref="Create{TClient}"/>.
    /// </summary>
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        NetworkClientOptions options,
        ISigner signer) =>
        Create(options, signer, invoker => new PaymentApi.NetworkService.NetworkServiceClient(invoker));

    /// <summary>
    /// Creates a PaymentIntent NetworkService client; see <see cref="Create{TClient}"/>.
    /// </summary>
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        NetworkClientOptions options,
        ISigner signer) =>
        Create(options, signer, invoker => new PaymentIntentApi.NetworkService.NetworkServiceClient(invoker));
}
