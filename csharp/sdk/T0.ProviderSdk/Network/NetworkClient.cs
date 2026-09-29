using Grpc.Core.Interceptors;
using Grpc.Net.Client;
using T0.ProviderSdk.Crypto;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Factory for creating auto-signing gRPC clients.
/// </summary>
public static class NetworkClient
{
    /// <summary>
    /// Creates a gRPC channel with auto-signing transport.
    /// </summary>
    /// <remarks>
    /// The channel applies no default deadline: <see cref="NetworkClientOptions.Timeout"/> and
    /// <see cref="NetworkClientOptions.StreamTimeout"/> take effect through
    /// <see cref="DefaultDeadlineInterceptor"/>, so either set <c>CallOptions.Deadline</c> on
    /// each call or wrap the channel:
    /// <c>channel.Intercept(new DefaultDeadlineInterceptor(options))</c>.
    /// </remarks>
    public static GrpcChannel Create(
        NetworkClientOptions options,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        ArgumentNullException.ThrowIfNull(options);
        ArgumentNullException.ThrowIfNull(signer);

        var signingHandler = new SigningDelegatingHandler(signer, timeProvider)
        {
            InnerHandler = new HttpClientHandler()
        };

        // Deadlines come from the call, never from HttpClient: its Timeout only runs until the
        // response headers, which for a client stream is the whole upload.
        var httpClient = new HttpClient(signingHandler)
        {
            Timeout = Timeout.InfiniteTimeSpan
        };

        try
        {
            return GrpcChannel.ForAddress(options.BaseUrl, new GrpcChannelOptions
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
    }

    /// <summary>
    /// Creates a gRPC channel with auto-signing transport from a private key hex string.
    /// </summary>
    /// <remarks>
    /// Like <see cref="Create"/>, the channel applies no default deadline.
    /// </remarks>
    public static GrpcChannel CreateChannel(
        string privateKeyHex,
        NetworkClientOptions? options = null,
        TimeProvider? timeProvider = null)
    {
        if (string.IsNullOrEmpty(privateKeyHex))
            throw new ArgumentException("provider private key is not set", nameof(privateKeyHex));

        return Create(options ?? new NetworkClientOptions(), Signer.FromHex(privateKeyHex), timeProvider);
    }

    /// <summary>
    /// Creates a Payment NetworkService client with auto-signing transport, request validation,
    /// and the default deadlines of <see cref="NetworkClientOptions"/>.
    /// </summary>
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        string baseUrl,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        var options = new NetworkClientOptions { BaseUrl = baseUrl };
        var channel = Create(options, signer, timeProvider);
        var invoker = channel.Intercept(new RequestValidationInterceptor(), new DefaultDeadlineInterceptor(options));
        return new PaymentApi.NetworkService.NetworkServiceClient(invoker);
    }

    /// <summary>
    /// Creates a PaymentIntent NetworkService client with auto-signing transport, request
    /// validation, and the default deadlines of <see cref="NetworkClientOptions"/>.
    /// </summary>
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        string baseUrl,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        var options = new NetworkClientOptions { BaseUrl = baseUrl };
        var channel = Create(options, signer, timeProvider);
        var invoker = channel.Intercept(new RequestValidationInterceptor(), new DefaultDeadlineInterceptor(options));
        return new PaymentIntentApi.NetworkService.NetworkServiceClient(invoker);
    }
}
