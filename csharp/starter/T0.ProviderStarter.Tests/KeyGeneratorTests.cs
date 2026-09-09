using System.Text.Json;
using T0.ProviderStarter;

namespace T0.ProviderStarter.Tests;

public class KeyGeneratorTests
{
    private static readonly JsonDocument Vectors = LoadVectors();

    private static JsonDocument LoadVectors()
    {
        var testDir = AppContext.BaseDirectory;
        var repoRoot = Path.GetFullPath(Path.Combine(testDir, "..", "..", "..", "..", "..", ".."));
        var vectorsPath = Path.Combine(repoRoot, "cross_test", "test_vectors.json");
        var json = File.ReadAllText(vectorsPath);
        return JsonDocument.Parse(json);
    }

    [Fact]
    public void DerivePublicKey_ShouldMatchTestVector()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;
        var expectedPublicKeyHex = keys.GetProperty("public_key").GetString()!;

        var actualPublicKeyHex = KeyGenerator.DerivePublicKeyHex(privateKeyHex);

        Assert.Equal(expectedPublicKeyHex, actualPublicKeyHex);
    }

    [Fact]
    public void DerivePublicKey_ShouldMatchImpostorVector()
    {
        var keys = Vectors.RootElement.GetProperty("impostor_keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;
        var expectedPublicKeyHex = keys.GetProperty("public_key").GetString()!;

        var actualPublicKeyHex = KeyGenerator.DerivePublicKeyHex(privateKeyHex);

        Assert.Equal(expectedPublicKeyHex, actualPublicKeyHex);
    }

    [Fact]
    public void Generate_ShouldProduceValidKeyStructure()
    {
        var (privateKeyHex, publicKeyHex) = KeyGenerator.Generate();

        // Private key: 32 bytes = 64 hex chars
        Assert.Equal(64, privateKeyHex.Length);
        Assert.True(privateKeyHex.All(c => "0123456789abcdef".Contains(c)));

        // Public key: 65 bytes = 130 hex chars, starts with "04" (uncompressed)
        Assert.Equal(130, publicKeyHex.Length);
        Assert.StartsWith("04", publicKeyHex);
        Assert.True(publicKeyHex.All(c => "0123456789abcdef".Contains(c)));
    }

    [Fact]
    public void Generate_ShouldProduceConsistentKeypair()
    {
        var (privateKeyHex, publicKeyHex) = KeyGenerator.Generate();

        // Re-derive public key from the private key — must match
        var reDerived = KeyGenerator.DerivePublicKeyHex(privateKeyHex);
        Assert.Equal(publicKeyHex, reDerived);
    }

    [Fact]
    public void Generate_ShouldProduceUniqueKeys()
    {
        var (priv1, _) = KeyGenerator.Generate();
        var (priv2, _) = KeyGenerator.Generate();

        Assert.NotEqual(priv1, priv2);
    }
}
