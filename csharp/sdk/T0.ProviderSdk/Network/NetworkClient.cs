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
    /// <see cref="NetworkClientOptions.Timeout"/> and <see cref="NetworkClientOptions.StreamTimeout"/>
    /// apply only through <c>channel.Intercept(new DefaultDeadlineInterceptor(options))</c>, which also
    /// rejects bidirectional streams. See docs/STREAMING.md.
    /// </remarks>
    public static GrpcChannel Create(
        NetworkClientOptions options,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        ArgumentNullException.ThrowIfNull(options);
        ArgumentNullException.ThrowIfNull(signer);

        var httpClient = CreateHttpClient(signer, timeProvider);
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

    internal static HttpClient CreateHttpClient(Signer signer, TimeProvider? timeProvider)
    {
        var signingHandler = new SigningDelegatingHandler(signer, timeProvider)
        {
            InnerHandler = new HttpClientHandler()
        };

        // Deadlines come from the call: HttpClient.Timeout only runs until the response headers,
        // which for a client stream is the whole upload.
        return new HttpClient(signingHandler)
        {
            Timeout = Timeout.InfiniteTimeSpan
        };
    }

    /// <summary>
    /// Creates a gRPC channel with auto-signing transport from a private key hex string.
    /// </summary>
    /// <remarks>Like <see cref="Create"/>, timeouts apply only through <see cref="DefaultDeadlineInterceptor"/>.</remarks>
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
    /// Creates a Payment NetworkService client with auto-signing transport, request validation and
    /// <see cref="DefaultDeadlineInterceptor"/>.
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
    /// validation and <see cref="DefaultDeadlineInterceptor"/>.
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
