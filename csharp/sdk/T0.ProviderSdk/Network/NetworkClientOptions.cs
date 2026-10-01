using System.Diagnostics.CodeAnalysis;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Configuration options for the auto-signing network client.
/// </summary>
public sealed class NetworkClientOptions
{
    private const string DefaultBaseUrl = "https://api.t-0.network";

    private string _baseUrl = DefaultBaseUrl;
    private TimeSpan _timeout = TimeSpan.FromSeconds(15);
    private TimeSpan _streamTimeout = TimeSpan.FromMinutes(5);

    /// <summary>
    /// Base URL of the T-0 Network API, <c>https://api.t-0.network</c> by default or when set to null.
    /// A path in it prefixes every call: <c>https://host/v1</c> calls <c>https://host/v1/&lt;service&gt;/&lt;method&gt;</c>.
    /// </summary>
    /// <exception cref="ArgumentException">
    /// The value is empty ("base URL is not set") or not a valid base URL ("base URL is not valid").
    /// </exception>
    [AllowNull]
    public string BaseUrl
    {
        get => _baseUrl;
        set => (_baseUrl, PathPrefix) = value is null ? (DefaultBaseUrl, "") : ValidateBaseUrl(value);
    }

    /// <summary>
    /// Deadline of a unary call that sets none of its own. Defaults to 15 seconds.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">
    /// The value is not positive.
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
    /// The value is not positive.
    /// </exception>
    public TimeSpan StreamTimeout
    {
        get => _streamTimeout;
        set => _streamTimeout = Validate(value, nameof(StreamTimeout));
    }

    // The base URL's path without its trailing "/" (e.g. "/v1"), or "". Grpc.Net ignores
    // the path of a channel's address, so SigningDelegatingHandler puts it before each call's path.
    internal string PathPrefix { get; private set; } = "";

    private static (string Url, string PathPrefix) ValidateBaseUrl(string value)
    {
        if (value.Length == 0)
            throw new ArgumentException("base URL is not set", nameof(BaseUrl));
        // A value without a scheme is read as https.
        var url = value.Contains(Uri.SchemeDelimiter, StringComparison.Ordinal) ? value : "https://" + value;
        return Uri.TryCreate(url, UriKind.Absolute, out var uri)
            && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
            && uri.Host.Length > 0 && uri.UserInfo.Length == 0 && uri.Port > 0
            && !url.AsSpan().ContainsAny('?', '#')
            ? (url, uri.AbsolutePath.TrimEnd('/'))
            : throw new ArgumentException("base URL is not valid", nameof(BaseUrl));
    }

    private static TimeSpan Validate(TimeSpan value, string name) =>
        value > TimeSpan.Zero
            ? value
            : throw new ArgumentOutOfRangeException(name, value, $"{name} must be a positive duration");
}
