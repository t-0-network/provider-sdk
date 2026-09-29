using System.Diagnostics.CodeAnalysis;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Configuration options for the auto-signing network client.
/// </summary>
public sealed class NetworkClientOptions
{
    private const string DefaultBaseUrl = "https://api.t-0.network";
    private const long MaxTimeoutMs = int.MaxValue;

    private string _baseUrl = DefaultBaseUrl;
    private TimeSpan _timeout = TimeSpan.FromSeconds(15);
    private TimeSpan _streamTimeout = TimeSpan.FromMinutes(5);

    /// <summary>
    /// Base URL of the T-0 Network API, <c>https://api.t-0.network</c> by default or when set to null.
    /// </summary>
    /// <exception cref="ArgumentException">
    /// The value is empty, or is not an <c>http://</c> or <c>https://</c> URL with a host and, if it
    /// has a port, a port from 1 to 65535.
    /// </exception>
    [AllowNull]
    public string BaseUrl
    {
        get => _baseUrl;
        set => _baseUrl = value is null ? DefaultBaseUrl : ValidateBaseUrl(value);
    }

    /// <summary>
    /// Deadline of a unary call that sets none of its own. Defaults to 15 seconds.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">
    /// The value is not positive or is longer than 2147483647 ms.
    /// </exception>
    public TimeSpan Timeout
    {
        get => _timeout;
        set => _timeout = Validate(value, nameof(Timeout));
    }

    /// <summary>
    /// Deadline of a client- or server-streaming call that sets none of its own, for the whole call
    /// including the wait for its first message. Defaults to 5 minutes. See docs/STREAMING.md.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">
    /// The value is not positive or is longer than 2147483647 ms.
    /// </exception>
    public TimeSpan StreamTimeout
    {
        get => _streamTimeout;
        set => _streamTimeout = Validate(value, nameof(StreamTimeout));
    }

    private static string ValidateBaseUrl(string value)
    {
        if (value.Length == 0)
            throw new ArgumentException("base URL is not set", nameof(BaseUrl));
        // Uri alone would read "http:host" as http://host and accept port 0.
        var hasScheme = value.StartsWith("http://", StringComparison.OrdinalIgnoreCase)
            || value.StartsWith("https://", StringComparison.OrdinalIgnoreCase);
        if (!hasScheme
            || !Uri.TryCreate(value, UriKind.Absolute, out var uri)
            || uri.Host.Length == 0
            || uri.Port is < 1 or > 65535)
            throw new ArgumentException("base URL is not valid", nameof(BaseUrl));
        return value;
    }

    // A timeout cannot be turned off, so Timeout.InfiniteTimeSpan is refused like any other negative value.
    private static TimeSpan Validate(TimeSpan value, string name) =>
        value > TimeSpan.Zero && value.TotalMilliseconds <= MaxTimeoutMs
            ? value
            : throw new ArgumentOutOfRangeException(
                name, value, $"{name} must be a positive duration of at most {MaxTimeoutMs} ms");
}
