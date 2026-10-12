using Grpc.Core;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;

namespace T0.ProviderSdk.Tests.Network;

public class NetworkClientOptionsTests
{
    private const string Key = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string OtherKey = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    [Fact]
    public void BaseUrl_UnsetOrNull_IsNotSet()
    {
        Assert.Null(new NetworkClientOptions().BaseUrl);
        Assert.Equal("base URL is not set",
            Assert.Throws<ArgumentException>(() => new NetworkClientOptions { BaseUrl = null }).Message);
        Assert.Equal("base URL is not set",
            Assert.Throws<ArgumentException>(() => new NetworkClientOptions { BaseUrl = "" }).Message);
        // A missing URL is reported even when the signer is missing too.
        Assert.Equal("base URL is not set", Assert.Throws<ArgumentException>(
            () => NetworkClient.Create<CallInvoker>(new NetworkClientOptions(), null!, invoker => invoker)).Message);
    }

    // The base_url_parsing vectors say which values are valid, not which scheme one without a scheme gets.
    [Fact]
    public void BaseUrlWithoutScheme_IsReadAsHttps()
    {
        Assert.Equal("https://api.t-0.network:443", new NetworkClientOptions { BaseUrl = "api.t-0.network:443" }.BaseUrl);
    }

    // The base URL overloads of the helpers apply the same reading.
    [Fact]
    public void BaseUrlOverloads_ReadAValueWithoutSchemeAsHttps()
    {
        var signer = Signer.FromHex(Key);

        Assert.NotNull(NetworkClient.CreateNetworkServiceClient("api.t-0.network", signer));
        Assert.NotNull(NetworkClient.CreatePaymentIntentNetworkServiceClient("localhost:8080", signer));
        var ex = Assert.Throws<ArgumentException>(() => NetworkClient.CreateNetworkServiceClient("user@h", signer));
        Assert.Equal("base URL is not valid", ex.Message);
    }

    // Internal, not Unavailable (which callers retry), with the signer's error as the cause; nothing is sent.
    [Fact]
    public async Task SignerFailure_IsInternal_WithItsCause()
    {
        var publicKey = Signer.FromHex(Key).GetPublicKey();
        var signers = new (SignFn Signer, string Cause)[]
        {
            (_ => throw new InvalidOperationException("signer is offline"), "signer is offline"),
            (_ => (new byte[63], publicKey), "signature must be 64 or 65 bytes"),
            (_ => (new byte[65], publicKey[..33]), "public key must be 65 bytes, uncompressed"),
        };

        foreach (var (signer, cause) in signers)
        {
            var client = NetworkClient.CreateNetworkServiceClient("http://127.0.0.1:1", signer);
            var ex = await Assert.ThrowsAsync<RpcException>(
                () => client.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);

            Assert.Equal(StatusCode.Internal, ex.StatusCode);
            Assert.Equal($"signing the request failed: {cause}", ex.Status.Detail);
        }
    }

    [Fact]
    public void NullArguments_AreRefused_WithoutAParameterSuffix()
    {
        var signer = Signer.FromHex(Key);
        var options = new NetworkClientOptions { BaseUrl = "https://api.t-0.network" };

        Assert.Equal("options must not be null", Assert.Throws<ArgumentNullException>(
            () => NetworkClient.Create<CallInvoker>(null!, signer, invoker => invoker)).Message);
        Assert.Equal("signer must not be null", Assert.Throws<ArgumentNullException>(
            () => NetworkClient.Create<CallInvoker>(options, null!, invoker => invoker)).Message);
        Assert.Equal("newClient must not be null", Assert.Throws<ArgumentNullException>(
            () => NetworkClient.Create<CallInvoker>(options, signer, null!)).Message);
        Assert.Equal("signer must not be null", Assert.Throws<ArgumentNullException>(
            () => new SigningDelegatingHandler(null!)).Message);
    }

    [Fact]
    public void Transport_SendsNoPings_AndFollowsNoRedirect()
    {
        using var transport = NetworkClient.CreateTransport();

        Assert.Equal(Timeout.InfiniteTimeSpan, transport.KeepAlivePingDelay);
        Assert.False(transport.AllowAutoRedirect);
    }

    // A redirect would send the signed request to another server.
    [Theory]
    [InlineData(302)]
    [InlineData(307)]
    public async Task Redirect_IsNotFollowed(int status)
    {
        var targetHits = 0;
        var (target, targetUrl) = await TestServer.StartHttp2Async(context =>
        {
            Interlocked.Increment(ref targetHits);
            context.Response.ContentType = "application/grpc";
            context.Response.Headers["grpc-status"] = "0";
            return Task.CompletedTask;
        });
        var (redirector, redirectorUrl) = await TestServer.StartHttp2Async(context =>
        {
            context.Response.StatusCode = status;
            context.Response.Headers.Location = targetUrl + context.Request.Path;
            return Task.CompletedTask;
        });

        try
        {
            var client = NetworkClient.CreateNetworkServiceClient(
                new NetworkClientOptions { BaseUrl = redirectorUrl }, Signer.FromHex(Key));

            await Assert.ThrowsAsync<RpcException>(
                () => client.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(0, Volatile.Read(ref targetHits));
        }
        finally
        {
            await redirector.DisposeAsync();
            await target.DisposeAsync();
        }
    }

    // The transport is shared by every client, so a cookie one server sets must not reach any request.
    [Fact]
    public async Task CookieFromTheServer_IsNotSentBack()
    {
        var cookies = new System.Collections.Concurrent.ConcurrentQueue<string>();
        var (server, url) = await TestServer.StartHttp2Async(context =>
        {
            cookies.Enqueue(context.Request.Headers.Cookie.ToString());
            context.Response.Headers.SetCookie = "session=abc; Path=/";
            context.Response.ContentType = "application/grpc";
            context.Response.Headers["grpc-status"] = "0";
            return Task.CompletedTask;
        });

        try
        {
            var client = NetworkClient.CreateNetworkServiceClient(
                new NetworkClientOptions { BaseUrl = url }, Signer.FromHex(Key));
            for (var i = 0; i < 2; i++)
                await Assert.ThrowsAsync<RpcException>(
                    () => client.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);

            Assert.Equal(["", ""], cookies);
            Assert.False(NetworkClient.SharedTransport.UseCookies);
        }
        finally
        {
            await server.DisposeAsync();
        }
    }

    [Fact]
    public void Clients_ShareOneTransport()
    {
        var first = NetworkClient.CreateSigningHandler(Signer.FromHex(Key));
        var second = NetworkClient.CreateSigningHandler(Signer.FromHex(OtherKey));

        Assert.Same(NetworkClient.SharedTransport, first.InnerHandler);
        Assert.Same(NetworkClient.SharedTransport, second.InnerHandler);
        Assert.False(NetworkClient.SharedTransport.AllowAutoRedirect);
    }

    [Fact]
    public async Task DisposingAClient_LeavesTheSharedTransportOpen()
    {
        NetworkClient.CreateHttpClient(Signer.FromHex(Key)).Dispose();

        // Nothing listens there: a live transport fails to connect, a disposed one refuses to send.
        using var other = NetworkClient.CreateHttpClient(Signer.FromHex(Key));
        var ex = await Assert.ThrowsAnyAsync<Exception>(
            () => other.GetAsync($"http://127.0.0.1:{TestPorts.FindFreePort()}/"));
        Assert.IsType<HttpRequestException>(ex);
    }
}
