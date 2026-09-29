namespace T0.ProviderSdk.Network;

/// <summary>
/// Configuration options for the auto-signing network client.
/// </summary>
public sealed class NetworkClientOptions
{
    /// <summary>
    /// Base URL of the T-0 Network API.
    /// </summary>
    public string BaseUrl { get; set; } = "https://api.t-0.network";

    /// <summary>
    /// Read only by <see cref="DefaultDeadlineInterceptor"/>, which
    /// <see cref="NetworkClient.CreateNetworkServiceClient"/> and
    /// <see cref="NetworkClient.CreatePaymentIntentNetworkServiceClient"/> install with default options;
    /// channels from <see cref="NetworkClient.Create"/> and <see cref="NetworkClient.CreateChannel"/> do
    /// not apply it. The default deadline of a unary call that sets none;
    /// <see cref="System.Threading.Timeout.InfiniteTimeSpan"/> means none.
    /// </summary>
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(15);

    /// <summary>
    /// Read only by <see cref="DefaultDeadlineInterceptor"/>, which
    /// <see cref="NetworkClient.CreateNetworkServiceClient"/> and
    /// <see cref="NetworkClient.CreatePaymentIntentNetworkServiceClient"/> install with default options;
    /// channels from <see cref="NetworkClient.Create"/> and <see cref="NetworkClient.CreateChannel"/> do
    /// not apply it. The default deadline of a client- or server-streaming call that sets none; null (the
    /// default) means none.
    /// </summary>
    /// <remarks>See docs/csharp/STREAMING.md.</remarks>
    public TimeSpan? StreamTimeout { get; set; }
}
