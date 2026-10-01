using Grpc.Health.V1;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;

namespace T0.ProviderSdk.Tests.Network;

/// <summary>
/// A path in the base URL prefixes every call; the signature does not cover the URL. The server records
/// the path each request arrives at, strips the prefix as a gateway would, and verifies the call.
/// </summary>
public class BaseUrlPathTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    [Theory]
    [InlineData("/prefix")]
    [InlineData("/prefix/")]
    [InlineData("/sda/payments/t0")]
    public async Task PathInBaseUrl_PrefixesEveryCall(string path)
    {
        var prefix = path.TrimEnd('/');
        var paths = new List<string>();
        var port = TestPorts.FindFreePort();
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
            options.ListenLocalhost(port, listenOptions => listenOptions.Protocols = HttpProtocols.Http2));
        builder.Services.AddGrpc();
        builder.Services.AddSingleton(new HealthServiceImpl([Health.Descriptor.FullName]));
        await using var app = builder.Build();
        app.Use(async (context, next) =>
        {
            lock (paths)
                paths.Add(context.Request.Path.Value!);
            await next(context);
        });
        app.UsePathBase(prefix);
        app.UseRouting();
        app.UseMiddleware<SignatureVerificationMiddleware>(new ProviderServerOptions { NetworkPublicKeyHex = PublicKey });
        app.MapGrpcService<HealthServiceImpl>();
        await app.StartAsync();
        await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));

        var client = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}{path}" },
            Signer.FromHex(PrivateKey),
            invoker => new Health.HealthClient(invoker));
        // A non-empty request, so that the signature covers real bytes.
        var response = await client.CheckAsync(new HealthCheckRequest { Service = Health.Descriptor.FullName });

        // SERVING only after the middleware has verified the signature.
        Assert.Equal(HealthCheckResponse.Types.ServingStatus.Serving, response.Status);
        lock (paths)
            Assert.Equal([$"{prefix}/grpc.health.v1.Health/Check"], paths);
    }
}
