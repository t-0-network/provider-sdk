namespace T0.ProviderSdk.Tests;

/// <summary>
/// Tests T0Config contract: required fields, defaults, and FromEnvironment behavior.
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

    [Fact]
    public void FromEnvironment_MissingPrivateKey_Throws()
    {
        ClearEnvVars();

        var ex = Assert.Throws<InvalidOperationException>(T0Config.FromEnvironment);
        Assert.Contains("PROVIDER_PRIVATE_KEY", ex.Message);
    }

    [Fact]
    public void FromEnvironment_MissingNetworkPublicKey_Throws()
    {
        ClearEnvVars();
        Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", "testkey");

        try
        {
            var ex = Assert.Throws<InvalidOperationException>(T0Config.FromEnvironment);
            Assert.Contains("NETWORK_PUBLIC_KEY", ex.Message);
        }
        finally
        {
            ClearEnvVars();
        }
    }

    [Fact]
    public void FromEnvironment_AllSet_LoadsCorrectly()
    {
        ClearEnvVars();
        Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", "privkey");
        Environment.SetEnvironmentVariable("NETWORK_PUBLIC_KEY", "0x04pubkey");
        Environment.SetEnvironmentVariable("TZERO_ENDPOINT", "https://test.endpoint");
        Environment.SetEnvironmentVariable("PORT", "3000");

        try
        {
            var config = T0Config.FromEnvironment();

            Assert.Equal("privkey", config.ProviderPrivateKey);
            Assert.Equal("0x04pubkey", config.NetworkPublicKey);
            Assert.Equal("https://test.endpoint", config.TZeroEndpoint);
            Assert.Equal(3000, config.Port);
        }
        finally
        {
            ClearEnvVars();
        }
    }

    [Fact]
    public void FromEnvironment_OptionalMissing_UsesDefaults()
    {
        ClearEnvVars();
        Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", "privkey");
        Environment.SetEnvironmentVariable("NETWORK_PUBLIC_KEY", "0x04pubkey");

        try
        {
            var config = T0Config.FromEnvironment();

            Assert.Equal("https://api-sandbox.t-0.network", config.TZeroEndpoint);
            Assert.Equal(8080, config.Port);
        }
        finally
        {
            ClearEnvVars();
        }
    }

    [Fact]
    public void FromEnvironment_InvalidPort_UsesDefault()
    {
        ClearEnvVars();
        Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", "privkey");
        Environment.SetEnvironmentVariable("NETWORK_PUBLIC_KEY", "0x04pubkey");
        Environment.SetEnvironmentVariable("PORT", "not_a_number");

        try
        {
            var config = T0Config.FromEnvironment();
            Assert.Equal(8080, config.Port);
        }
        finally
        {
            ClearEnvVars();
        }
    }

    private static void ClearEnvVars()
    {
        Environment.SetEnvironmentVariable("PROVIDER_PRIVATE_KEY", null);
        Environment.SetEnvironmentVariable("NETWORK_PUBLIC_KEY", null);
        Environment.SetEnvironmentVariable("TZERO_ENDPOINT", null);
        Environment.SetEnvironmentVariable("PORT", null);
    }
}
