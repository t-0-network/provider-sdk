using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Sockets;
using System.Security.Cryptography;
using Google.Protobuf;
using Grpc.Core;
using Grpc.Net.Client;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Common;
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

    // A key the Go helper does not trust.
    private const string OtherPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    private static readonly string? GoHelperPath = FindGoHelper();

    private static string? FindGoHelper()
    {
        // Path from test output directory (bin/Debug/net10.0/) to go_helper binary
        var testDir = AppContext.BaseDirectory;
        var repoRoot = Path.GetFullPath(Path.Combine(testDir, "..", "..", "..", "..", "..", ".."));
        var path = Path.Combine(repoRoot, "cross_test", "go_helper", "go_helper");
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

        var port = FindFreePort();
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
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));

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

            var signer = Signer.FromHex(PrivateKey);
            using var channel = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" },
                signer);
            var healthClient = new Grpc.Health.V1.Health.HealthClient(channel);

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
    public async Task CSharpClient_GoServer_ClientStream()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey));
        var invoker = channel.CreateCallInvoker();

        using var call = invoker.AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        foreach (var value in new[] { "m1", "m2", "m3" })
            await call.RequestStream.WriteAsync(new StringValue { Value = value });
        await call.RequestStream.CompleteAsync();

        var response = await call.ResponseAsync;
        Assert.Equal("m1,m2,m3", response.Value);
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream verified over the first envelope")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

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
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey));
        var invoker = channel.CreateCallInvoker();

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
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey));
        var invoker = channel.CreateCallInvoker();

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
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey));
        var invoker = channel.CreateCallInvoker();

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
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(PrivateKey));
        var invoker = channel.CreateCallInvoker();
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
        // NetworkClient.Create's pipeline plus a header recorder below the signer; the default
        // GrpcChannelOptions include the gzip provider.
        var recorder = new HeaderRecorder { InnerHandler = new HttpClientHandler() };
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
    public async Task CSharpClient_GoServer_ClientStream_SignedOverTheWholeBody_IsRejected()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        using var channel = BufferingChannel(server.BaseUrl, Signer.FromHex(PrivateKey));

        var ex = await CompleteClientStreamAsync(channel);

        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync(
                "/test.v1.StreamTest/ClientStream rejected: signature does not verify over the first message")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_Unsigned_IsRejected()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        using var channel = BufferingChannel(server.BaseUrl, signer: null);

        var ex = await CompleteClientStreamAsync(channel);

        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream rejected: unknown public key")
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
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl },
            Signer.FromHex(PrivateKey),
            timeProvider: new FixedTimeProvider(DateTimeOffset.UtcNow.AddMinutes(-2)));

        var ex = await FirstMessageOnlyClientStreamAsync(channel);

        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync(
                "/test.v1.StreamTest/ClientStream rejected: timestamp is outside the allowed time window")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public async Task CSharpClient_GoServer_ClientStream_OtherKey_IsRejected()
    {
        if (GoHelperPath is null)
        {
            if (Environment.GetEnvironmentVariable("CI") != null)
                Assert.Fail("Go helper binary required in CI but not found");
            return;
        }

        await using var server = await GoStreamServer.StartAsync(GoHelperPath);
        using var channel = NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = server.BaseUrl }, Signer.FromHex(OtherPrivateKey));

        var ex = await FirstMessageOnlyClientStreamAsync(channel);

        Assert.Equal(StatusCode.Unauthenticated, ex.StatusCode);
        await server.WaitForLogAsync("/test.v1.StreamTest/ClientStream rejected: unknown public key")
            .WaitAsync(TimeSpan.FromSeconds(10));
    }

    private static async Task<RpcException> CompleteClientStreamAsync(GrpcChannel channel)
    {
        using var call = channel.CreateCallInvoker().AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        foreach (var value in new[] { "m1", "m2", "m3" })
            await call.RequestStream.WriteAsync(new StringValue { Value = value });
        await call.RequestStream.CompleteAsync();
        return await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync);
    }

    // Only m1: later writes could race the rejection.
    private static async Task<RpcException> FirstMessageOnlyClientStreamAsync(GrpcChannel channel)
    {
        using var call = channel.CreateCallInvoker().AsyncClientStreamingCall(ClientStreamMethod, null, StreamCallOptions());
        await call.RequestStream.WriteAsync(new StringValue { Value = "m1" });
        return await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync);
    }

    private static GrpcChannel BufferingChannel(string baseUrl, Signer? signer) =>
        GrpcChannel.ForAddress(baseUrl, new GrpcChannelOptions
        {
            HttpClient = new HttpClient(new WholeBodySigningHandler(signer) { InnerHandler = new HttpClientHandler() }),
            DisposeHttpClient = true,
        });

    /// <summary>
    /// Buffers the whole request body and signs it, or only buffers without a signer.
    /// </summary>
    private sealed class WholeBodySigningHandler(Signer? signer) : DelegatingHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var source = request.Content!;
            var body = await source.ReadAsByteArrayAsync(cancellationToken);

            if (signer is not null)
            {
                var timestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                var result = signer.Sign(Keccak256.Hash(body, Headers.EncodeTimestamp(timestampMs)));
                request.Headers.TryAddWithoutValidation(Headers.PublicKey, result.PublicKeyHex);
                request.Headers.TryAddWithoutValidation(Headers.Signature, result.SignatureHex);
                request.Headers.TryAddWithoutValidation(Headers.SignatureTimestamp, timestampMs.ToString());
            }

            request.Content = new ByteArrayContent(body);
            request.Content.Headers.ContentType = source.Headers.ContentType;
            return await base.SendAsync(request, cancellationToken);
        }
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
            var port = FindFreePort();
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
                await WaitForPortAsync(port, TimeSpan.FromSeconds(10));
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
