using Xunit;

namespace MyProvider;

/// <summary>
/// Configuration only. This does not start a server.
/// </summary>
public class ConfigTests
{
    private const string PrivateKey = "4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2";
    private const string NetworkKey = "0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b";
    private const string Sandbox = "https://api-sandbox.t-0.network";

    private static readonly object Gate = new();

    public static IEnumerable<object[]> Cases()
    {
        yield return Row("both keys trimmed",
            privateKey: "  " + PrivateKey + " \n",
            networkKey: "\t" + NetworkKey + " ",
            expectedPrivate: PrivateKey,
            expectedNetwork: NetworkKey);

        yield return Row("blank private key",
            privateKey: "   ",
            networkKey: NetworkKey,
            error: "PROVIDER_PRIVATE_KEY is not set",
            privateKeyHelp: true);

        yield return Row("blank network key",
            privateKey: PrivateKey,
            networkKey: "  ",
            error: "NETWORK_PUBLIC_KEY is not set",
            help: Config.NetworkKeyHelp);

        yield return Row("bad private key",
            privateKey: "not-a-key",
            networkKey: NetworkKey,
            error: "PROVIDER_PRIVATE_KEY is not usable: private key must be 32 bytes (64 hex characters)",
            help: Config.PrivateKeyUnusableHelp);

        yield return Row("PORT blank", port: "", expectedPort: 8080);
        yield return Row("PORT whitespace", port: "   ", expectedPort: 8080);
        yield return Row("PORT 8080", port: "8080", expectedPort: 8080);
        yield return Row("PORT padded 8080", port: " 8080 ", expectedPort: 8080);
        yield return Row("PORT 0",
            port: "0",
            error: "PORT is not a valid port number: 0",
            help: Config.PortHelp);
        yield return Row("PORT http",
            port: "http",
            error: "PORT is not a valid port number: http",
            help: Config.PortHelp);

        yield return Row("blank TZERO_ENDPOINT", endpoint: "   ", expectedEndpoint: Sandbox);
        yield return Row("missing .env notice", notice: true);
    }

    [Theory]
    [MemberData(nameof(Cases))]
    public void Load(Case c)
    {
        lock (Gate)
        {
            var snapshot = Snapshot();
            var previousDir = Directory.GetCurrentDirectory();
            var previousError = Console.Error;
            var stderr = new StringWriter();
            string? temp = null;
            try
            {
                Console.SetError(stderr);
                Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", c.PrivateKey);
                Environment.SetEnvironmentVariable("NETWORK_PUBLIC_KEY", c.NetworkKey);
                Environment.SetEnvironmentVariable("TZERO_ENDPOINT", c.Endpoint);
                Environment.SetEnvironmentVariable("PORT", c.Port);

                if (c.Notice)
                {
                    temp = Directory.CreateTempSubdirectory("my-provider-config").FullName;
                    Directory.SetCurrentDirectory(temp);
                    Config.LoadEnv();
                    var path = Path.GetFullPath(".env");
                    Assert.True(Path.IsPathRooted(path));
                    Assert.Equal(Path.Combine(Directory.GetCurrentDirectory(), ".env"), path);
                    Assert.StartsWith("my-provider-config", new DirectoryInfo(Directory.GetCurrentDirectory()).Name);
                    Assert.False(File.Exists(path));
                    Assert.Equal(
                        "No .env at " + path + " — taking configuration from the environment instead",
                        stderr.ToString().TrimEnd('\r', '\n'));
                    return;
                }

                if (c.Error is null)
                {
                    var config = Config.FromEnvironment();
                    Assert.Equal(c.ExpectedPrivate, config.ProviderPrivateKey);
                    Assert.Equal(c.ExpectedNetwork, config.NetworkPublicKey);
                    Assert.Equal(c.ExpectedEndpoint, config.TZeroEndpoint);
                    Assert.Equal(c.ExpectedPort, config.Port);
                    return;
                }

                var ex = Assert.Throws<ConfigException>(() => Config.FromEnvironment());
                Assert.Equal(c.Error, ex.Message);
                Assert.Equal(c.PrivateKeyHelp ? PrivateKeyMissingHelp() : c.Help, ex.Help);
            }
            finally
            {
                Console.SetError(previousError);
                Directory.SetCurrentDirectory(previousDir);
                if (temp is not null)
                    Directory.Delete(temp, recursive: true);
                Restore(snapshot);
            }
        }
    }

    private static string PrivateKeyMissingHelp() =>
        ".env is read from the working directory, and we looked in " + Path.GetFullPath(".env") + ". " +
        "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
        "Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable.";

    private static object[] Row(
        string name,
        string? privateKey = PrivateKey,
        string? networkKey = NetworkKey,
        string? endpoint = null,
        string? port = null,
        string? expectedPrivate = null,
        string? expectedNetwork = null,
        string? expectedEndpoint = null,
        int expectedPort = 8080,
        string? error = null,
        string? help = null,
        bool privateKeyHelp = false,
        bool notice = false) =>
        [
            new Case(name, privateKey, networkKey, endpoint, port,
                expectedPrivate ?? (privateKey?.Trim() ?? ""),
                expectedNetwork ?? (networkKey?.Trim() ?? ""),
                expectedEndpoint ?? Sandbox,
                expectedPort, error, help, privateKeyHelp, notice),
        ];

    private static Dictionary<string, string?> Snapshot() => new()
    {
        ["PROVIDER_PRIVATE_KEY"] = Environment.GetEnvironmentVariable("PROVIDER_PRIVATE_KEY"),
        ["NETWORK_PUBLIC_KEY"] = Environment.GetEnvironmentVariable("NETWORK_PUBLIC_KEY"),
        ["TZERO_ENDPOINT"] = Environment.GetEnvironmentVariable("TZERO_ENDPOINT"),
        ["PORT"] = Environment.GetEnvironmentVariable("PORT"),
    };

    private static void Restore(Dictionary<string, string?> snapshot)
    {
        foreach (var (name, value) in snapshot)
            Environment.SetEnvironmentVariable(name, value);
    }

    public sealed record Case(
        string Name,
        string? PrivateKey,
        string? NetworkKey,
        string? Endpoint,
        string? Port,
        string ExpectedPrivate,
        string ExpectedNetwork,
        string ExpectedEndpoint,
        int ExpectedPort,
        string? Error,
        string? Help,
        bool PrivateKeyHelp,
        bool Notice)
    {
        public override string ToString() => Name;
    }
}
