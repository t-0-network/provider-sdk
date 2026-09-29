using System.Diagnostics;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using Google.Protobuf;
using Grpc.Core;
using Grpc.Net.Client;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.Network;
using StringValue = Google.Protobuf.WellKnownTypes.StringValue;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// Cross-language integration tests between C# and Go.
/// Tests real gRPC communication with signature signing/verification.
///
/// Requires the Go helper binary to be built:
///     cd cross_test/go_helper &amp;&amp; go build -o go_helper .
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
        var path = Path.Combine(repoRoot, "cross_test", "go_helper", "go_helper");
        return File.Exists(path) ? path : null;
    }

    /// <summary>
    /// Go client signs a request → C# server verifies and handles it.
    /// </summary>
    [Fact]
    public async Task GoClient_CSharpServer_PayOut()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        var port = TestPorts.FindFreePort();
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
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            // Run Go client that signs and sends a PayOut request
            var proc = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = GoHelperPath,
                    ArgumentList =
                    {
                        "call-pay-out",
                        $"http://127.0.0.1:{port}",
                        PrivateKey,
                        "--grpc",
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

    /// <summary>
    /// Go client calls health check on C# server via gRPC.
    /// </summary>
    [Fact]
    public async Task GoClient_CSharpServer_HealthCheck()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        var port = TestPorts.FindFreePort();
        var handler = new TestPaymentHandler();

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

        var fqns = new List<string>
        {
            "tzero.v1.payment.ProviderService",
            Grpc.Health.V1.Health.Descriptor.FullName,
        };
        builder.Services.AddSingleton(new HealthServiceImpl(fqns));

        var app = builder.Build();
        app.UseMiddleware<SignatureVerificationMiddleware>(
            new ProviderServerOptions { NetworkPublicKeyHex = PublicKey });
        app.MapGrpcService<TestPaymentHandler>();
        app.MapGrpcService<HealthServiceImpl>();

        try
        {
            await app.StartAsync();
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            var proc = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = GoHelperPath,
                    ArgumentList =
                    {
                        "call-health",
                        $"http://127.0.0.1:{port}",
                        PrivateKey,
                        "--grpc",
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
            Assert.Contains("status=SERVING", stdout, StringComparison.OrdinalIgnoreCase);

            proc.Dispose();
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }

    /// <summary>
    /// C# client calls health check on Go server — proves C# signing is accepted by Go.
    /// </summary>
    [Fact]
    public async Task CSharpClient_GoServer_HealthCheck()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        var port = TestPorts.FindFreePort();
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
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));

            var signer = Signer.FromHex(PrivateKey);
            var healthClient = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" },
                signer,
                invoker => new Grpc.Health.V1.Health.HealthClient(invoker));

            var response = await healthClient.CheckAsync(
                new Grpc.Health.V1.HealthCheckRequest { Service = Grpc.Health.V1.Health.Descriptor.FullName });

            Assert.Equal(Grpc.Health.V1.HealthCheckResponse.Types.ServingStatus.Serving, response.Status);
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

    // test.v1.StreamTest (cross_test/stream_test.proto), built by hand on StringValue.
    private static readonly Marshaller<StringValue> StringValueMarshaller =
        Marshallers.Create(value => value.ToByteArray(), StringValue.Parser.ParseFrom);

    private static readonly Method<StringValue, StringValue> ClientStreamMethod = new(
        MethodType.ClientStreaming, "test.v1.StreamTest", "ClientStream", StringValueMarshaller, StringValueMarshaller);

    private static readonly Method<StringValue, StringValue> ServerStreamMethod = new(
        MethodType.ServerStreaming, "test.v1.StreamTest", "ServerStream", StringValueMarshaller, StringValueMarshaller);

    private static CallOptions StreamCallOptions() => new(deadline: DateTime.UtcNow.AddSeconds(30));

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_VerifiedBeforeLaterMessagesAreWritten()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        var invoker = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey), i => i);

        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        await call.RequestStream.WriteAsync(new StringValue { Value = "m1" });

        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream verified over the first envelope")
            .WaitAsync(TimeSpan.FromSeconds(10));

        await call.RequestStream.WriteAsync(new StringValue { Value = "m2" });
        await call.RequestStream.WriteAsync(new StringValue { Value = "m3" });
        await call.RequestStream.CompleteAsync();

        Assert.Equal("m1,m2,m3", (await call.ResponseAsync).Value);
    }

    [Fact]
    public async Task CSharpClient_GoServer_ServerStream()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        var invoker = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey), i => i);

        using var call = invoker.AsyncServerStreamingCall(
            ServerStreamMethod, null, StreamCallOptions(), new StringValue { Value = "hello" });
        var received = new List<string>();
        while (await call.ResponseStream.MoveNext(CancellationToken.None))
            received.Add(call.ResponseStream.Current.Value);

        Assert.Equal(["hello", "hello", "hello"], received);
        await server.WaitForLogAsync("/test.v1.StreamTest/ServerStream verified over the first envelope")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_EmptyClientStream_IsRejected()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        var invoker = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey), i => i);

        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        await call.RequestStream.CompleteAsync();

        var ex = await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync);
        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream rejected: no first message")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_LargeFirstMessage()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        var invoker = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey), i => i);
        var large = Convert.ToBase64String(RandomNumberGenerator.GetBytes(192 * 1024)); // 256 KiB, over the pipe's pause threshold

        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        await call.RequestStream.WriteAsync(new StringValue { Value = large });
        await call.RequestStream.WriteAsync(new StringValue { Value = "tail" });
        await call.RequestStream.CompleteAsync();

        Assert.Equal($"{large},tail", (await call.ResponseAsync).Value);
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream verified over the first envelope")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_GzipFirstMessage()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        // The SDK's signing handler and transport with a header recorder between them; the default
        // GrpcChannelOptions include the gzip provider.
        var recorder = new HeaderRecorder { InnerHandler = NetworkClient.CreateTransport() };
        using var channel = GrpcChannel.ForAddress(server.BaseUrl, new GrpcChannelOptions
        {
            HttpClient = new HttpClient(new SigningDelegatingHandler(Signer.FromHex(PrivateKey)) { InnerHandler = recorder }),
            DisposeHttpClient = true,
        });
        var invoker = channel.CreateCallInvoker();
        var callOptions = StreamCallOptions().WithHeaders(new Metadata { { "grpc-internal-encoding-request", "gzip" } });
        var first = new string('m', 1000);

        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, callOptions);
        await call.RequestStream.WriteAsync(new StringValue { Value = first });
        await call.RequestStream.WriteAsync(new StringValue { Value = "tail" });
        await call.RequestStream.CompleteAsync();

        Assert.Equal($"{first},tail", (await call.ResponseAsync).Value);
        Assert.Equal("gzip", Assert.Single(recorder.Sent!.GetValues("grpc-encoding")));
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream verified over the first envelope")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_StaleTimestamp_IsRejected()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        // The factories take no clock, so the signing handler is built here with one two minutes behind.
        var signer = new SigningDelegatingHandler(
            Signer.FromHex(PrivateKey), new FixedTimeProvider(DateTimeOffset.UtcNow.AddMinutes(-2)))
        {
            InnerHandler = NetworkClient.CreateTransport()
        };
        using var channel = GrpcChannel.ForAddress(server.BaseUrl, new GrpcChannelOptions
        {
            HttpClient = new HttpClient(signer),
            DisposeHttpClient = true,
        });

        var ex = await FirstMessageOnlyClientStreamAsync(channel.CreateCallInvoker());

        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync(
                "/test.v1.StreamTest/ClientStream rejected: timestamp is outside the allowed time window")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    // Only m1: later writes could race the rejection.
    private static async Task<RpcException> FirstMessageOnlyClientStreamAsync(CallInvoker invoker)
    {
        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        await call.RequestStream.WriteAsync(new StringValue { Value = "m1" });
        return await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync);
    }

    private sealed class HeaderRecorder : DelegatingHandler
    {
        public HttpRequestHeaders? Sent { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Sent = request.Headers;
            return base.SendAsync(request, cancellationToken);
        }
    }

    /// <summary>
    /// <c>go_helper serve</c> on a free port. Its output is read asynchronously, so tests wait for
    /// a log line rather than read it.
    /// </summary>
    private sealed class GoStreamServer : IAsyncDisposable
    {
        private readonly Process _process;
        private readonly List<string> _lines = [];
        private readonly List<(string Text, TaskCompletionSource Seen)> _waiters = [];

        private GoStreamServer(Process process, int port)
        {
            _process = process;
            BaseUrl = $"http://127.0.0.1:{port}";
        }

        public string BaseUrl { get; }

        public static async Task<GoStreamServer> StartAsync(string helperPath)
        {
            var port = TestPorts.FindFreePort();
            var process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = helperPath,
                    ArgumentList = { "serve", port.ToString(), PublicKey },
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                }
            };
            var server = new GoStreamServer(process, port);
            process.OutputDataReceived += (_, e) => server.OnLine(e.Data);
            process.ErrorDataReceived += (_, e) => server.OnLine(e.Data);

            try
            {
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
                return server;
            }
            catch
            {
                await server.DisposeAsync();
                throw;
            }
        }

        public Task WaitForLogAsync(string text)
        {
            lock (_lines)
            {
                if (_lines.Any(line => line.Contains(text, StringComparison.Ordinal)))
                    return Task.CompletedTask;
                var seen = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
                _waiters.Add((text, seen));
                return seen.Task;
            }
        }

        private void OnLine(string? line)
        {
            if (line is null)
                return;
            lock (_lines)
            {
                _lines.Add(line);
                foreach (var waiter in _waiters.Where(w => line.Contains(w.Text, StringComparison.Ordinal)).ToList())
                {
                    waiter.Seen.TrySetResult();
                    _waiters.Remove(waiter);
                }
            }
        }

        public async ValueTask DisposeAsync()
        {
            try
            {
                if (!_process.HasExited)
                {
                    _process.Kill();
                    await _process.WaitForExitAsync();
                }
            }
            catch (InvalidOperationException)
            {
                // Never started.
            }
            _process.Dispose();
        }
    }
}
