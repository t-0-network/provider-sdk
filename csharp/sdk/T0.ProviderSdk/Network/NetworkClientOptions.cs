using System.Diagnostics.CodeAnalysis;
using System.Net;
using System.Net.Sockets;
using System.Text.RegularExpressions;

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
    /// The value is empty, or is not <c>http://</c> or <c>https://</c> followed by a host, an optional
    /// port from 1 to 65535 and an optional trailing '/'. The host is an IPv4 address, an IPv6 address
    /// in brackets, or a name of labels of ASCII letters, digits and inner '-' separated by '.', whose
    /// last label starts with a letter. User info, a path, a query and a fragment are refused.
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
        if (!IsValidBaseUrl(value) || !Uri.TryCreate(value, UriKind.Absolute, out _))
            throw new ArgumentException("base URL is not valid", nameof(BaseUrl));
        return value;
    }

    // Checked as written: Uri alone would read "http:host" as http://host, "http://h:" as port 80 and
    // "1.2.3" as the address 1.2.0.3, and would accept port 0, user info, names such as my_host that
    // some gRPC clients cannot connect to, and a path that the channel drops.
    private static bool IsValidBaseUrl(string url)
    {
        var schemeLength = url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ? "http://".Length
            : url.StartsWith("https://", StringComparison.OrdinalIgnoreCase) ? "https://".Length
            : 0;
        if (schemeLength == 0)
            return false;

        var rest = url.AsSpan(schemeLength);
        var end = rest.IndexOfAny('/', '?', '#');
        var authority = end >= 0 ? rest[..end] : rest;
        var tail = end >= 0 ? rest[end..] : [];
        if (!tail.IsEmpty && !tail.SequenceEqual("/"))
            return false; // a path, query or fragment

        ReadOnlySpan<char> host, port;
        if (authority.StartsWith('['))
        {
            var close = authority.IndexOf(']');
            if (close < 0)
                return false;
            host = authority[..(close + 1)];
            port = authority[(close + 1)..];
        }
        else
        {
            var colon = authority.IndexOf(':');
            host = colon >= 0 ? authority[..colon] : authority;
            port = colon >= 0 ? authority[colon..] : [];
        }

        return IsHost(host) && IsPort(port);
    }

    // IPv4, bracketed IPv6, or a DNS name whose last label starts with a letter.
    private static bool IsHost(ReadOnlySpan<char> host)
    {
        if (host.Length > 2 && host[0] == '[' && host[^1] == ']')
            return IPAddress.TryParse(host[1..^1], out var address)
                && address.AddressFamily == AddressFamily.InterNetworkV6;
        return IsIPv4(host) || HostName.IsMatch(host);
    }

    private static bool IsIPv4(ReadOnlySpan<char> host)
    {
        var octets = 0;
        foreach (var range in host.Split('.'))
        {
            var octet = host[range];
            if (++octets > 4 || octet.Length is 0 or > 3 || octet.ContainsAnyExceptInRange('0', '9') || int.Parse(octet) > 255)
                return false;
        }
        return octets == 4;
    }

    // Nothing, or ':' and a port from 1 to 65535.
    private static bool IsPort(ReadOnlySpan<char> port) =>
        port.IsEmpty
        || (port[0] == ':' && port.Length is > 1 and <= 6 && !port[1..].ContainsAnyExceptInRange('0', '9')
            && int.Parse(port[1..]) is >= 1 and <= 65535);

    private static readonly Regex HostName = new(
        @"^(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)*[A-Za-z](?:[A-Za-z0-9-]*[A-Za-z0-9])?\z",
        RegexOptions.CultureInvariant);

    // A timeout cannot be turned off, so Timeout.InfiniteTimeSpan is refused like any other negative value.
    private static TimeSpan Validate(TimeSpan value, string name) =>
        value > TimeSpan.Zero && value.TotalMilliseconds <= MaxTimeoutMs
            ? value
            : throw new ArgumentOutOfRangeException(
                name, value, $"{name} must be a positive duration of at most {MaxTimeoutMs} ms");
}
