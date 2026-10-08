using System.Diagnostics.CodeAnalysis;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Configuration options for the auto-signing network client.
/// </summary>
public sealed class NetworkClientOptions
{
    /// <summary>The base URL a client uses when it is given none.</summary>
    public const string DefaultBaseUrl = "https://api.t-0.network";

    /// <summary>The unary timeout a client uses when it is given none: 15 seconds.</summary>
    public static readonly TimeSpan DefaultTimeout = TimeSpan.FromSeconds(15);

    /// <summary>The stream timeout a client uses when it is given none: 5 minutes.</summary>
    public static readonly TimeSpan DefaultStreamTimeout = TimeSpan.FromMinutes(5);

    /// <summary>The largest <see cref="Timeout"/> and <see cref="StreamTimeout"/>: 2147483647 ms.</summary>
    public static readonly TimeSpan MaxTimeout = TimeSpan.FromMilliseconds(int.MaxValue);

    private string _baseUrl = DefaultBaseUrl;
    private TimeSpan _timeout = DefaultTimeout;
    private TimeSpan _streamTimeout = DefaultStreamTimeout;

    /// <summary>
    /// Base URL of the T-0 Network API, <c>https://api.t-0.network</c> by default or when set to null.
    /// A path in it prefixes every call: <c>https://host/v1</c> calls <c>https://host/v1/&lt;service&gt;/&lt;method&gt;</c>.
    /// </summary>
    /// <exception cref="ArgumentException">
    /// The value is empty ("base URL is not set") or not a valid base URL ("base URL is not valid"),
    /// which includes a value with whitespace or a control character anywhere.
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
    /// The value is not positive or is over <see cref="MaxTimeout"/>.
    /// </exception>
    public TimeSpan Timeout
    {
        get => _timeout;
        set => _timeout = Validate(value, Messages.TimeoutNotValid);
    }

    /// <summary>
    /// Deadline of a client- or server-streaming call that sets none of its own, for the whole call
    /// including the wait for its first message. Defaults to 5 minutes. See docs/STREAMING.md.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">
    /// The value is not positive or is over <see cref="MaxTimeout"/>.
    /// </exception>
    public TimeSpan StreamTimeout
    {
        get => _streamTimeout;
        set => _streamTimeout = Validate(value, Messages.StreamTimeoutNotValid);
    }

    // The base URL's path without its trailing "/" (e.g. "/v1"), or "". Grpc.Net ignores
    // the path of a channel's address, so SigningDelegatingHandler puts it before each call's path.
    internal string PathPrefix { get; private set; } = "";

    private static (string Url, string PathPrefix) ValidateBaseUrl(string value)
    {
        if (value.Length == 0)
            throw new ArgumentException(Messages.BaseUrlNotSet);
        // A value without a scheme is read as https.
        var url = value.Contains(Uri.SchemeDelimiter, StringComparison.Ordinal) ? value : "https://" + value;
        // Never trimmed: Uri would drop surrounding whitespace, and take it, and control characters,
        // in the path; every SDK refuses them all, anywhere.
        if (url.Any(IsSpaceOrControl))
            throw new ArgumentException(Messages.BaseUrlNotValid);
        return Uri.TryCreate(url, UriKind.Absolute, out var uri)
            && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
            && uri.Host.Length > 0 && uri.UserInfo.Length == 0 && uri.Port > 0
            && !url.AsSpan().ContainsAny('?', '#')
            ? (url, uri.AbsolutePath.TrimEnd('/'))
            : throw new ArgumentException(Messages.BaseUrlNotValid);
    }

    // A whitespace or control character: U+0000..U+0020, U+007F, or another Unicode White_Space
    // character. The same set in every SDK.
    private static bool IsSpaceOrControl(char c) =>
        c <= '\u0020' || c == '\u007f' || c == '\u0085' || c == '\u00a0' || c == '\u1680'
        || (c >= '\u2000' && c <= '\u200a') || c == '\u2028' || c == '\u2029' || c == '\u202f'
        || c == '\u205f' || c == '\u3000';

    private static TimeSpan Validate(TimeSpan value, string message) =>
        value > TimeSpan.Zero && value <= MaxTimeout
            ? value
            : throw new ArgumentOutOfRangeException(null, message);
}
