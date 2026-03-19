namespace T0.ProviderSdk;

/// <summary>
/// Typed configuration for a T-0 Network provider.
/// Use <see cref="FromEnvironment"/> to load from environment variables.
/// </summary>
public sealed class T0Config
{
    /// <summary>
    /// Provider's secp256k1 private key in hex format (with or without 0x prefix).
    /// Environment variable: PROVIDER_PRIVATE_KEY
    /// </summary>
    public required string ProviderPrivateKey { get; init; }

    /// <summary>
    /// T-0 Network's public key in hex format (with or without 0x prefix).
    /// Environment variable: NETWORK_PUBLIC_KEY
    /// </summary>
    public required string NetworkPublicKey { get; init; }

    /// <summary>
    /// T-0 Network API endpoint URL.
    /// Environment variable: TZERO_ENDPOINT
    /// </summary>
    public string TZeroEndpoint { get; init; } = "https://api-sandbox.t-0.network";

    /// <summary>
    /// Port for the provider server.
    /// Environment variable: PORT
    /// </summary>
    public int Port { get; init; } = 8080;

    /// <summary>
    /// Loads configuration from environment variables with fail-fast validation.
    /// Required: PROVIDER_PRIVATE_KEY, NETWORK_PUBLIC_KEY.
    /// Optional: TZERO_ENDPOINT (default: sandbox), PORT (default: 8080).
    /// </summary>
    /// <exception cref="InvalidOperationException">Thrown when required environment variables are missing.</exception>
    public static T0Config FromEnvironment()
    {
        var privateKey = Environment.GetEnvironmentVariable("PROVIDER_PRIVATE_KEY");
        if (string.IsNullOrEmpty(privateKey))
            throw new InvalidOperationException(
                "PROVIDER_PRIVATE_KEY is not set. Check your .env file.");

        var networkPublicKey = Environment.GetEnvironmentVariable("NETWORK_PUBLIC_KEY");
        if (string.IsNullOrEmpty(networkPublicKey))
            throw new InvalidOperationException(
                "NETWORK_PUBLIC_KEY is not set. Check your .env file.");

        var endpoint = Environment.GetEnvironmentVariable("TZERO_ENDPOINT");
        var portStr = Environment.GetEnvironmentVariable("PORT");

        return new T0Config
        {
            ProviderPrivateKey = privateKey,
            NetworkPublicKey = networkPublicKey,
            TZeroEndpoint = string.IsNullOrEmpty(endpoint)
                ? "https://api-sandbox.t-0.network"
                : endpoint,
            Port = !string.IsNullOrEmpty(portStr) && int.TryParse(portStr, out var port)
                ? port
                : 8080,
        };
    }
}
