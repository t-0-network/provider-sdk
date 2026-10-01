using System.Buffers.Binary;
using System.Text;
using Microsoft.AspNetCore.Http;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Provider;

namespace T0.ProviderSdk.Tests.Provider;

public class SignatureVerificationMiddlewareTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private readonly Signer _signer = Signer.FromHex(TestPrivateKey);

    private ProviderServerOptions CreateOptions() => new()
    {
        NetworkPublicKeyHex = _signer.GetPublicKeyHexPrefixed()
    };

    private static SignatureVerificationMiddleware NewMiddleware(string? key) =>
        new(_ => Task.CompletedTask, new ProviderServerOptions { NetworkPublicKeyHex = key! });

    private static T0ProviderServer NewServer(string key) =>
        new(new T0Config { ProviderPrivateKey = TestPrivateKey, NetworkPublicKey = key, Port = 0 }, Signer.FromHex(TestPrivateKey));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  \n")]
    public void MissingNetworkPublicKey_ShouldThrowAtStartup(string? key)
    {
        var ex = Assert.Throws<ArgumentException>(() => NewMiddleware(key));
        Assert.Equal("network public key is not set", ex.Message);
        ex = Assert.Throws<ArgumentException>(() => NewServer(key!));
        Assert.Equal("network public key is not set", ex.Message);
    }

    public static TheoryData<string> MalformedNetworkPublicKeys()
    {
        var valid = Signer.FromHex(TestPrivateKey).GetPublicKeyHex();
        return new TheoryData<string>
        {
            "0xnot-a-key",
            "0x",
            valid[..128],
            "02" + valid[2..],
            "06" + valid[2..],
            "04" + new string('0', 128),
        };
    }

    [Theory]
    [MemberData(nameof(MalformedNetworkPublicKeys))]
    public void MalformedNetworkPublicKey_ShouldThrowAtStartup(string key)
    {
        var ex = Assert.Throws<ArgumentException>(() => NewMiddleware(key));
        Assert.StartsWith("invalid network public key: ", ex.Message);
        ex = Assert.Throws<ArgumentException>(() => NewServer(key));
        Assert.StartsWith("invalid network public key: ", ex.Message);
    }

    [Fact]
    public void PaddedNetworkPublicKey_ShouldBeAccepted()
    {
        var key = $"  {_signer.GetPublicKeyHexPrefixed()}\n";
        NewMiddleware(key);
        NewServer(key);
    }

    [Fact]
    public void CompressedNetworkPublicKey_ShouldBeAccepted()
    {
        var key = "0x" + CompressedPublicKeyHex(_signer.GetPublicKey());
        Assert.Equal(_signer.GetPublicKey(), SignatureVerificationMiddleware.ParseNetworkPublicKey(key));
        NewMiddleware(key);
        NewServer(key);
    }

    public static TheoryData<string> NetworkPublicKeyHeaders()
    {
        var publicKey = Signer.FromHex(TestPrivateKey).GetPublicKey();
        return new TheoryData<string>
        {
            "0x" + CompressedPublicKeyHex(publicKey),
            "0X" + CompressedPublicKeyHex(publicKey),
            CompressedPublicKeyHex(publicKey),
            "0X" + HexUtils.BytesToHex(publicKey),
        };
    }

    [Theory]
    [MemberData(nameof(NetworkPublicKeyHeaders))]
    public async Task NetworkPublicKeyHeader_EitherForm_ShouldPassThrough(string publicKeyHeader)
    {
        var (handlerCalled, _) = await InvokeSignedAsync(publicKeyHeader);
        Assert.True(handlerCalled);
    }

    public static TheoryData<string?> NonHexPublicKeyHeaders()
    {
        var valid = Signer.FromHex(TestPrivateKey).GetPublicKeyHexPrefixed();
        return new TheoryData<string?>
        {
            null,
            "0x",
            "0xnot-a-key",
            valid[..^1],
            valid[..66] + " " + valid[66..],
            valid + "zz",
            " " + valid,
        };
    }

    [Theory]
    [MemberData(nameof(NonHexPublicKeyHeaders))]
    public async Task NonHexPublicKeyHeader_ShouldReturnInvalidArgument(string? publicKeyHeader)
    {
        var (handlerCalled, context) = await InvokeSignedAsync(publicKeyHeader);
        Assert.False(handlerCalled);
        Assert.Equal("3", context.Response.Headers["grpc-status"].ToString()); // InvalidArgument = 3
        Assert.Equal($"missing or invalid header: {Headers.PublicKey}", context.Response.Headers["grpc-message"].ToString());
    }

    public static TheoryData<string> NonKeyPublicKeyHeaders()
    {
        var valid = Signer.FromHex(TestPrivateKey).GetPublicKeyHex();
        return new TheoryData<string>
        {
            "0x" + valid[..128],
            "0x02" + valid[2..],
            "0x04" + valid[2..66],
            "0x06" + valid[2..],
            "0x04" + new string('0', 128),
        };
    }

    [Theory]
    [MemberData(nameof(NonKeyPublicKeyHeaders))]
    public async Task NonKeyPublicKeyHeader_ShouldReturnUnauthenticated(string publicKeyHeader)
    {
        var (handlerCalled, context) = await InvokeSignedAsync(publicKeyHeader);
        Assert.False(handlerCalled);
        Assert.Equal("16", context.Response.Headers["grpc-status"].ToString()); // Unauthenticated = 16
        Assert.Equal("unknown public key", context.Response.Headers["grpc-message"].ToString());
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task OtherPublicKeyHeader_ShouldReturnUnauthenticated(bool compressed)
    {
        var other = Signer.FromHex(new string('2', 64)).GetPublicKey();
        var (handlerCalled, context) = await InvokeSignedAsync(
            "0x" + (compressed ? CompressedPublicKeyHex(other) : HexUtils.BytesToHex(other)));
        Assert.False(handlerCalled);
        Assert.Equal("16", context.Response.Headers["grpc-status"].ToString());
        Assert.Equal("unknown public key", context.Response.Headers["grpc-message"].ToString());
    }

    [Fact]
    public async Task ValidSignature_ShouldPassThrough()
    {
        var body = "test body"u8.ToArray();
        var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs));

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);
        var digest = Keccak256.Hash(body, tsBytes);
        var result = _signer.Sign(digest);

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = CreateContext(body, result.SignatureHex, result.PublicKeyHex, timestampMs);
        await middleware.InvokeAsync(context);

        Assert.True(handlerCalled);
    }

    [Fact]
    public async Task MissingSignatureHeader_ShouldReturnError()
    {
        var body = "test"u8.ToArray();
        var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs));

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = new DefaultHttpContext();
        context.Request.Body = new MemoryStream(body);
        context.Request.Headers[Headers.PublicKey] = _signer.GetPublicKeyHexPrefixed();
        context.Request.Headers[Headers.SignatureTimestamp] = timestampMs.ToString();
        // Missing X-Signature header

        await middleware.InvokeAsync(context);

        Assert.False(handlerCalled);
        Assert.Equal("3", context.Response.Headers["grpc-status"].ToString()); // InvalidArgument = 3
    }

    [Theory]
    [InlineData(-120_000, false)]  // 2 min past → reject
    [InlineData(-60_001, false)]   // just past boundary → reject
    [InlineData(-60_000, true)]    // exact boundary → accept
    [InlineData(0, true)]          // now → accept
    [InlineData(60_000, true)]     // exact boundary → accept
    [InlineData(60_001, false)]    // just past boundary → reject
    [InlineData(120_000, false)]   // 2 min future → reject
    public async Task TimestampBoundary_ShouldRejectOutsideWindow(long offsetMs, bool shouldPass)
    {
        var body = "test"u8.ToArray();
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var requestTimestamp = now + offsetMs;
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(now));

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)requestTimestamp);
        var digest = Keccak256.Hash(body, tsBytes);
        var result = _signer.Sign(digest);

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = CreateContext(body, result.SignatureHex, result.PublicKeyHex, requestTimestamp);
        await middleware.InvokeAsync(context);

        Assert.Equal(shouldPass, handlerCalled);
        if (!shouldPass)
        {
            Assert.Equal("3", context.Response.Headers["grpc-status"].ToString());
        }
    }

    [Fact]
    public async Task WrongPublicKey_ShouldReturnUnauthenticated()
    {
        var body = "test"u8.ToArray();
        var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs));

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);
        var digest = Keccak256.Hash(body, tsBytes);
        var result = _signer.Sign(digest);

        // Use a different key to generate a valid-looking but wrong public key
        var wrongKey = "0x" + new string('a', 128) + "00"; // 65 bytes
        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = CreateContext(body, result.SignatureHex, wrongKey, timestampMs);
        await middleware.InvokeAsync(context);

        Assert.False(handlerCalled);
        Assert.Equal("16", context.Response.Headers["grpc-status"].ToString()); // Unauthenticated = 16
    }

    [Fact]
    public async Task InvalidSignature_ShouldReturnUnauthenticated()
    {
        var body = "test"u8.ToArray();
        var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs));

        // Create valid headers but with a wrong signature (sign different data)
        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);
        var wrongDigest = Keccak256.Hash("wrong data"u8.ToArray(), tsBytes);
        var result = _signer.Sign(wrongDigest);

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = CreateContext(body, result.SignatureHex, result.PublicKeyHex, timestampMs);
        await middleware.InvokeAsync(context);

        Assert.False(handlerCalled);
        Assert.Equal("16", context.Response.Headers["grpc-status"].ToString());
    }

    /// <summary>
    /// Runs a request signed with the network key through the middleware, with
    /// <paramref name="publicKeyHeader"/> as X-Public-Key (none for null).
    /// </summary>
    private async Task<(bool HandlerCalled, DefaultHttpContext Context)> InvokeSignedAsync(string? publicKeyHeader)
    {
        var body = "test body"u8.ToArray();
        var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)timestampMs);
        var result = _signer.Sign(Keccak256.Hash(body, tsBytes));

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(timestampMs)));

        var context = CreateContext(body, result.SignatureHex, publicKeyHeader, timestampMs);
        await middleware.InvokeAsync(context);
        return (handlerCalled, context);
    }

    /// <summary>
    /// The 33-byte compressed encoding of a 65-byte uncompressed key: 0x02 or 0x03 by the parity of y, then x.
    /// </summary>
    private static string CompressedPublicKeyHex(byte[] publicKey) =>
        HexUtils.BytesToHex([(byte)(0x02 | (publicKey[64] & 1)), .. publicKey[1..33]]);

    private static DefaultHttpContext CreateContext(
        byte[] body, string signature, string? publicKey, long timestampMs)
    {
        var context = new DefaultHttpContext();
        context.Request.Body = new MemoryStream(body);
        context.Request.Headers[Headers.Signature] = signature;
        context.Request.Headers[Headers.PublicKey] = publicKey;
        context.Request.Headers[Headers.SignatureTimestamp] = timestampMs.ToString();
        return context;
    }

    private sealed class FakeTimeProvider(DateTimeOffset fixedTime) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => fixedTime;
    }
}
