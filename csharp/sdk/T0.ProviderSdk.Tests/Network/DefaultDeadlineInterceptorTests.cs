using System.Net;
using System.Net.Sockets;
using Google.Protobuf;
using Grpc.Core;
using Grpc.Core.Interceptors;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using StringValue = Google.Protobuf.WellKnownTypes.StringValue;

namespace T0.ProviderSdk.Tests.Network;

public class DefaultDeadlineInterceptorTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private static readonly Marshaller<StringValue> Marshaller =
        Marshallers.Create(value => value.ToByteArray(), StringValue.Parser.ParseFrom);

    private static Method<StringValue, StringValue> NewMethod(MethodType type) =>
        new(type, "test.v1.StreamTest", type.ToString(), Marshaller, Marshaller);

    /// <summary>
    /// Makes one call of <paramref name="type"/> through the interceptor and returns the deadline
    /// the call reached the invoker with.
    /// </summary>
    private static DateTime? DeadlineOf(MethodType type, NetworkClientOptions options, CallOptions callOptions = default)
    {
        var inner = new CapturingInvoker();
        var invoker = inner.Intercept(new DefaultDeadlineInterceptor(options));
        var method = NewMethod(type);
        var request = new StringValue { Value = "x" };

        switch (type)
        {
            case MethodType.Unary:
                invoker.AsyncUnaryCall(method, null, callOptions, request);
                break;
            case MethodType.ServerStreaming:
                invoker.AsyncServerStreamingCall(method, null, callOptions, request);
                break;
            case MethodType.ClientStreaming:
                invoker.AsyncClientStreamingCall(method, null, callOptions);
                break;
            case MethodType.DuplexStreaming:
                invoker.AsyncDuplexStreamingCall(method, null, callOptions);
                break;
        }

        Assert.Equal(type, inner.Type);
        return inner.Options.Deadline;
    }

    private static void AssertDeadlineIn(DateTime? deadline, DateTime before, TimeSpan timeout)
    {
        Assert.NotNull(deadline);
        Assert.Equal(DateTimeKind.Utc, deadline.Value.Kind);
        Assert.InRange(deadline.Value, before + timeout, DateTime.UtcNow + timeout);
    }

    [Fact]
    public void Defaults_Are15SecondsForUnaryAndNoneForStreams()
    {
        var options = new NetworkClientOptions();
        Assert.Equal(TimeSpan.FromSeconds(15), options.Timeout);
        Assert.Null(options.StreamTimeout);
    }

    [Fact]
    public void Unary_GetsTimeout()
    {
        var options = new NetworkClientOptions { Timeout = TimeSpan.FromSeconds(7), StreamTimeout = TimeSpan.FromMinutes(2) };
        var before = DateTime.UtcNow;

        AssertDeadlineIn(DeadlineOf(MethodType.Unary, options), before, options.Timeout);
    }

    [Fact]
    public void BlockingUnary_GetsTimeout()
    {
        var options = new NetworkClientOptions { Timeout = TimeSpan.FromSeconds(7) };
        var inner = new CapturingInvoker();
        var before = DateTime.UtcNow;

        inner.Intercept(new DefaultDeadlineInterceptor(options))
            .BlockingUnaryCall(NewMethod(MethodType.Unary), null, default, new StringValue());

        AssertDeadlineIn(inner.Options.Deadline, before, options.Timeout);
    }

    [Theory]
    [InlineData(MethodType.ServerStreaming)]
    [InlineData(MethodType.ClientStreaming)]
    [InlineData(MethodType.DuplexStreaming)]
    public void Streams_GetStreamTimeout(MethodType type)
    {
        var options = new NetworkClientOptions { Timeout = TimeSpan.FromSeconds(7), StreamTimeout = TimeSpan.FromMinutes(2) };
        var before = DateTime.UtcNow;

        AssertDeadlineIn(DeadlineOf(type, options), before, options.StreamTimeout!.Value);
    }

    [Theory]
    [InlineData(MethodType.ServerStreaming)]
    [InlineData(MethodType.ClientStreaming)]
    [InlineData(MethodType.DuplexStreaming)]
    public void Streams_WithoutStreamTimeout_GetNoDeadline(MethodType type)
    {
        Assert.Null(DeadlineOf(type, new NetworkClientOptions()));
    }

    [Fact]
    public void InfiniteTimeout_GivesUnaryNoDeadline()
    {
        Assert.Null(DeadlineOf(MethodType.Unary, new NetworkClientOptions { Timeout = Timeout.InfiniteTimeSpan }));
    }

    [Theory]
    [InlineData(MethodType.Unary)]
    [InlineData(MethodType.ServerStreaming)]
    [InlineData(MethodType.ClientStreaming)]
    [InlineData(MethodType.DuplexStreaming)]
    public void DeadlineOnTheCall_IsKept(MethodType type)
    {
        var deadline = DateTime.UtcNow.AddHours(1);
        var options = new NetworkClientOptions { StreamTimeout = TimeSpan.FromMinutes(2) };

        Assert.Equal(deadline, DeadlineOf(type, options, new CallOptions(deadline: deadline)));
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-5)]
    public void NonPositiveTimeouts_AreRejected(int seconds)
    {
        var timeout = TimeSpan.FromSeconds(seconds);
        Assert.Throws<ArgumentOutOfRangeException>(
            () => new DefaultDeadlineInterceptor(new NetworkClientOptions { Timeout = timeout }));
        Assert.Throws<ArgumentOutOfRangeException>(
            () => new DefaultDeadlineInterceptor(new NetworkClientOptions { StreamTimeout = timeout }));
    }

    /// <summary>
    /// The service-client helpers send the unary default as grpc-timeout; a raw channel from
    /// NetworkClient.Create sends none, and no HttpClient timeout cuts it short either.
    /// </summary>
    [Fact]
    public async Task ServiceClientHelper_SendsTheDefaultDeadline_RawChannelDoesNot()
    {
        var port = FindFreePort();
        var timeouts = new List<string?>();

        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
            options.ListenLocalhost(port, listenOptions => listenOptions.Protocols = HttpProtocols.Http2));
        var app = builder.Build();
        app.Run(context =>
        {
            lock (timeouts)
                timeouts.Add(context.Request.Headers["grpc-timeout"].SingleOrDefault());
            // Trailers-only response: UNIMPLEMENTED.
            context.Response.ContentType = "application/grpc";
            context.Response.Headers["grpc-status"] = "12";
            return Task.CompletedTask;
        });

        try
        {
            await app.StartAsync();
            await WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            var baseUrl = $"http://127.0.0.1:{port}";
            var signer = Signer.FromHex(PrivateKey);

            var helperClient = NetworkClient.CreateNetworkServiceClient(baseUrl, signer);
            var ex = await Assert.ThrowsAsync<RpcException>(
                () => helperClient.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            using var channel = NetworkClient.Create(new NetworkClientOptions { BaseUrl = baseUrl }, signer);
            var rawClient = new PaymentApi.NetworkService.NetworkServiceClient(channel);
            ex = await Assert.ThrowsAsync<RpcException>(
                () => rawClient.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            Assert.Equal(2, timeouts.Count);
            Assert.InRange(ParseGrpcTimeout(timeouts[0]), TimeSpan.FromSeconds(10), TimeSpan.FromSeconds(15));
            Assert.Null(timeouts[1]);
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }

    /// <summary>
    /// grpc-timeout: an integer followed by a unit (H, M, S, m, u, n).
    /// </summary>
    private static TimeSpan ParseGrpcTimeout(string? value)
    {
        Assert.NotNull(value);
        var amount = long.Parse(value[..^1]);
        return value[^1] switch
        {
            'H' => TimeSpan.FromHours(amount),
            'M' => TimeSpan.FromMinutes(amount),
            'S' => TimeSpan.FromSeconds(amount),
            'm' => TimeSpan.FromMilliseconds(amount),
            'u' => TimeSpan.FromMicroseconds(amount),
            'n' => TimeSpan.FromTicks(amount / 100),
            _ => throw new FormatException($"invalid grpc-timeout {value}"),
        };
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
    /// Records the options of the call it receives and returns an inert call.
    /// </summary>
    private sealed class CapturingInvoker : CallInvoker
    {
        public MethodType? Type { get; private set; }
        public CallOptions Options { get; private set; }

        private void Capture<TRequest, TResponse>(Method<TRequest, TResponse> method, CallOptions options)
        {
            Type = method.Type;
            Options = options;
        }

        public override TResponse BlockingUnaryCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request)
        {
            Capture(method, options);
            return default!;
        }

        public override AsyncUnaryCall<TResponse> AsyncUnaryCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request)
        {
            Capture(method, options);
            return new AsyncUnaryCall<TResponse>(
                Task.FromResult<TResponse>(default!), Task.FromResult(new Metadata()),
                () => Status.DefaultSuccess, () => new Metadata(), () => { });
        }

        public override AsyncServerStreamingCall<TResponse> AsyncServerStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request)
        {
            Capture(method, options);
            return new AsyncServerStreamingCall<TResponse>(
                null!, Task.FromResult(new Metadata()), () => Status.DefaultSuccess, () => new Metadata(), () => { });
        }

        public override AsyncClientStreamingCall<TRequest, TResponse> AsyncClientStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options)
        {
            Capture(method, options);
            return new AsyncClientStreamingCall<TRequest, TResponse>(
                null!, Task.FromResult<TResponse>(default!), Task.FromResult(new Metadata()),
                () => Status.DefaultSuccess, () => new Metadata(), () => { });
        }

        public override AsyncDuplexStreamingCall<TRequest, TResponse> AsyncDuplexStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options)
        {
            Capture(method, options);
            return new AsyncDuplexStreamingCall<TRequest, TResponse>(
                null!, null!, Task.FromResult(new Metadata()),
                () => Status.DefaultSuccess, () => new Metadata(), () => { });
        }
    }
}
