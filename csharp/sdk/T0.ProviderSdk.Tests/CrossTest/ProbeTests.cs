using System.Diagnostics;
using System.Net.Sockets;
using System.Text.Json;
using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Tests.Network;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// The C# column of the shared server behavior: every case of server_cases in
/// cross_test/test_vectors.json, sent by <c>go_helper probe</c> over gRPC (the protocol the C#
/// server serves) to a <see cref="T0ProviderServer"/> with its defaults and a payment service mapped.
/// </summary>
public class ProbeTests
{
    // From the test output directory (bin/Debug/net10.0/) to the repository root.
    private static readonly string CrossTest = Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "..", "cross_test"));
    private static readonly string GoHelper = Path.Combine(CrossTest, "go_helper", "go_helper");
    private static readonly string VectorsPath = Path.Combine(CrossTest, "test_vectors.json");

    [Fact]
    public async Task SharedServerCases()
    {
        if (!File.Exists(GoHelper))
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail($"Go helper binary required in CI but not found at {GoHelper}");
            return;
        }
        using var vectors = JsonDocument.Parse(await File.ReadAllTextAsync(VectorsPath));
        var networkPublicKey = "0x" + vectors.RootElement.GetProperty("keys").GetProperty("public_key").GetString();

        var port = TestPorts.FindFreePort();
        var signer = Signer.FromHex("0x" + new string('2', 64));
        var config = new T0Config { ProviderPrivateKey = "", NetworkPublicKey = networkPublicKey, Port = port };
        using var stop = new CancellationTokenSource();
        var server = new T0ProviderServer(config, signer).MapPaymentService<InvalidResponsesHandler>(
            NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer));
        var run = server.RunAsync(stop.Token);
        try
        {
            await WaitForPort(port);
            using var probe = Process.Start(new ProcessStartInfo
            {
                FileName = GoHelper,
                ArgumentList =
                {
                    "probe", $"http://127.0.0.1:{port}", "--sdk", "csharp", "--protocol", "grpc",
                    "--vectors", VectorsPath,
                },
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            })!;
            var output = await probe.StandardOutput.ReadToEndAsync();
            var errors = await probe.StandardError.ReadToEndAsync();
            await probe.WaitForExitAsync();
            Assert.True(probe.ExitCode == 0, output + errors);
        }
        finally
        {
            await stop.CancelAsync();
            await run;
        }
    }

    // The two responses server_cases expect to fail validation: ApprovePaymentQuotes returns an empty
    // response (TestPaymentHandler's), whose result oneof is required; PayOut returns failed.details
    // of 1025 characters, one over the limit.
    public sealed class InvalidResponsesHandler : TestPaymentHandler
    {
        public override Task<PayoutResponse> PayOut(PayoutRequest request, ServerCallContext context) =>
            Task.FromResult(new PayoutResponse
            {
                Failed = new PayoutResponse.Types.Failed { Details = new string('x', 1025) },
            });
    }

    private static async Task WaitForPort(int port)
    {
        var deadline = DateTime.UtcNow.AddSeconds(15);
        while (true)
        {
            try
            {
                using var client = new TcpClient();
                await client.ConnectAsync("127.0.0.1", port);
                return;
            }
            catch (SocketException) when (DateTime.UtcNow < deadline)
            {
                await Task.Delay(100);
            }
        }
    }
}
