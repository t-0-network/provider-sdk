using System.Collections.Concurrent;
using Grpc.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.CrossTest;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// An invalid response is logged once, with the fields the other SDKs log and the version the
/// health headers report.
/// </summary>
public class ValidationLogTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public async Task InvalidResponse_IsLoggedOnce_WithTheOverriddenSdkVersion()
    {
        var port = TestPorts.FindFreePort();
        var signer = Signer.FromHex(PrivateKey);
        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = signer.GetPublicKeyHexPrefixed(),
            Port = port,
        };
        var logs = new CapturingLoggerProvider();
        var server = new T0ProviderServer(config, signer)
            .MapPaymentService<TestPaymentHandler>(NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer))
            .WithSdkVersion("9.9.9-test");
        server.Services.AddSingleton<ILoggerProvider>(logs);
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            var client = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" }, signer,
                invoker => new ProviderService.ProviderServiceClient(invoker));

            // TestPaymentHandler answers with an empty PayoutResponse, whose result oneof is required.
            var ex = await Assert.ThrowsAsync<RpcException>(() => client.PayOutAsync(new PayoutRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Internal, ex.StatusCode);

            var entry = Assert.Single(logs.Entries, e => e.Category == typeof(ValidationInterceptor).FullName);
            Assert.Equal(LogLevel.Error, entry.Level);
            Assert.Equal("tzero.v1.payment.ProviderService/PayOut", entry.Fields["rpc_method"]);
            Assert.Equal(PayoutResponse.Descriptor.FullName, entry.Fields["response_type"]);
            Assert.Equal(ex.Status.Detail, $"response validation failed: {entry.Fields["violations"]}");
            Assert.Equal("9.9.9-test", entry.Fields["sdk_version"]);
            Assert.StartsWith("response validation failed ", entry.Message);
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }

    /// <summary>
    /// A response whose rule ProtoValidate cannot evaluate is Internal "response validation error:
    /// &lt;cause&gt;" (V12), logged once like an invalid one.
    /// </summary>
    [Fact]
    public async Task UnevaluableRule_IsInternalResponseValidationError_AndLoggedOnce()
    {
        var logs = new CapturingLoggerProvider();
        var interceptor = new ValidationInterceptor(
            logs.CreateLogger(typeof(ValidationInterceptor).FullName!), "9.9.9-test");
        var cause = UnevaluableRule.Cause();

        var ex = await Assert.ThrowsAsync<RpcException>(() => interceptor.UnaryServerHandler(
            new PayoutRequest(),
            new CallContext("/tzero.v1.payment.ProviderService/PayOut"),
            (PayoutRequest _, ServerCallContext _) => Task.FromResult(new UnevaluableRule())));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal($"response validation error: {cause}", ex.Status.Detail);
        var entry = Assert.Single(logs.Entries);
        Assert.Equal(LogLevel.Error, entry.Level);
        Assert.Equal("tzero.v1.payment.ProviderService/PayOut", entry.Fields["rpc_method"]);
        Assert.Equal(UnevaluableRule.TypeName, entry.Fields["response_type"]);
        Assert.Equal(cause, entry.Fields["error"]);
        Assert.Equal("9.9.9-test", entry.Fields["sdk_version"]);
        Assert.StartsWith("response validation error ", entry.Message);
    }

    private sealed record Entry(string Category, LogLevel Level, string Message, Dictionary<string, object?> Fields);

    private sealed class CapturingLoggerProvider : ILoggerProvider
    {
        public ConcurrentQueue<Entry> Entries { get; } = new();

        public ILogger CreateLogger(string categoryName) => new Logger(categoryName, Entries);

        public void Dispose() { }

        private sealed class Logger(string category, ConcurrentQueue<Entry> entries) : ILogger
        {
            public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

            public bool IsEnabled(LogLevel logLevel) => true;

            public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception,
                Func<TState, Exception?, string> formatter)
            {
                var fields = state is IEnumerable<KeyValuePair<string, object?>> pairs
                    ? pairs.ToDictionary(p => p.Key, p => p.Value)
                    : [];
                entries.Enqueue(new Entry(category, logLevel, formatter(state, exception), fields));
            }
        }
    }

    private sealed class CallContext(string method) : ServerCallContext
    {
        protected override Task WriteResponseHeadersAsyncCore(Metadata responseHeaders) => Task.CompletedTask;
        protected override ContextPropagationToken CreatePropagationTokenCore(ContextPropagationOptions? options) => null!;
        protected override string MethodCore => method;
        protected override string HostCore => "localhost";
        protected override string PeerCore => "test-peer";
        protected override DateTime DeadlineCore => DateTime.MaxValue;
        protected override Metadata RequestHeadersCore => new();
        protected override CancellationToken CancellationTokenCore => CancellationToken.None;
        protected override Metadata ResponseTrailersCore => new();
        protected override Status StatusCore { get; set; }
        protected override WriteOptions? WriteOptionsCore { get; set; }
        protected override AuthContext AuthContextCore => null!;
    }
}
