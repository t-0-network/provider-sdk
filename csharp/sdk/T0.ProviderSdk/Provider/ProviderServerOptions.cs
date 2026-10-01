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
    /// Maximum request body size in bytes. Default: 10 MiB.
    /// </summary>
    public long MaxBodySize { get; set; } = 10 * 1024 * 1024;
}
