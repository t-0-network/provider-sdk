using System.Collections.Concurrent;
using System.Runtime.CompilerServices;
using Grpc.Core;
using Grpc.Core.Interceptors;
using Grpc.Net.Client;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Factory for auto-signing gRPC clients of the T-0 Network.
/// </summary>
/// <remarks>
/// Every client signs its requests and gives a call without a deadline of its own the timeout option
/// (tzero.v1.common.timeout) of its method, else <see cref="NetworkClientOptions.Timeout"/> (unary) or
/// <see cref="NetworkClientOptions.StreamTimeout"/> (client and server streams). Bidirectional streams
/// are refused (see docs/STREAMING.md). All clients share one connection pool, so a client is cheap
/// to create and needs no disposing.
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
        // Before the signer, so a missing URL is reported even when the signer is missing too.
        var baseUrl = options.BaseUrl;
        if (string.IsNullOrEmpty(baseUrl))
            throw new ArgumentException(Messages.BaseUrlNotSet);
        if (signer is null)
            throw new ArgumentNullException(null, Messages.SignerNull);
        if (newClient is null)
            throw new ArgumentNullException(null, Messages.ArgumentNull("newClient"));

        var httpClient = CreateHttpClient(signer, options.PathPrefix);
        GrpcChannel channel;
        try
        {
            channel = GrpcChannel.ForAddress(baseUrl, new GrpcChannelOptions
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
            return newClient(channel.Intercept(
                new DefaultDeadlineInterceptor(options, MethodTimeouts(typeof(TClient)))));
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

    private static readonly ConcurrentDictionary<Type, IReadOnlyDictionary<string, TimeSpan>> MethodTimeoutsCache = new();

    // The timeout option (tzero.v1.common.timeout) of each method of the service whose generated
    // client is clientType or a base type of it, by the method's full name ("/pkg.Service/Method"). A
    // type that is not a generated client gets none: its calls get the client's timeout.
    internal static IReadOnlyDictionary<string, TimeSpan> MethodTimeouts(Type clientType) =>
        MethodTimeoutsCache.GetOrAdd(clientType, type =>
            GeneratedService.DescriptorOf(type) is { } service
                ? service.Methods
                    .Where(method => method.GetOptions()?.HasExtension(TimeoutExtensions.Timeout) == true)
                    .ToDictionary(
                        method => $"/{service.FullName}/{method.Name}",
                        method => method.GetOptions().GetExtension(TimeoutExtensions.Timeout).ToTimeSpan())
                : new Dictionary<string, TimeSpan>());

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
}
