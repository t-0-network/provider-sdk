using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using ProtoDecimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// Cross-language integration tests between C# and Go.
/// Tests real gRPC communication with signature signing/verification.
///
/// Requires the Go helper binary to be built:
///     cd csharp/sdk/T0.ProviderSdk.Tests/CrossTest/go_helper &amp;&amp; go build -o go_helper .
/// </summary>
public class CrossServerTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    private static readonly string? GoHelperPath = FindGoHelper();

    private static string? FindGoHelper()
    {
        // Path from test output directory (bin/Debug/net10.0/) to go_helper binary
        var testDir = AppContext.BaseDirectory;
        var repoRoot = Path.GetFullPath(Path.Combine(testDir, "..", "..", "..", "..", "..", ".."));
        var path = Path.Combine(repoRoot, "sdk", "T0.ProviderSdk.Tests", "CrossTest", "go_helper", "go_helper");
        return File.Exists(path) ? path : null;
    }

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
    /// C# client signs a request → Go server verifies and handles it.
    /// </summary>
    [Fact]
    public async Task CSharpClient_GoServer_PayOut()
    {
        if (GoHelperPath is null)
        {
            // Skip test if go_helper not built
            return;
        }

        var port = FindFreePort();
        var proc = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = GoHelperPath,
                ArgumentList = { "serve", port.ToString(), PublicKey },
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
            }
        };

        try
        {
            proc.Start();
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            // Create C# client with auto-signing transport
            var signer = Signer.FromHex(PrivateKey);
            using var channel = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" },
                signer);
            var client = new ProviderService.ProviderServiceClient(channel);

            // Make a signed PayOut call — Go server verifies our signature
            var response = await client.PayOutAsync(new PayoutRequest
            {
                PaymentId = 42,
                Currency = "EUR",
                Amount = new ProtoDecimal { Unscaled = 100, Exponent = 0 },
            });

            Assert.NotNull(response);
        }
        finally
        {
            if (!proc.HasExited)
            {
                proc.Kill();
                await proc.WaitForExitAsync();
            }
            proc.Dispose();
        }
    }

    /// <summary>
    /// Go client signs a request → C# server verifies and handles it.
    /// </summary>
    [Fact]
    public async Task GoClient_CSharpServer_PayOut()
    {
        if (GoHelperPath is null)
        {
            // Skip test if go_helper not built
            return;
        }

        var port = FindFreePort();
        var handler = new TestPaymentHandler();

        // Build ASP.NET Core server with gRPC + signature verification
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.ListenLocalhost(port, listenOptions =>
            {
                listenOptions.Protocols = HttpProtocols.Http2;
            });
        });
        builder.Services.AddGrpc();
        builder.Services.AddSingleton(handler);

        var app = builder.Build();
        app.UseMiddleware<SignatureVerificationMiddleware>(
            new ProviderServerOptions { NetworkPublicKeyHex = PublicKey });
        app.MapGrpcService<TestPaymentHandler>();

        try
        {
            await app.StartAsync();
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            // Run Go client that signs and sends a PayOut request
            var proc = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = GoHelperPath,
                    ArgumentList =
                    {
                        "call-pay-out-grpc",
                        $"http://127.0.0.1:{port}",
                        PrivateKey,
                        PublicKey,
                    },
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                }
            };

            proc.Start();
            var stdout = await proc.StandardOutput.ReadToEndAsync();
            var stderr = await proc.StandardError.ReadToEndAsync();
            await proc.WaitForExitAsync();

            Assert.Equal(0, proc.ExitCode);
            Assert.Contains("OK", stdout);

            // Verify the C# server actually received the call
            Assert.Single(handler.PayOutCalls);
            Assert.Equal(42UL, handler.PayOutCalls[0].PaymentId);
            Assert.Equal("EUR", handler.PayOutCalls[0].Currency);

            proc.Dispose();
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }
}
