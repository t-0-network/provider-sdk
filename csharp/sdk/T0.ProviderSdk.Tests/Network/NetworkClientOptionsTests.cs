using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests.Network;

public class NetworkClientOptionsTests
{
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
    [InlineData("http://my_host:8080")]
    [InlineData("HTTPS://example.com/base/")]
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
    [InlineData("not a url")]
    [InlineData("/relative/path")]
    public void BaseUrlWithoutHttpSchemeHostOrValidPort_IsRefused(string url)
    {
        var ex = Assert.Throws<ArgumentException>(() => new NetworkClientOptions { BaseUrl = url });
        Assert.StartsWith("base URL is not valid", ex.Message);
    }

    [Fact]
    public void Transport_PingsEvery30Seconds_WithA10SecondTimeout()
    {
        using var transport = NetworkClient.CreateTransport();

        Assert.Equal(TimeSpan.FromSeconds(30), transport.KeepAlivePingDelay);
        Assert.Equal(TimeSpan.FromSeconds(10), transport.KeepAlivePingTimeout);
        Assert.Equal(HttpKeepAlivePingPolicy.WithActiveRequests, transport.KeepAlivePingPolicy);
    }
}
