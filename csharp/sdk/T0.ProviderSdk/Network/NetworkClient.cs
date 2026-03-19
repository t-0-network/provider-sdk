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

        var httpClient = new HttpClient(signingHandler)
        {
            Timeout = options.Timeout
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
    /// Creates a Payment NetworkService client with auto-signing transport.
    /// </summary>
    public static PaymentApi.NetworkService.NetworkServiceClient CreateNetworkServiceClient(
        string baseUrl,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        var channel = Create(new NetworkClientOptions { BaseUrl = baseUrl }, signer, timeProvider);
        return new PaymentApi.NetworkService.NetworkServiceClient(channel);
    }

    /// <summary>
    /// Creates a PaymentIntent NetworkService client with auto-signing transport.
    /// </summary>
    public static PaymentIntentApi.NetworkService.NetworkServiceClient CreatePaymentIntentNetworkServiceClient(
        string baseUrl,
        Signer signer,
        TimeProvider? timeProvider = null)
    {
        var channel = Create(new NetworkClientOptions { BaseUrl = baseUrl }, signer, timeProvider);
        return new PaymentIntentApi.NetworkService.NetworkServiceClient(channel);
    }
}
