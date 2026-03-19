namespace T0.ProviderSdk.Provider;

/// <summary>
/// Configuration options for provider server signature verification.
/// </summary>
public sealed class ProviderServerOptions
{
    /// <summary>
    /// The T-0 Network public key in hex format (with or without 0x prefix).
    /// If empty, signature verification is disabled.
    /// </summary>
    public string NetworkPublicKeyHex { get; set; } = "";

    /// <summary>
    /// Maximum request body size in bytes. Default: 1 MB.
    /// </summary>
    public long MaxBodySize { get; set; } = 1_048_576;
}
