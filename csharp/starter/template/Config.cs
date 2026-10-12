using System.Globalization;
using T0.ProviderSdk;

namespace MyProvider;

/// <summary>
/// A configuration failure. <see cref="Exception.Message"/> is the error line and
/// <see cref="Help"/> is the one line printed under it. Neither includes a stack.
/// </summary>
public sealed class ConfigException : Exception
{
    public ConfigException(string message, string help) : base(message)
    {
        Help = help;
    }

    public string Help { get; }
}

/// <summary>
/// Reads the provider's configuration from environment variables.
/// </summary>
public static class Config
{
    public const string DefaultEndpoint = "https://api-sandbox.t-0.network";

    public const string NetworkKeyHelp =
        "Ask the t-0 team for the network public key and put it in .env.";

    public const string PortHelp =
        "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";

    /// <summary>Absolute path of the .env file looked up in the working directory.</summary>
    public static string EnvPath => Path.GetFullPath(".env");

    public static string PrivateKeyMissingHelp =>
        ".env is read from the working directory, and we looked in " + EnvPath + ". " +
        "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
        "Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable.";

    public static string MissingEnvNotice =>
        "No .env at " + EnvPath + " — taking configuration from the environment instead";

    /// <summary>
    /// Loads .env from the working directory. A missing file is not a failure: one notice
    /// on stderr, then the process environment is used. Variables already set win over the file.
    /// </summary>
    public static void LoadEnv()
    {
        var path = EnvPath;
        if (!File.Exists(path))
        {
            Console.Error.WriteLine(MissingEnvNotice);
            return;
        }

        DotNetEnv.Env.NoClobber().Load(path);
    }

    /// <summary>
    /// Loads configuration from environment variables with fail-fast validation.
    /// Required: PROVIDER_PRIVATE_KEY, NETWORK_PUBLIC_KEY.
    /// Optional: TZERO_ENDPOINT (default: sandbox), PORT (default: 8080).
    /// An empty value counts as unset. Both keys are trimmed. The SDK checks the keys
    /// themselves when the client and the server are built.
    /// </summary>
    /// <exception cref="ConfigException">A required variable is missing, or PORT is not an integer from 1 to 65535.</exception>
    public static T0Config FromEnvironment()
    {
        var privateKey = Environment.GetEnvironmentVariable("PROVIDER_PRIVATE_KEY")?.Trim();
        if (string.IsNullOrEmpty(privateKey))
            throw new ConfigException("PROVIDER_PRIVATE_KEY is not set", PrivateKeyMissingHelp);

        var networkPublicKey = Environment.GetEnvironmentVariable("NETWORK_PUBLIC_KEY")?.Trim();
        if (string.IsNullOrEmpty(networkPublicKey))
            throw new ConfigException("NETWORK_PUBLIC_KEY is not set", NetworkKeyHelp);

        var endpoint = Environment.GetEnvironmentVariable("TZERO_ENDPOINT")?.Trim();
        if (string.IsNullOrEmpty(endpoint))
            endpoint = DefaultEndpoint;

        return new T0Config
        {
            ProviderPrivateKey = privateKey,
            NetworkPublicKey = networkPublicKey,
            TZeroEndpoint = endpoint,
            Port = ParsePort(Environment.GetEnvironmentVariable("PORT")),
        };
    }

    private static int ParsePort(string? raw)
    {
        var portStr = raw?.Trim() ?? "";
        if (portStr.Length == 0)
            return 8080;
        if (!int.TryParse(portStr, NumberStyles.Integer, CultureInfo.InvariantCulture, out var port)
            || port is < 1 or > 65535)
            throw new ConfigException($"PORT is not a valid port number: {portStr}", PortHelp);
        return port;
    }
}
