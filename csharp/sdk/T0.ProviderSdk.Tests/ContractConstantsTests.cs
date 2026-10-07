using System.Reflection;
using System.Text.Json;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;

namespace T0.ProviderSdk.Tests;

/// <summary>
/// The named constants of the SDK against <c>constants</c> in cross_test/test_vectors.json, the
/// file every SDK compares its constants with.
/// </summary>
public class ContractConstantsTests
{
    private static readonly string VectorsPath = Path.GetFullPath(Path.Combine(
        AppContext.BaseDirectory, "..", "..", "..", "..", "..", "..", "cross_test", "test_vectors.json"));

    [Fact]
    public void ConstantsMatchTheSharedFile()
    {
        using var vectors = JsonDocument.Parse(File.ReadAllText(VectorsPath));
        var constants = vectors.RootElement.GetProperty("constants");

        var sdk = new Dictionary<string, object>
        {
            ["default_max_body_size"] = ProviderServerOptions.DefaultMaxBodySize,
            ["timestamp_window_ms"] = (long)ProviderServerOptions.TimestampWindow.TotalMilliseconds,
            ["public_key_header"] = Headers.PublicKey,
            ["signature_header"] = Headers.Signature,
            ["signature_timestamp_header"] = Headers.SignatureTimestamp,
            ["default_base_url"] = NetworkClientOptions.DefaultBaseUrl,
            ["default_timeout_ms"] = (long)NetworkClientOptions.DefaultTimeout.TotalMilliseconds,
            ["default_stream_timeout_ms"] = (long)NetworkClientOptions.DefaultStreamTimeout.TotalMilliseconds,
            ["max_timeout_ms"] = (long)NetworkClientOptions.MaxTimeout.TotalMilliseconds,
        };

        Assert.Equal(
            constants.EnumerateObject().Select(p => p.Name).Order(),
            sdk.Keys.Order());
        foreach (var property in constants.EnumerateObject())
        {
            object expected = property.Value.ValueKind == JsonValueKind.Number
                ? property.Value.GetInt64()
                : property.Value.GetString()!;
            Assert.Equal($"{property.Name}: {expected}", $"{property.Name}: {sdk[property.Name]}");
        }
    }

    /// <summary>
    /// <see cref="Messages"/> against <c>messages</c>: one member per message C# raises (every
    /// message whose <c>messages_scope</c> lists csharp or that has no scope), named in PascalCase. A
    /// message with placeholders is a method whose parameters are named after them; each is filled
    /// with a sample value.
    /// </summary>
    [Fact]
    public void MessagesMatchTheSharedFile()
    {
        using var vectors = JsonDocument.Parse(File.ReadAllText(VectorsPath));
        var scope = vectors.RootElement.GetProperty("messages_scope");
        var expected = vectors.RootElement.GetProperty("messages").EnumerateObject()
            .Where(m => !scope.TryGetProperty(m.Name, out var sdks)
                || sdks.EnumerateArray().Any(s => s.GetString() == "csharp"))
            .ToDictionary(m => PascalCase(m.Name), m => m.Value.GetString()!);

        const BindingFlags Members = BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.DeclaredOnly;
        var fields = typeof(Messages).GetFields(Members).Where(f => f.IsLiteral).ToDictionary(f => f.Name);
        var methods = typeof(Messages).GetMethods(Members).ToDictionary(m => m.Name);
        Assert.Equal(expected.Keys.Order(), fields.Keys.Concat(methods.Keys).Order());

        foreach (var (name, text) in expected)
        {
            string want = text, actual;
            if (fields.TryGetValue(name, out var field))
            {
                actual = (string)field.GetRawConstantValue()!;
            }
            else
            {
                var parameters = methods[name].GetParameters();
                var samples = parameters
                    .Select(p => p.ParameterType == typeof(long) ? (object)12345L : $"sample {p.Name}")
                    .ToArray();
                foreach (var (parameter, sample) in parameters.Zip(samples))
                    want = want.Replace($"{{{parameter.Name}}}", sample.ToString());
                actual = (string)methods[name].Invoke(null, samples)!;
            }
            Assert.Equal($"{name}: {want}", $"{name}: {actual}");
        }
    }

    /// <summary>
    /// The body limit the server and the middleware use for the max body size they are given.
    /// </summary>
    [Fact]
    public void MaxBodySizeCasesMatchTheSharedFile()
    {
        using var vectors = JsonDocument.Parse(File.ReadAllText(VectorsPath));
        var cases = vectors.RootElement.GetProperty("max_body_size_cases");
        Assert.NotEmpty(cases.EnumerateArray());
        var signer = T0.ProviderSdk.Crypto.Signer.FromHex(new string('2', 64));

        foreach (var c in cases.EnumerateArray())
        {
            var name = c.GetProperty("name").GetString();
            var input = c.GetProperty("input").GetInt64();
            var expected = c.GetProperty("limit").GetInt64();

            var config = new T0Config { ProviderPrivateKey = "", NetworkPublicKey = signer.GetPublicKeyHexPrefixed() };
            var server = new T0ProviderServer(config, signer).WithMaxBodySize(input);
            var middleware = new SignatureVerificationMiddleware(_ => Task.CompletedTask,
                new ProviderServerOptions { NetworkPublicKeyHex = signer.GetPublicKeyHexPrefixed(), MaxBodySize = input });
            Assert.Equal($"{name}: {expected} {expected}", $"{name}: {server.MaxBodySize} {middleware.MaxBodySize}");
        }
    }

    private static string PascalCase(string snake) =>
        string.Concat(snake.Split('_').Select(word => char.ToUpperInvariant(word[0]) + word[1..]));
}
