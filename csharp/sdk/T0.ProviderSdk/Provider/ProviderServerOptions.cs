namespace T0.ProviderSdk.Provider;

/// <summary>
/// Configuration options for provider server signature verification.
/// </summary>
public sealed class ProviderServerOptions
{
    /// <summary>
    /// The T-0 Network public key in hex format (with or without 0x prefix). Required:
    /// <see cref="SignatureVerificationMiddleware"/> throws <see cref="ArgumentException"/>
    /// at startup for a missing or malformed key. Surrounding whitespace is trimmed.
    /// </summary>
    public string NetworkPublicKeyHex { get; set; } = "";

    /// <summary>
    /// The largest request body a provider server accepts unless <see cref="MaxBodySize"/> sets
    /// another: the whole HTTP body of a unary call, its gRPC prefix included.
    /// </summary>
    public const long DefaultMaxBodySize = 10 * 1024 * 1024; // 10 MiB

    /// <summary>
    /// How far X-Signature-Timestamp may be from the server's clock, either way. Fixed: no option
    /// changes it.
    /// </summary>
    public static readonly TimeSpan TimestampWindow = TimeSpan.FromSeconds(60);

    /// <summary>
    /// The largest request body accepted, in bytes. Default: <see cref="DefaultMaxBodySize"/>, also
    /// for a value of 0 or less.
    /// </summary>
    public long MaxBodySize { get; set; } = DefaultMaxBodySize;
}
