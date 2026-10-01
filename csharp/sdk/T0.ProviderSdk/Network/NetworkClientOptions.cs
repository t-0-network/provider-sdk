using System.Buffers;
using System.Diagnostics.CodeAnalysis;
using System.Text;

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

    // The base URL's path as written, without its trailing "/" (e.g. "/v1"), or "". Grpc.Net ignores
    // the path of a channel's address, so SigningDelegatingHandler puts it before each call's path.
    internal string PathPrefix { get; private set; } = "";

    private static (string Url, string PathPrefix) ValidateBaseUrl(string value)
    {
        if (value.Length == 0)
            throw new ArgumentException("base URL is not set", nameof(BaseUrl));
        // A value without a scheme is read as https.
        var url = value.Contains(Uri.SchemeDelimiter, StringComparison.Ordinal) ? value : "https://" + value;
        return IsValidBaseUrl(url, out var pathPrefix)
            ? (url, pathPrefix)
            : throw new ArgumentException("base URL is not valid", nameof(BaseUrl));
    }

    private static readonly SearchValues<char> HostNameChars =
        SearchValues.Create("-.0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    private static readonly SearchValues<char> PathChars =
        SearchValues.Create("-./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz~");

    // Uri parses the URL. Where it reads text as something else, the authority and path are also
    // checked as written: Uri skips leading white space, reads "h:" and "h:080" as port 80, "1.2.3"
    // as 1.2.0.3, "[::1]x" as [::1]/x, drops an IPv6 zone and normalizes paths ("/v1/..", "/v%31").
    private static bool IsValidBaseUrl(string url, out string pathPrefix)
    {
        pathPrefix = "";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)
            || (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps)
            || !url.StartsWith(uri.Scheme + Uri.SchemeDelimiter, StringComparison.OrdinalIgnoreCase)
            || uri.Port == 0 || uri.Query.Length > 0 || uri.Fragment.Length > 0)
            return false;

        var rest = url.AsSpan(uri.Scheme.Length + Uri.SchemeDelimiter.Length);
        var slash = rest.IndexOf('/');
        var authority = slash < 0 ? rest : rest[..slash];
        var path = slash < 0 ? [] : rest[slash..];
        if (authority.Contains('@'))
            return false; // user info, also an empty one, which Uri does not report

        var hostLength = uri.HostNameType == UriHostNameType.IPv6 ? authority.IndexOf(']') + 1 : uri.Host.Length;
        if (hostLength <= 0 || hostLength > authority.Length)
            return false;
        var host = authority[..hostLength];
        var port = authority[hostLength..];
        var validHost = uri.HostNameType switch
        {
            UriHostNameType.IPv4 => host.SequenceEqual(uri.Host),
            UriHostNameType.IPv6 => !uri.IdnHost.Contains('%'),
            // Basic: a name Uri does not take for DNS, such as one with a label over 63 characters.
            UriHostNameType.Dns or UriHostNameType.Basic => Ascii.EqualsIgnoreCase(host, uri.Host) && IsHostName(host),
            _ => false,
        };
        if (!validHost || !(port.IsEmpty || port.SequenceEqual($":{uri.Port}")))
            return false;

        // Uri keeps such a path as written unless a segment is "." or "..".
        if (path.ContainsAnyExcept(PathChars) || path.Contains("//", StringComparison.Ordinal)
            || !(path.IsEmpty || path.SequenceEqual(uri.AbsolutePath)))
            return false;
        pathPrefix = path.TrimEnd('/').ToString();
        return true;
    }

    // Labels of ASCII letters, digits and inner "-", the last starting with a letter; gRPC clients
    // cannot connect to other names. Uri also takes "_", a label ending in "-", a trailing "." and a
    // last label such as "1b".
    private static bool IsHostName(ReadOnlySpan<char> host)
    {
        var last = host[(host.LastIndexOf('.') + 1)..];
        if (host.ContainsAnyExcept(HostNameChars) || last.IsEmpty || !char.IsAsciiLetter(last[0]))
            return false;
        foreach (var range in host.Split('.'))
        {
            if (host[range] is [] or ['-', ..] or [.., '-'])
                return false;
        }
        return true;
    }

    // A timeout cannot be turned off, so Timeout.InfiniteTimeSpan is refused like any other negative value.
    private static TimeSpan Validate(TimeSpan value, string name) =>
        value > TimeSpan.Zero && value.TotalMilliseconds <= MaxTimeoutMs
            ? value
            : throw new ArgumentOutOfRangeException(
                name, value, $"{name} must be a positive duration of at most {MaxTimeoutMs} ms");
}
