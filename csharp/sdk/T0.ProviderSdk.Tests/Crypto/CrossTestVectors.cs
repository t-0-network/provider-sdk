using System.Buffers.Binary;
using System.Globalization;
using System.Text;
using System.Text.Json;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.Network;

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

    [Fact]
    public void RequestSignature_ShouldMatchExpected()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var rs = Vectors.RootElement.GetProperty("request_signing");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;
        var expectedSignature = rs.GetProperty("expected_signature").GetString()!;

        var body = Encoding.UTF8.GetBytes(rs.GetProperty("body").GetString()!);
        var timestampMs = rs.GetProperty("timestamp_ms").GetInt64();
        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);

        var digest = Keccak256.Hash(body, tsBytes);
        Assert.Equal(rs.GetProperty("expected_hash").GetString()!, HexUtils.BytesToHex(digest));

        var signer = Signer.FromHex(privateKeyHex);
        var result = signer.Sign(digest);
        // Compare first 64 bytes (r+s) against the cross-language test vector
        Assert.Equal(expectedSignature, HexUtils.BytesToHex(result.Signature[..64]));
    }

    /// <summary>
    /// Bodies the string-valued request_signing block cannot express: binary, gRPC-framed,
    /// empty, and one whose signature has a leading zero byte — the case a signer that trims
    /// instead of padding to a fixed 32 bytes fails, and only that case.
    /// </summary>
    [Fact]
    public void RequestSigningCases_ShouldMatchVectorBytes()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var signer = Signer.FromHex(keys.GetProperty("private_key").GetString()!);

        var cases = Vectors.RootElement.GetProperty("request_signing_cases");
        Assert.NotEmpty(cases.EnumerateArray());

        foreach (var vec in cases.EnumerateArray())
        {
            var digest = RequestDigest(vec);
            Assert.Equal(vec.GetProperty("expected_hash").GetString()!, HexUtils.BytesToHex(digest));

            var result = signer.Sign(digest);
            Assert.Equal(
                vec.GetProperty("expected_signature").GetString()!,
                HexUtils.BytesToHex(result.Signature[..64]));
        }
    }

    /// <summary>
    /// The presented-request cases, including the ones a provider has to refuse.
    /// </summary>
    [Fact]
    public void SignatureVerification_ShouldMatchVectorOutcomes()
    {
        var cases = Vectors.RootElement.GetProperty("signature_verification");
        Assert.NotEmpty(cases.EnumerateArray());

        foreach (var vec in cases.EnumerateArray())
        {
            var publicKey = HexUtils.HexToBytes(vec.GetProperty("public_key").GetString()!);
            var signature = HexUtils.HexToBytes(vec.GetProperty("signature").GetString()!);

            Assert.Equal(
                vec.GetProperty("valid").GetBoolean(),
                SignatureVerifier.Verify(publicKey, RequestDigest(vec), signature));
        }
    }

    /// <summary>
    /// The rule the middleware parses the configured network key and X-Public-Key with, and the
    /// deprecated <see cref="SignatureVerifier.ParsePublicKeyHex"/> with it: valid keys in their
    /// 65-byte uncompressed encoding, everything else rejected.
    /// </summary>
    [Fact]
    public void PublicKeyParsing_ShouldMatchVectorOutcomes()
    {
        var cases = Vectors.RootElement.GetProperty("public_key_parsing");
        Assert.NotEmpty(cases.EnumerateArray());

#pragma warning disable CS0618 // ParsePublicKeyHex is obsolete; it must still follow the rule
        Func<string, byte[]>[] parsers = [SignatureVerificationMiddleware.ParsePublicKey, SignatureVerifier.ParsePublicKeyHex];
#pragma warning restore CS0618
        foreach (var parse in parsers)
        {
            foreach (var vec in cases.EnumerateArray())
            {
                var name = $"{parse.Method.Name} {vec.GetProperty("name").GetString()}";
                var expected = vec.GetProperty("valid").GetBoolean()
                    ? vec.GetProperty("uncompressed").GetString()!
                    : "rejected";

                string parsed;
                try
                {
                    parsed = HexUtils.BytesToHex(parse(vec.GetProperty("input").GetString()!));
                }
                catch (Exception e) when (e is FormatException or ArgumentException or ArithmeticException)
                {
                    parsed = "rejected";
                }
                Assert.Equal($"{name}: {expected}", $"{name}: {parsed}");
            }
        }
    }

    /// <summary>
    /// The rule the middleware parses X-Signature-Timestamp with: decimal digits only, at most
    /// <see cref="long.MaxValue"/>.
    /// </summary>
    [Fact]
    public void TimestampParsing_ShouldMatchVectorOutcomes()
    {
        var cases = Vectors.RootElement.GetProperty("timestamp_parsing");
        Assert.NotEmpty(cases.EnumerateArray());

        foreach (var vec in cases.EnumerateArray())
        {
            var name = vec.GetProperty("name").GetString();
            var expected = vec.GetProperty("valid").GetBoolean()
                ? vec.GetProperty("value").GetString()!
                : "rejected";

            var parsed = SignatureVerificationMiddleware.TryParseTimestamp(
                vec.GetProperty("input").GetString(), out var timestampMs)
                ? timestampMs.ToString(CultureInfo.InvariantCulture)
                : "rejected";
            Assert.Equal($"{name}: {expected}", $"{name}: {parsed}");
        }
    }

    /// <summary>
    /// The <c>first_envelope</c> cases sent through <see cref="SigningDelegatingHandler"/>: the
    /// signature covers the first envelope, prefix included, and the body goes out unchanged.
    /// </summary>
    [Fact]
    public async Task StreamSigningCases_ShouldMatchVectorBytes()
    {
        var privateKeyHex = Vectors.RootElement.GetProperty("keys").GetProperty("private_key").GetString()!;
        var cases = Vectors.RootElement.GetProperty("stream_signing_cases").EnumerateArray()
            .Where(vec => vec.GetProperty("covers").GetString() == "first_envelope")
            .ToList();
        Assert.NotEmpty(cases);

        foreach (var vec in cases)
        {
            var body = HexUtils.HexToBytes(vec.GetProperty("body_hex").GetString()!);
            var timestampMs = vec.GetProperty("timestamp_ms").GetInt64();

            var contentType = vec.GetProperty("content_type").GetString()!;
            var inner = new RecordingHandler();
            var handler = new SigningDelegatingHandler(
                Signer.FromHex(privateKeyHex),
                new FixedTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs)))
            {
                InnerHandler = inner
            };
            using var client = new HttpClient(handler);
            using var response = await client.SendAsync(new HttpRequestMessage(HttpMethod.Post, "http://example.com/")
            {
                Content = new PushContent(stream => stream.WriteAsync(body).AsTask(), contentType)
            }).WithTimeout();

            var request = await inner.Received.Task.WithTimeout();
            Assert.Equal(timestampMs.ToString(), request.Headers.GetValues(Headers.SignatureTimestamp).Single());
            Assert.Equal(vec.GetProperty("expected_signature").GetString()!,
                HexUtils.BytesToHex(StreamingTestHelpers.HeaderBytes(request, Headers.Signature)[..64]));
            Assert.Equal(body, inner.Body.ToArray());
        }
    }

    /// <summary>
    /// What the middleware hashes: the raw body with the little-endian timestamp appended.
    /// </summary>
    private static byte[] RequestDigest(JsonElement vec)
    {
        var body = HexUtils.HexToBytes(vec.GetProperty("body_hex").GetString()!);

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)vec.GetProperty("timestamp_ms").GetInt64());

        return Keccak256.Hash(body, tsBytes);
    }
}
