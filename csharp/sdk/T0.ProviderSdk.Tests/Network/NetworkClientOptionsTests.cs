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
    [InlineData("http://127.0.0.1:8080")]
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
    [InlineData("ftp://api.t-0.network")]
    [InlineData("/relative/path")]
    [InlineData("http://")]
    [InlineData(" ")]
    public void BaseUrlWithoutHttpSchemeOrHost_IsRefused(string url)
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
