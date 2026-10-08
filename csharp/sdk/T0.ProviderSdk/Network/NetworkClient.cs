using System.Runtime.CompilerServices;
using Grpc.Core;
using Grpc.Core.Interceptors;
using Grpc.Net.Client;
using T0.ProviderSdk.Common;
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
/// (client and server streams). Bidirectional streams are refused (see docs/STREAMING.md). All
/// clients share one connection pool, so a client is cheap to create and needs no disposing.
/// </remarks>
public static class NetworkClient
{
    /// <summary>
    /// Creates a client from a generated gRPC client's constructor, for example
    /// <c>NetworkClient.Create(options, signer, invoker =&gt; new NetworkService.NetworkServiceClient(invoker))</c>.
    /// </summary>
    public static TClient Create<TClient>(
        NetworkClientOptions options,
        SignFn signer,
        Func<CallInvoker, TClient> newClient)
    {
        if (options is null)
            throw new ArgumentNullException(null, Messages.ArgumentNull("options"));
        if (signer is null)
            throw new ArgumentNullException(null, Messages.SignerNull);
        if (newClient is null)
            throw new ArgumentNullException(null, Messages.ArgumentNull("newClient"));

        var httpClient = CreateHttpClient(signer, options.PathPrefix);
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

    /// <summary>
    /// Creates a client that signs with <paramref name="signer"/> through a <see cref="SignFn"/> made
    /// from <see cref="ISigner.Sign"/>, with the same checks and errors; see
    /// <see cref="Create{TClient}(NetworkClientOptions, SignFn, Func{CallInvoker, TClient})"/>.
    /// </summary>
    [Obsolete(SignerAdapter.ObsoleteMessage)]
    [OverloadResolutionPriority(-1)]
    public static TClient Create<TClient>(
        NetworkClientOptions options,
        ISigner signer,
        Func<CallInvoker, TClient> newClient) =>
        Create(options, SignerAdapter.ToSignFn(signer)!, newClient);

    // One connection pool for the process: a client created per request would otherwise leave a
    // pool of open connections behind it, since nothing disposes a client.
    internal static readonly SocketsHttpHandler SharedTransport = CreateTransport();

    internal static HttpClient CreateHttpClient(SignFn signer, string pathPrefix = "")
    {
        // disposeHandler: false, so disposing one client's channel leaves the shared transport open.
        // Deadlines come from the call: HttpClient.Timeout only runs until the response headers,
        // which for a client stream is the whole upload.
        return new HttpClient(CreateSigningHandler(signer, pathPrefix), disposeHandler: false)
        {
            Timeout = System.Threading.Timeout.InfiniteTimeSpan
        };
    }

    internal static SigningDelegatingHandler CreateSigningHandler(SignFn signer, string pathPrefix = "") =>
        new(signer) { InnerHandler = SharedTransport, PathPrefix = pathPrefix };

    // A redirect is not followed: it would send the signed request to another server. No cookies
    // are kept: the transport is shared by every client, whatever key signs its requests. No HTTP/2
    // keepalive pings are sent, as in the other SDKs' clients.
    internal static SocketsHttpHandler CreateTransport() => new()
    {
        AllowAutoRedirect = false,
        UseCookies = false,
    };

    /// <summary>
    /// Creates a Payment NetworkService client; see
    /// <see cref="Create{TClient}(NetworkClientOptions, SignFn, Func{CallInvoker, TClient})"/>.
    /// </summary>
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        NetworkClientOptions options,
        SignFn signer) =>
        Create(options, signer, invoker => new PaymentApi.NetworkService.NetworkServiceClient(invoker));

    /// <summary>
    /// Creates a Payment NetworkService client for <paramref name="baseUrl"/> with the default timeouts;
    /// pass <see cref="NetworkClientOptions"/> to change them.
    /// </summary>
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        string baseUrl,
        SignFn signer) =>
        CreateNetworkServiceClient(new NetworkClientOptions { BaseUrl = baseUrl }, signer);

    /// <summary>
    /// Creates a PaymentIntent NetworkService client; see
    /// <see cref="Create{TClient}(NetworkClientOptions, SignFn, Func{CallInvoker, TClient})"/>.
    /// </summary>
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        NetworkClientOptions options,
        SignFn signer) =>
        Create(options, signer, invoker => new PaymentIntentApi.NetworkService.NetworkServiceClient(invoker));

    /// <summary>
    /// Creates a PaymentIntent NetworkService client for <paramref name="baseUrl"/> with the default
    /// timeouts; pass <see cref="NetworkClientOptions"/> to change them.
    /// </summary>
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        string baseUrl,
        SignFn signer) =>
        CreatePaymentIntentNetworkServiceClient(new NetworkClientOptions { BaseUrl = baseUrl }, signer);

    /// <summary>
    /// Creates a Payment NetworkService client that signs with an <see cref="ISigner"/>; see
    /// <see cref="Create{TClient}(NetworkClientOptions, ISigner, Func{CallInvoker, TClient})"/>.
    /// </summary>
    [Obsolete(SignerAdapter.ObsoleteMessage)]
    [OverloadResolutionPriority(-1)]
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        NetworkClientOptions options,
        ISigner signer) =>
        CreateNetworkServiceClient(options, SignerAdapter.ToSignFn(signer)!);

    /// <summary>
    /// Creates a Payment NetworkService client for <paramref name="baseUrl"/> that signs with an
    /// <see cref="ISigner"/>; see
    /// <see cref="Create{TClient}(NetworkClientOptions, ISigner, Func{CallInvoker, TClient})"/>.
    /// </summary>
    [Obsolete(SignerAdapter.ObsoleteMessage)]
    [OverloadResolutionPriority(-1)]
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        string baseUrl,
        ISigner signer) =>
        CreateNetworkServiceClient(baseUrl, SignerAdapter.ToSignFn(signer)!);

    /// <summary>
    /// Creates a PaymentIntent NetworkService client that signs with an <see cref="ISigner"/>; see
    /// <see cref="Create{TClient}(NetworkClientOptions, ISigner, Func{CallInvoker, TClient})"/>.
    /// </summary>
    [Obsolete(SignerAdapter.ObsoleteMessage)]
    [OverloadResolutionPriority(-1)]
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        NetworkClientOptions options,
        ISigner signer) =>
        CreatePaymentIntentNetworkServiceClient(options, SignerAdapter.ToSignFn(signer)!);

    /// <summary>
    /// Creates a PaymentIntent NetworkService client for <paramref name="baseUrl"/> that signs with
    /// an <see cref="ISigner"/>; see
    /// <see cref="Create{TClient}(NetworkClientOptions, ISigner, Func{CallInvoker, TClient})"/>.
    /// </summary>
    [Obsolete(SignerAdapter.ObsoleteMessage)]
    [OverloadResolutionPriority(-1)]
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        string baseUrl,
        ISigner signer) =>
        CreatePaymentIntentNetworkServiceClient(baseUrl, SignerAdapter.ToSignFn(signer)!);
}
