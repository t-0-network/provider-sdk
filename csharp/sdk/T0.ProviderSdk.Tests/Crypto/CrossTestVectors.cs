using System.Buffers.Binary;
using System.Text;
using System.Text.Json;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Crypto;

/// <summary>
/// Tests using shared cross-language test vectors from cross_test/test_vectors.json.
/// Ensures C# crypto produces identical output to Go, Java, Node, and Python.
/// </summary>
public class CrossTestVectors
{
    private static readonly JsonDocument Vectors = LoadVectors();

    private static JsonDocument LoadVectors()
    {
        // Path from test output directory (bin/Debug/net10.0/) to cross_test/
        var testDir = AppContext.BaseDirectory;
        var repoRoot = Path.GetFullPath(Path.Combine(testDir, "..", "..", "..", "..", "..", ".."));
        var vectorsPath = Path.Combine(repoRoot, "cross_test", "test_vectors.json");
        var json = File.ReadAllText(vectorsPath);
        return JsonDocument.Parse(json);
    }

    [Fact]
    public void Keccak256_ShouldMatchAllVectors()
    {
        var keccakVectors = Vectors.RootElement.GetProperty("keccak256");

        foreach (var vec in keccakVectors.EnumerateArray())
        {
            var input = vec.GetProperty("input").GetString()!;
            var expectedHash = vec.GetProperty("hash").GetString()!;

            var hash = Keccak256.Hash(Encoding.UTF8.GetBytes(input));
            Assert.Equal(expectedHash, HexUtils.BytesToHex(hash));
        }
    }

    [Fact]
    public void KeyDerivation_ShouldMatchVectorPublicKey()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;
        var expectedPublicKeyHex = keys.GetProperty("public_key").GetString()!;

        var signer = Signer.FromHex(privateKeyHex);
        Assert.Equal(expectedPublicKeyHex, HexUtils.BytesToHex(signer.GetPublicKey()));
    }

    [Fact]
    public void RequestHash_ShouldMatchExpected()
    {
        var rs = Vectors.RootElement.GetProperty("request_signing");
        var body = rs.GetProperty("body").GetString()!;
        var timestampMs = rs.GetProperty("timestamp_ms").GetInt64();
        var expectedHash = rs.GetProperty("expected_hash").GetString()!;

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);

        var hash = Keccak256.Hash(Encoding.UTF8.GetBytes(body), tsBytes);
        Assert.Equal(expectedHash, HexUtils.BytesToHex(hash));
    }

    [Fact]
    public void SignVerifyRoundTrip_ShouldSucceed()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;

        var signer = Signer.FromHex(privateKeyHex);
        var digest = Keccak256.Hash(Encoding.UTF8.GetBytes("round trip test"));

        var result = signer.Sign(digest);

        Assert.Equal(65, result.Signature.Length);
        Assert.Equal(65, result.PublicKey.Length);

        var valid = SignatureVerifier.Verify(signer.GetPublicKey(), digest, result.Signature);
        Assert.True(valid);
    }

    [Fact]
    public void SignVerifyRoundTrip_64ByteSignature_ShouldSucceed()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;

        var signer = Signer.FromHex(privateKeyHex);
        var digest = Keccak256.Hash(Encoding.UTF8.GetBytes("64 byte sig test"));

        var result = signer.Sign(digest);

        // Strip the recovery byte to get 64-byte signature
        var sig64 = result.Signature[..64];

        var valid = SignatureVerifier.Verify(signer.GetPublicKey(), digest, sig64);
        Assert.True(valid);
    }
}
