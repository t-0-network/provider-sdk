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
    /// Request timeout.
    /// </summary>
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(15);
}
