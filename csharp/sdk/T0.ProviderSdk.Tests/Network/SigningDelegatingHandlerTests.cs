using System.Buffers.Binary;
using System.Text;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests.Network;

public class SigningDelegatingHandlerTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public async Task SendAsync_ShouldAddSignatureHeaders()
    {
        var signer = Signer.FromHex(TestPrivateKey);
        var fixedTime = new DateTimeOffset(2024, 1, 23, 10, 13, 20, TimeSpan.Zero);
        var timeProvider = new FakeTimeProvider(fixedTime);

        var capturedRequest = new TaskCompletionSource<HttpRequestMessage>();
        var innerHandler = new FakeHandler(capturedRequest);

        var handler = new SigningDelegatingHandler(signer, timeProvider)
        {
            InnerHandler = innerHandler
        };

        var client = new HttpClient(handler);
        var request = new HttpRequestMessage(HttpMethod.Post, "https://example.com/test")
        {
            Content = new StringContent("test request body", Encoding.UTF8, "application/proto")
        };

        await client.SendAsync(request);

        var captured = await capturedRequest.Task;

        // Verify headers are present
        Assert.True(captured.Headers.Contains(Headers.PublicKey));
        Assert.True(captured.Headers.Contains(Headers.Signature));
        Assert.True(captured.Headers.Contains(Headers.SignatureTimestamp));

        // Verify public key matches
        var pubKeyHex = captured.Headers.GetValues(Headers.PublicKey).First();
        Assert.StartsWith("0x", pubKeyHex);
        Assert.Equal(signer.GetPublicKeyHexPrefixed(), pubKeyHex);

        // Verify signature starts with 0x
        var sigHex = captured.Headers.GetValues(Headers.Signature).First();
        Assert.StartsWith("0x", sigHex);

        // Verify timestamp
        var tsStr = captured.Headers.GetValues(Headers.SignatureTimestamp).First();
        var ts = long.Parse(tsStr);
        Assert.Equal(fixedTime.ToUnixTimeMilliseconds(), ts);

        // Verify the signature is valid
        var bodyBytes = Encoding.UTF8.GetBytes("test request body");
        var tsBytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(tsBytes, (ulong)ts);
        var digest = Keccak256.Hash(bodyBytes, tsBytes);
        var sigBytes = Convert.FromHexString(sigHex[2..]);
        Assert.True(SignatureVerifier.Verify(signer.GetPublicKey(), digest, sigBytes));
    }

    [Fact]
    public async Task SendAsync_EmptyBody_ShouldStillSign()
    {
        var signer = Signer.FromHex(TestPrivateKey);
        var capturedRequest = new TaskCompletionSource<HttpRequestMessage>();
        var innerHandler = new FakeHandler(capturedRequest);

        var handler = new SigningDelegatingHandler(signer)
        {
            InnerHandler = innerHandler
        };

        var client = new HttpClient(handler);
        var request = new HttpRequestMessage(HttpMethod.Post, "https://example.com/test");

        await client.SendAsync(request);

        var captured = await capturedRequest.Task;
        Assert.True(captured.Headers.Contains(Headers.Signature));
    }

    private sealed class FakeHandler(TaskCompletionSource<HttpRequestMessage> tcs) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            tcs.SetResult(request);
            return Task.FromResult(new HttpResponseMessage(System.Net.HttpStatusCode.OK));
        }
    }

    private sealed class FakeTimeProvider(DateTimeOffset fixedTime) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => fixedTime;
    }
}
