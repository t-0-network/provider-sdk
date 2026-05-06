using System.Net;
using System.Net.Sockets;
using System.Reflection;
using Grpc.Core;
using Grpc.Net.Client;
using T0.ProviderSdk;
using T0.ProviderSdk.Api.Tzero.V1.System;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.CrossTest;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// Tests for the auto-registered SystemService.
///
/// 1. Direct unit test of <c>SystemServiceImpl.Health()</c> response shape.
/// 2. End-to-end via <c>T0ProviderServer.RunAsync</c>: a signed Health call
///    succeeds and returns both the customer FQN and SystemService's own FQN.
/// 3. Unsigned Health is rejected — proves the signature middleware also
///    covers the auto-registered service.
/// </summary>
public class SystemServiceImplTests
{
    // Same dev keypair used in CrossServerTests.
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private const string PaymentServiceFqn = "tzero.v1.payment.ProviderService";
    private const string SystemServiceFqn = "tzero.v1.system.SystemService";

    private static readonly string ExpectedSdkVersion = ReadSdkVersionFromAssembly();

    private static int FindFreePort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    private static async Task WaitForPortAsync(int port, TimeSpan timeout)
    {
        var deadline = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < deadline)
        {
            try
            {
                using var client = new TcpClient();
                await client.ConnectAsync(IPAddress.Loopback, port);
                return;
            }
            catch (SocketException)
            {
                await Task.Delay(100);
            }
        }
        throw new TimeoutException($"Port {port} not ready after {timeout.TotalSeconds}s");
    }

    private static string ReadSdkVersionFromAssembly()
    {
        var raw = typeof(T0ProviderServer).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion ?? "unknown";
        var plus = raw.IndexOf('+');
        return plus >= 0 ? raw[..plus] : raw;
    }

    /// <summary>
    /// Direct unit test: build a SystemServiceImpl with an FQN list and verify
    /// the Health response shape mirrors what the proto contract requires.
    /// </summary>
    [Fact]
    public async Task Health_ImplResponseShape()
    {
        var services = new List<string> { PaymentServiceFqn, SystemServiceFqn };
        var impl = new SystemServiceImpl(services);

        var before = DateTime.UtcNow;
        var response = await impl.Health(new HealthRequest(), context: null!);
        var after = DateTime.UtcNow;

        Assert.Equal(services, response.Services);
        Assert.Equal(ExpectedSdkVersion, response.SdkVersion);
        Assert.Equal(SdkEcosystem.Csharp, response.SdkEcosystem);
        Assert.NotNull(response.CurrentTime);
        var currentTime = response.CurrentTime.ToDateTime();
        Assert.InRange(currentTime, before.AddSeconds(-1), after.AddSeconds(1));
    }

    /// <summary>
    /// End-to-end: T0ProviderServer auto-registers SystemService alongside the
    /// customer's PaymentService. A signed Health call returns both FQNs.
    /// </summary>
    [Fact]
    public async Task T0ProviderServer_AutoRegistersSystemService_SignedHealthSucceeds()
    {
        var port = FindFreePort();
        var signer = Signer.FromHex(PrivateKey);
        var dummyNetworkClient = NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer);

        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = PublicKey,
            Port = port,
            TZeroEndpoint = "http://localhost:1",
        };

        // Use args to enable HTTP/2 cleartext, mirroring the starter's appsettings.json
        // which sets Kestrel:EndpointDefaults:Protocols=Http2.
        var server = new T0ProviderServer(config, signer,
            new[] { "--Kestrel:EndpointDefaults:Protocols=Http2" });
        server.MapPaymentService<TestPaymentHandler>(dummyNetworkClient);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            using var channel = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" },
                signer);
            var client = new SystemService.SystemServiceClient(channel);

            var response = await client.HealthAsync(new HealthRequest());

            Assert.Contains(PaymentServiceFqn, response.Services);
            Assert.Contains(SystemServiceFqn, response.Services);
            Assert.Equal(ExpectedSdkVersion, response.SdkVersion);
            Assert.Equal(SdkEcosystem.Csharp, response.SdkEcosystem);
            Assert.NotNull(response.CurrentTime);
            var skew = (DateTime.UtcNow - response.CurrentTime.ToDateTime()).Duration();
            Assert.True(skew < TimeSpan.FromSeconds(5), $"clock skew too large: {skew}");
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }

    /// <summary>
    /// Auto-registered SystemService inherits the SignatureVerificationMiddleware:
    /// an unsigned request is rejected with InvalidArgument (missing public-key header).
    /// </summary>
    [Fact]
    public async Task T0ProviderServer_SystemService_RejectsUnsignedRequest()
    {
        var port = FindFreePort();
        var signer = Signer.FromHex(PrivateKey);
        var dummyNetworkClient = NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer);

        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = PublicKey,
            Port = port,
            TZeroEndpoint = "http://localhost:1",
        };

        var server = new T0ProviderServer(config, signer,
            new[] { "--Kestrel:EndpointDefaults:Protocols=Http2" });
        server.MapPaymentService<TestPaymentHandler>(dummyNetworkClient);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            // Plain channel — no signing handler.
            using var channel = GrpcChannel.ForAddress($"http://127.0.0.1:{port}");
            var client = new SystemService.SystemServiceClient(channel);

            var ex = await Assert.ThrowsAsync<RpcException>(() =>
                client.HealthAsync(new HealthRequest()).ResponseAsync);
            Assert.Equal(StatusCode.InvalidArgument, ex.StatusCode);
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }
}
