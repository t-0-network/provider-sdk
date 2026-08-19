using System.Net;
using System.Net.Sockets;
using Grpc.Core;
using Grpc.Health.V1;
using Grpc.Net.Client;
using T0.ProviderSdk;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.CrossTest;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// The no-code-change guarantee: a customer who maps only their own services
/// through <c>T0ProviderServer</c> gets health on the port, behind the same
/// signature middleware. Mirrors <c>go/provider/health_test.go</c>.
/// </summary>
public class HealthServiceImplTests
{
    // Same dev keypair used in CrossServerTests.
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private const string PaymentServiceFqn = "tzero.v1.payment.ProviderService";
    private const string HealthServiceFqn = "grpc.health.v1.Health";

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

    /// <summary>
    /// Starts a server with the customer's PaymentService mapped and nothing else
    /// named. Uses args to enable HTTP/2 cleartext, mirroring the starter's
    /// appsettings.json which sets Kestrel:EndpointDefaults:Protocols=Http2.
    /// </summary>
    private static (T0ProviderServer Server, int Port) NewServer()
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
        return (server, port);
    }

    private static Health.HealthClient NewSignedClient(int port) =>
        new(NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" },
            Signer.FromHex(PrivateKey)));

    [Fact]
    public async Task SignedCheck_AnswersForRegisteredServicesAndRefusesTheRest()
    {
        var (server, port) = NewServer();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            var client = NewSignedClient(port);

            // The customer's own service, health itself, and the whole-process query.
            foreach (var service in new[] { PaymentServiceFqn, HealthServiceFqn, "" })
            {
                var response = await client.CheckAsync(new HealthCheckRequest { Service = service });
                Assert.Equal(HealthCheckResponse.Types.ServingStatus.Serving, response.Status);
            }

            var ex = await Assert.ThrowsAsync<RpcException>(() =>
                client.CheckAsync(new HealthCheckRequest { Service = "example.v1.NotRegistered" }).ResponseAsync);
            Assert.Equal(StatusCode.NotFound, ex.StatusCode);
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }

    /// <summary>
    /// Response headers are the only place the SDK reports what it is: the health
    /// contract has a single status field and names its service in the request, so
    /// the message itself has no room for this.
    /// </summary>
    [Fact]
    public async Task CheckResponse_CarriesSdkIdentityHeaders()
    {
        var (server, port) = NewServer();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            using var call = NewSignedClient(port).CheckAsync(new HealthCheckRequest());
            await call.ResponseAsync;
            var headers = await call.ResponseHeadersAsync;

            Assert.Equal("csharp", headers.GetValue(HealthServiceImpl.SdkEcosystemHeader));
            Assert.Equal(HealthServiceImpl.LoadSdkVersion(), headers.GetValue(HealthServiceImpl.SdkVersionHeader));
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }

    /// <summary>
    /// The probe is signed like every other call the Network makes. Without this
    /// the transport would be publishing an unauthenticated endpoint on a
    /// partner's port.
    /// </summary>
    [Fact]
    public async Task UnsignedCheck_IsRejected()
    {
        var (server, port) = NewServer();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            // Plain channel — no signing handler.
            using var channel = GrpcChannel.ForAddress($"http://127.0.0.1:{port}");
            var client = new Health.HealthClient(channel);

            var ex = await Assert.ThrowsAsync<RpcException>(() =>
                client.CheckAsync(new HealthCheckRequest()).ResponseAsync);
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
