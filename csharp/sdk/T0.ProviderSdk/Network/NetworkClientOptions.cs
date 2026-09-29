namespace T0.ProviderSdk.Network;

/// <summary>
/// Configuration options for the auto-signing network client.
/// </summary>
public sealed class NetworkClientOptions
{
    private const long MaxTimeoutMs = int.MaxValue;

    private TimeSpan _timeout = TimeSpan.FromSeconds(15);
    private TimeSpan _streamTimeout = TimeSpan.FromMinutes(5);

    /// <summary>
    /// Base URL of the T-0 Network API.
    /// </summary>
    public string BaseUrl { get; set; } = "https://api.t-0.network";

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

    // A timeout cannot be turned off, so Timeout.InfiniteTimeSpan is refused like any other negative value.
    private static TimeSpan Validate(TimeSpan value, string name) =>
        value > TimeSpan.Zero && value.TotalMilliseconds <= MaxTimeoutMs
            ? value
            : throw new ArgumentOutOfRangeException(
                name, value, $"{name} must be a positive duration of at most {MaxTimeoutMs} ms");
}
