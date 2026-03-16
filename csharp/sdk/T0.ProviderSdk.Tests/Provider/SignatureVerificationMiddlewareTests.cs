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

    [Fact]
    public async Task TimestampOutOfRange_ShouldReturnError()
    {
        var body = "test"u8.ToArray();
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var oldTimestamp = now - 120_000; // 2 minutes old
        var timeProvider = new FakeTimeProvider(DateTimeOffset.FromUnixTimeMilliseconds(now));

        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)oldTimestamp);
        var digest = Keccak256.Hash(body, tsBytes);
        var result = _signer.Sign(digest);

        var handlerCalled = false;
        var middleware = new SignatureVerificationMiddleware(
            _ => { handlerCalled = true; return Task.CompletedTask; },
            CreateOptions(),
            timeProvider);

        var context = CreateContext(body, result.SignatureHex, result.PublicKeyHex, oldTimestamp);
        await middleware.InvokeAsync(context);

        Assert.False(handlerCalled);
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

    private static DefaultHttpContext CreateContext(
        byte[] body, string signature, string publicKey, long timestampMs)
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
