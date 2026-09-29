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
    /// Default deadline of a unary call that sets none, applied by <see cref="DefaultDeadlineInterceptor"/>.
    /// <see cref="System.Threading.Timeout.InfiniteTimeSpan"/> means none.
    /// </summary>
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(15);

    /// <summary>
    /// Default deadline of a client- or server-streaming call that sets none; null (the default)
    /// means none. Applied like <see cref="Timeout"/>.
    /// </summary>
    /// <remarks>See docs/csharp/STREAMING.md.</remarks>
    public TimeSpan? StreamTimeout { get; set; }
}
