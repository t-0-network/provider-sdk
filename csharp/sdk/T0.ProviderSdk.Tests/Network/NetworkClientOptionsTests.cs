using Grpc.Core;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;

namespace T0.ProviderSdk.Tests.Network;

public class NetworkClientOptionsTests
{
    private const string Key = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string OtherKey = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    [Fact]
    public void BaseUrl_DefaultsToTheNetwork_AlsoWhenSetToNull()
    {
        Assert.Equal("https://api.t-0.network", new NetworkClientOptions().BaseUrl);
        Assert.Equal("https://api.t-0.network", new NetworkClientOptions { BaseUrl = null }.BaseUrl);
    }

    [Theory]
    [InlineData("https://api.t-0.network")]
    [InlineData("http://localhost:8080")]
    [InlineData("http://127.0.0.1:1234")]
    [InlineData("http://[::1]:8080")]
    [InlineData("https://api.t-0.network/")]
    [InlineData("http://h:8080/")]
    [InlineData("HTTPS://example.com")]
    public void BaseUrl_AcceptsHttpAndHttpsUrls(string url)
    {
        Assert.Equal(url, new NetworkClientOptions { BaseUrl = url }.BaseUrl);
    }

    [Fact]
    public void EmptyBaseUrl_IsRefused()
    {
        var ex = Assert.Throws<ArgumentException>(() => new NetworkClientOptions { BaseUrl = "" });
        Assert.StartsWith("base URL is not set", ex.Message);
    }

    [Theory]
    [InlineData("api.t-0.network")] // no scheme: not repaired
    [InlineData("api.t-0.network:443")]
    [InlineData("ftp://h")]
    [InlineData("http://")]
    [InlineData("http://:8080")]
    [InlineData("http:foo")]
    [InlineData("http://h:99999")]
    [InlineData("http://h:0")]
    [InlineData("http://my_host:8080")]
    [InlineData("http://bücher.example")]
    [InlineData("http://user@h")]
    [InlineData("http://h:")]
    [InlineData("http://[:::]:8080")] // not an IPv6 address
    [InlineData("https://api.t-0.network/v1")]
    [InlineData("https://api.t-0.network/v1/")]
    [InlineData("https://api.t-0.network?x")]
    [InlineData("https://api.t-0.network#x")]
    [InlineData("not a url")]
    [InlineData("/relative/path")]
    public void BaseUrlOtherThanSchemeHostAndPort_IsRefused(string url)
    {
        var ex = Assert.Throws<ArgumentException>(() => new NetworkClientOptions { BaseUrl = url });
        Assert.StartsWith("base URL is not valid", ex.Message);
    }

    [Fact]
    public void Transport_PingsEvery5Minutes_WithA10SecondTimeout()
    {
        using var transport = NetworkClient.CreateTransport();

        Assert.Equal(TimeSpan.FromMinutes(5), transport.KeepAlivePingDelay);
        Assert.Equal(TimeSpan.FromSeconds(10), transport.KeepAlivePingTimeout);
        Assert.Equal(HttpKeepAlivePingPolicy.WithActiveRequests, transport.KeepAlivePingPolicy);
        Assert.False(transport.AllowAutoRedirect);
    }

    // A redirect would send the signed request to another server.
    [Theory]
    [InlineData(302)]
    [InlineData(307)]
    public async Task Redirect_IsNotFollowed(int status)
    {
        var targetHits = 0;
        var (target, targetUrl) = await StartServerAsync(context =>
        {
            Interlocked.Increment(ref targetHits);
            context.Response.ContentType = "application/grpc";
            context.Response.Headers["grpc-status"] = "0";
            return Task.CompletedTask;
        });
        var (redirector, redirectorUrl) = await StartServerAsync(context =>
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

    /// <summary>
    /// HTTP/2 cleartext server on a free port.
    /// </summary>
    private static async Task<(WebApplication App, string BaseUrl)> StartServerAsync(RequestDelegate handler)
    {
        var port = TestPorts.FindFreePort();
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
            options.ListenLocalhost(port, listenOptions => listenOptions.Protocols = HttpProtocols.Http2));
        var app = builder.Build();
        app.Run(handler);

        try
        {
            await app.StartAsync();
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            return (app, $"http://127.0.0.1:{port}");
        }
        catch
        {
            await app.DisposeAsync();
            throw;
        }
    }

    [Fact]
    public void Clients_ShareOneTransport()
    {
        var first = NetworkClient.CreateSigningHandler(Signer.FromHex(Key));
        var second = NetworkClient.CreateSigningHandler(Signer.FromHex(OtherKey));

        Assert.Same(NetworkClient.SharedTransport, first.InnerHandler);
        Assert.Same(NetworkClient.SharedTransport, second.InnerHandler);
        Assert.Equal(TimeSpan.FromMinutes(5), NetworkClient.SharedTransport.KeepAlivePingDelay);
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
