namespace T0.ProviderSdk;

/// <summary>
/// Typed configuration for a T-0 Network provider.
/// </summary>
public sealed class T0Config
{
    /// <summary>
    /// Provider's secp256k1 private key in hex format (with or without 0x prefix).
    /// </summary>
    public required string ProviderPrivateKey { get; init; }

    /// <summary>
    /// T-0 Network's public key in hex format (with or without 0x prefix).
    /// </summary>
    public required string NetworkPublicKey { get; init; }

    /// <summary>
    /// T-0 Network API endpoint URL.
    /// </summary>
    public string TZeroEndpoint { get; init; } = "https://api-sandbox.t-0.network";

    /// <summary>
    /// Port for the provider server.
    /// </summary>
    public int Port { get; init; } = 8080;
}
