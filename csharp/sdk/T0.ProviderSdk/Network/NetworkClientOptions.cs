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
    /// Default deadline of each unary call, from sending the request to reading the end of the
    /// response. Applied by <see cref="DefaultDeadlineInterceptor"/>, which the
    /// <c>NetworkClient.Create*ServiceClient</c> helpers install; a deadline set on the call wins.
    /// <see cref="System.Threading.Timeout.InfiniteTimeSpan"/> means none.
    /// </summary>
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(15);

    /// <summary>
    /// Default deadline of each client-streaming and server-streaming call, from starting the call
    /// to reading the end of the response. Null (the default) means none. Applied like
    /// <see cref="Timeout"/>.
    /// </summary>
    public TimeSpan? StreamTimeout { get; set; }
}
