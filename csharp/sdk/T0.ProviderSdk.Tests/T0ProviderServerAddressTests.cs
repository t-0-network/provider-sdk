using System.Net;
using System.Net.Sockets;
using Grpc.Health.V1;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests;

/// <summary>
/// Where <see cref="T0ProviderServer"/> listens. Without Kestrel configuration it listens on
/// config.Port on every interface, IPv4 and IPv6, with HTTP/2 without TLS. An application's own
/// <c>Kestrel:Endpoints</c> replace that address; the server does not listen on both.
/// </summary>
public class T0ProviderServerAddressTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public Task WithoutConfiguration_AnswersOnTheIPv4Loopback() => AnswersWithoutConfiguration("127.0.0.1");

    [IPv6LoopbackFact]
    public Task WithoutConfiguration_AnswersOnTheIPv6Loopback() => AnswersWithoutConfiguration("[::1]");

    // The configured endpoint on another port than config.Port, and on the same one.
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task KestrelEndpointsConfiguration_ReplacesTheSdkAddress(bool samePort)
    {
        var sdkPort = TestPorts.FindFreePort();
        var configuredPort = sdkPort;
        if (!samePort)
            do configuredPort = TestPorts.FindFreePort(); while (configuredPort == sdkPort);
        // No Protocols for the endpoint: HTTP/2 comes from the SDK's endpoint defaults.
        var server = NewServer(sdkPort, $"--Kestrel:Endpoints:Http:Url=http://127.0.0.1:{configuredPort}");

        await TestServer.RunAsync(server, configuredPort, async () =>
        {
            if (!samePort)
                Assert.False(await AcceptsAsync(IPAddress.Loopback, sdkPort),
                    $"the SDK's address 127.0.0.1:{sdkPort} is bound next to the configured endpoint");
            Assert.False(await AcceptsAsync(IPAddress.IPv6Loopback, sdkPort),
                $"the SDK's address [::1]:{sdkPort} is bound next to the configured endpoint");
            await AssertServingAsync($"http://127.0.0.1:{configuredPort}");
        });
    }

    private static async Task AnswersWithoutConfiguration(string host)
    {
        var port = TestPorts.FindFreePort();
        await TestServer.RunAsync(NewServer(port), port, () => AssertServingAsync($"http://{host}:{port}"));
    }

    private static T0ProviderServer NewServer(int port, params string[] args)
    {
        var signer = Signer.FromHex(PrivateKey);
        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = signer.GetPublicKeyHexPrefixed(),
            Port = port,
        };
        return new T0ProviderServer(config, signer, args);
    }

    // A signed health check, the probe the Network sends.
    private static async Task AssertServingAsync(string baseUrl)
    {
        var client = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = baseUrl }, Signer.FromHex(PrivateKey),
            invoker => new Health.HealthClient(invoker));
        var response = await client.CheckAsync(new HealthCheckRequest());
        Assert.Equal(HealthCheckResponse.Types.ServingStatus.Serving, response.Status);
    }

    private static async Task<bool> AcceptsAsync(IPAddress address, int port)
    {
        try
        {
            using var client = new TcpClient(address.AddressFamily);
            await client.ConnectAsync(address, port);
            return true;
        }
        catch (SocketException)
        {
            return false;
        }
    }
}

/// <summary>A fact that needs the IPv6 loopback; skipped, with the reason, on a machine without one.</summary>
public sealed class IPv6LoopbackFactAttribute : FactAttribute
{
    public IPv6LoopbackFactAttribute()
    {
        if (!TestPorts.IPv6LoopbackAvailable())
            Skip = "this machine has no IPv6 loopback ([::1]) to listen on";
    }
}
