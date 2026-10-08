using System.Globalization;
using T0.ProviderSdk;

namespace MyProvider;

/// <summary>
/// Reads the provider's configuration from environment variables.
/// </summary>
public static class Config
{
    /// <summary>
    /// Loads configuration from environment variables with fail-fast validation.
    /// Required: PROVIDER_PRIVATE_KEY, NETWORK_PUBLIC_KEY.
    /// Optional: TZERO_ENDPOINT (default: sandbox), PORT (default: 8080).
    /// </summary>
    /// <exception cref="InvalidOperationException">Thrown when a required variable is missing or PORT is not an integer from 1 to 65535.</exception>
    public static T0Config FromEnvironment()
    {
        var privateKey = Environment.GetEnvironmentVariable("PROVIDER_PRIVATE_KEY");
        if (string.IsNullOrEmpty(privateKey))
            throw new InvalidOperationException(
                "PROVIDER_PRIVATE_KEY is not set. Check your .env file.");

        var networkPublicKey = Environment.GetEnvironmentVariable("NETWORK_PUBLIC_KEY")?.Trim();
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
            Port = ParsePort(portStr),
        };
    }

    private static int ParsePort(string? portStr)
    {
        if (string.IsNullOrEmpty(portStr))
            return 8080;
        if (!int.TryParse(portStr, NumberStyles.None, CultureInfo.InvariantCulture, out var port)
            || port is < 1 or > 65535)
            throw new InvalidOperationException(
                $"PORT must be an integer from 1 to 65535 (got \"{portStr}\").");
        return port;
    }
}
