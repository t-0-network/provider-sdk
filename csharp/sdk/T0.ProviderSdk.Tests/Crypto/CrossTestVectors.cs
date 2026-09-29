using System.Buffers.Binary;
using System.Text;
using System.Text.Json;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
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
    /// <c>first_envelope</c> includes the 5-byte prefix (a signer below the gRPC framer, like
    /// <see cref="SigningDelegatingHandler"/>); <c>first_payload</c> does not. The first_envelope
    /// cases also run through the handler.
    /// </summary>
    [Fact]
    public async Task StreamSigningCases_ShouldMatchVectorBytes()
    {
        var keys = Vectors.RootElement.GetProperty("keys");
        var privateKeyHex = keys.GetProperty("private_key").GetString()!;
        var signer = Signer.FromHex(privateKeyHex);

        var cases = Vectors.RootElement.GetProperty("stream_signing_cases");
        Assert.NotEmpty(cases.EnumerateArray());

        foreach (var vec in cases.EnumerateArray())
        {
            var name = vec.GetProperty("name").GetString()!;
            var body = HexUtils.HexToBytes(vec.GetProperty("body_hex").GetString()!);
            var covers = vec.GetProperty("covers").GetString()!;
            var timestampMs = vec.GetProperty("timestamp_ms").GetInt64();
            var expectedSignature = vec.GetProperty("expected_signature").GetString()!;

            var signed = covers switch
            {
                "first_envelope" => FirstEnvelope(body),
                "first_payload" => FirstEnvelope(body)[5..],
                _ => throw new InvalidOperationException($"{name}: unknown covers value {covers}"),
            };
            Assert.Equal(vec.GetProperty("signed_hex").GetString()!, HexUtils.BytesToHex(signed));

            var digest = Keccak256.Hash(signed, Headers.EncodeTimestamp(timestampMs));
            Assert.Equal(vec.GetProperty("expected_hash").GetString()!, HexUtils.BytesToHex(digest));
            Assert.Equal(expectedSignature, HexUtils.BytesToHex(signer.Sign(digest).Signature[..64]));

            if (covers != "first_envelope")
                continue;

            // The C# client speaks gRPC only; a Connect envelope has the gRPC frame's layout.
            var contentType = vec.GetProperty("content_type").GetString()!;
            if (!contentType.StartsWith("application/grpc", StringComparison.Ordinal))
                contentType = "application/grpc";

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
            Assert.Equal(expectedSignature,
                HexUtils.BytesToHex(StreamingTestHelpers.HeaderBytes(request, Headers.Signature)[..64]));
            Assert.Equal(body, inner.Body.ToArray());
        }
    }

    private static byte[] FirstEnvelope(byte[] body)
    {
        if (body.Length == 0)
            return [];
        var length = BinaryPrimitives.ReadUInt32BigEndian(body.AsSpan(1, 4));
        return body[..(5 + (int)length)];
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
