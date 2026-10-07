namespace T0.ProviderSdk.Tests;

/// <summary>
/// Tests T0Config contract: required fields and defaults.
/// </summary>
public class T0ConfigTests
{
    [Fact]
    public void Constructor_RequiredFields_MustBeSet()
    {
        var config = new T0Config
        {
            ProviderPrivateKey = "abc123",
            NetworkPublicKey = "0x04def456"
        };

        Assert.Equal("abc123", config.ProviderPrivateKey);
        Assert.Equal("0x04def456", config.NetworkPublicKey);
    }

    [Fact]
    public void Constructor_Defaults_AreCorrect()
    {
        var config = new T0Config
        {
            ProviderPrivateKey = "key",
            NetworkPublicKey = "pub"
        };

        Assert.Equal("https://api-sandbox.t-0.network", config.TZeroEndpoint);
        Assert.Equal(8080, config.Port);
    }

    [Fact]
    public void Constructor_CustomValues_Override()
    {
        var config = new T0Config
        {
            ProviderPrivateKey = "key",
            NetworkPublicKey = "pub",
            TZeroEndpoint = "https://custom.endpoint",
            Port = 9090
        };

        Assert.Equal("https://custom.endpoint", config.TZeroEndpoint);
        Assert.Equal(9090, config.Port);
    }
}
