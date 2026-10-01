using Google.Protobuf;
using Grpc.Core;
using Grpc.Core.Interceptors;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;
using StringValue = Google.Protobuf.WellKnownTypes.StringValue;

namespace T0.ProviderSdk.Tests.Network;

public class DefaultDeadlineInterceptorTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private static readonly Marshaller<StringValue> Marshaller =
        Marshallers.Create(value => value.ToByteArray(), StringValue.Parser.ParseFrom);

    private static Method<StringValue, StringValue> NewMethod(MethodType type) =>
        new(type, "test.v1.StreamTest", type.ToString(), Marshaller, Marshaller);

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
    public void Defaults_Are15SecondsForUnaryAnd5MinutesForStreams()
    {
        var options = new NetworkClientOptions();
        Assert.Equal(TimeSpan.FromSeconds(15), options.Timeout);
        Assert.Equal(TimeSpan.FromMinutes(5), options.StreamTimeout);
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
    public void Streams_GetStreamTimeout(MethodType type)
    {
        var options = new NetworkClientOptions { Timeout = TimeSpan.FromSeconds(7), StreamTimeout = TimeSpan.FromMinutes(2) };
        var before = DateTime.UtcNow;

        AssertDeadlineIn(DeadlineOf(type, options), before, options.StreamTimeout);
    }

    [Theory]
    [InlineData(MethodType.ServerStreaming)]
    [InlineData(MethodType.ClientStreaming)]
    public void Streams_GetFiveMinutesByDefault(MethodType type)
    {
        var before = DateTime.UtcNow;

        AssertDeadlineIn(DeadlineOf(type, new NetworkClientOptions()), before, TimeSpan.FromMinutes(5));
    }

    [Theory]
    [InlineData(MethodType.Unary)]
    [InlineData(MethodType.ServerStreaming)]
    [InlineData(MethodType.ClientStreaming)]
    public void DeadlineOnTheCall_ReplacesTheDefault_LongerOrShorter(MethodType type)
    {
        var options = new NetworkClientOptions();

        var longer = DateTime.UtcNow.AddHours(1);
        Assert.Equal(longer, DeadlineOf(type, options, new CallOptions(deadline: longer)));

        var shorter = DateTime.UtcNow.AddSeconds(1);
        Assert.Equal(shorter, DeadlineOf(type, options, new CallOptions(deadline: shorter)));
    }

    [Fact]
    public void DuplexStream_IsRejected_WithoutReachingTheInvoker()
    {
        var inner = new CapturingInvoker();
        var invoker = inner.Intercept(new DefaultDeadlineInterceptor(new NetworkClientOptions()));

        var ex = Assert.Throws<RpcException>(
            () => invoker.AsyncDuplexStreamingCall(NewMethod(MethodType.DuplexStreaming), null, default));

        Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);
        Assert.Equal("bidirectional streams are not supported", ex.Status.Detail);
        Assert.Null(inner.Type);
    }

    [Theory]
    [InlineData(0L)]
    [InlineData(-10_000L)] // Timeout.InfiniteTimeSpan
    public void TimeoutsThatAreNotPositive_AreRefusedWhenSet(long ticks)
    {
        var timeout = TimeSpan.FromTicks(ticks);
        var options = new NetworkClientOptions();

        var ex = Assert.Throws<ArgumentOutOfRangeException>(() => options.Timeout = timeout);
        Assert.StartsWith("Timeout must be a positive duration", ex.Message);
        ex = Assert.Throws<ArgumentOutOfRangeException>(() => options.StreamTimeout = timeout);
        Assert.StartsWith("StreamTimeout must be a positive duration", ex.Message);

        Assert.Equal(TimeSpan.FromSeconds(15), options.Timeout);
        Assert.Equal(TimeSpan.FromMinutes(5), options.StreamTimeout);
    }

    [Fact]
    public void ChannelHttpClient_HasNoTimeout()
    {
        using var httpClient = NetworkClient.CreateHttpClient(Signer.FromHex(PrivateKey));

        Assert.Equal(Timeout.InfiniteTimeSpan, httpClient.Timeout);
    }

    [Fact]
    public async Task ClientStream_WithoutAFirstMessage_FailsAtTheStreamDeadline()
    {
        // Nothing listens there: the request never gets past the signing handler, which waits for
        // the first message.
        var options = new NetworkClientOptions
        {
            BaseUrl = $"http://127.0.0.1:{TestPorts.FindFreePort()}",
            StreamTimeout = TimeSpan.FromMilliseconds(300),
        };
        var invoker = NetworkClient.Create(options, Signer.FromHex(PrivateKey), i => i);

        using var call = invoker.AsyncClientStreamingCall(NewMethod(MethodType.ClientStreaming), null, default);
        var ex = await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync).WaitAsync(TimeSpan.FromSeconds(2));

        Assert.Equal(StatusCode.DeadlineExceeded, ex.StatusCode);
    }

    [Fact]
    public async Task Helpers_SendTheConfiguredTimeout_WithoutValidatingTheRequest()
    {
        var timeouts = new List<string?>();
        var (app, baseUrl) = await StartTimeoutRecorderAsync(timeouts);

        try
        {
            var signer = Signer.FromHex(PrivateKey);

            var payment = NetworkClient.CreateNetworkServiceClient(
                new NetworkClientOptions { BaseUrl = baseUrl, Timeout = TimeSpan.FromSeconds(7) }, signer);
            var ex = await Assert.ThrowsAsync<RpcException>(
                () => payment.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            // Fails buf.validate (payment_intent_id 0), and is sent all the same: the network validates.
            var paymentIntent = NetworkClient.CreatePaymentIntentNetworkServiceClient(
                new NetworkClientOptions { BaseUrl = baseUrl, Timeout = TimeSpan.FromSeconds(9) }, signer);
            ex = await Assert.ThrowsAsync<RpcException>(
                () => paymentIntent.ConfirmPaymentAsync(new PaymentIntentApi.ConfirmPaymentRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            Assert.Equal(2, timeouts.Count);
            Assert.InRange(ParseGrpcTimeout(timeouts[0]), TimeSpan.FromSeconds(2), TimeSpan.FromSeconds(7));
            Assert.InRange(ParseGrpcTimeout(timeouts[1]), TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(9));
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }

    // The form master's docs and starter use: a base URL string and the default timeouts.
    [Fact]
    public async Task BaseUrlOverloads_UseTheBaseUrlAndTheDefaultTimeout()
    {
        var timeouts = new List<string?>();
        var (app, baseUrl) = await StartTimeoutRecorderAsync(timeouts);

        try
        {
            var signer = Signer.FromHex(PrivateKey);

            var payment = NetworkClient.CreateNetworkServiceClient(baseUrl, signer);
            var ex = await Assert.ThrowsAsync<RpcException>(
                () => payment.UpdateQuoteAsync(new PaymentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            var paymentIntent = NetworkClient.CreatePaymentIntentNetworkServiceClient(baseUrl, signer);
            ex = await Assert.ThrowsAsync<RpcException>(
                () => paymentIntent.ConfirmPaymentAsync(new PaymentIntentApi.ConfirmPaymentRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            // Both reached the server at baseUrl, each with the 15 s default.
            Assert.Equal(2, timeouts.Count);
            Assert.All(timeouts, timeout =>
                Assert.InRange(ParseGrpcTimeout(timeout), TimeSpan.FromSeconds(10), TimeSpan.FromSeconds(15)));
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }

    [Fact]
    public async Task ClientStream_SendsTheStreamTimeout_OrTheCallersDeadline()
    {
        var timeouts = new List<string?>();
        var (app, baseUrl) = await StartTimeoutRecorderAsync(timeouts);

        try
        {
            var cases = new (TimeSpan? StreamTimeout, DateTime? Deadline)[]
            {
                (null, null), // the default, 5 minutes
                (TimeSpan.FromMinutes(2), null),
                (null, DateTime.UtcNow.AddHours(1)), // longer than the default
            };
            foreach (var (streamTimeout, deadline) in cases)
            {
                var options = new NetworkClientOptions { BaseUrl = baseUrl };
                if (streamTimeout is { } value)
                    options.StreamTimeout = value;
                var invoker = NetworkClient.Create(options, Signer.FromHex(PrivateKey), i => i);

                using var call = invoker.AsyncClientStreamingCall(
                    NewMethod(MethodType.ClientStreaming), null, new CallOptions(deadline: deadline));
                // The request goes out with its first message.
                await call.RequestStream.WriteAsync(new StringValue { Value = "m1" });
                var ex = await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync)
                    .WaitAsync(TimeSpan.FromSeconds(10));
                Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);
            }

            Assert.Equal(3, timeouts.Count);
            Assert.InRange(ParseGrpcTimeout(timeouts[0]), TimeSpan.FromMinutes(4), TimeSpan.FromMinutes(5));
            Assert.InRange(ParseGrpcTimeout(timeouts[1]), TimeSpan.FromSeconds(110), TimeSpan.FromMinutes(2));
            Assert.InRange(ParseGrpcTimeout(timeouts[2]), TimeSpan.FromMinutes(59), TimeSpan.FromHours(1));
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
    }

    /// <summary>
    /// HTTP/2 server that records each request's grpc-timeout and answers UNIMPLEMENTED unread.
    /// </summary>
    private static async Task<(WebApplication App, string BaseUrl)> StartTimeoutRecorderAsync(List<string?> timeouts)
    {
        var port = TestPorts.FindFreePort();
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
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            return (app, $"http://127.0.0.1:{port}");
        }
        catch
        {
            await app.DisposeAsync();
            throw;
        }
    }

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
