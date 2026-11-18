using Google.Protobuf;
using Grpc.Core;
using Grpc.Core.Interceptors;
using Grpc.Health.V1;
using Microsoft.AspNetCore.Builder;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;
using StringValue = Google.Protobuf.WellKnownTypes.StringValue;

namespace T0.ProviderSdk.Tests.Network;

public class DefaultDeadlineInterceptorTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private static readonly Marshaller<StringValue> Marshaller =
        Marshallers.Create(value => value.ToByteArray(), StringValue.Parser.ParseFrom);

    private static readonly Dictionary<string, TimeSpan> NoMethodTimeouts = [];

    private static Method<StringValue, StringValue> NewMethod(MethodType type) =>
        new(type, "test.v1.StreamTest", type.ToString(), Marshaller, Marshaller);

    private static DateTime? DeadlineOf(MethodType type, NetworkClientOptions options, CallOptions callOptions = default)
    {
        var inner = new CapturingInvoker();
        var invoker = inner.Intercept(new DefaultDeadlineInterceptor(options, NoMethodTimeouts));
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
    public void Defaults_Are30SecondsForUnaryAnd5MinutesForStreams()
    {
        var options = new NetworkClientOptions();
        Assert.Equal(TimeSpan.FromSeconds(30), options.Timeout);
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

        inner.Intercept(new DefaultDeadlineInterceptor(options, NoMethodTimeouts))
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

    // The generated descriptors carry the tzero.v1.common.timeout option, an extension from an
    // imported file: the lookup must find it, not fall back to the client's timeout.
    [Fact]
    public void MethodTimeouts_ComeFromTheGeneratedDescriptors()
    {
        var provider = NetworkClient.MethodTimeouts(typeof(PaymentApi.ProviderService.ProviderServiceClient));
        Assert.Equal(TimeSpan.FromSeconds(29), provider["/tzero.v1.payment.ProviderService/ApprovePaymentQuotes"]);
        Assert.Equal(TimeSpan.FromSeconds(30), provider["/tzero.v1.payment.ProviderService/PayOut"]);

        var network = NetworkClient.MethodTimeouts(typeof(PaymentApi.NetworkService.NetworkServiceClient));
        Assert.Equal(TimeSpan.FromSeconds(30), network["/tzero.v1.payment.NetworkService/UpdateQuote"]);

        // A subclass of a generated client has the options of its service.
        var subclass = NetworkClient.MethodTimeouts(typeof(SubclassedProviderClient));
        Assert.Equal(TimeSpan.FromSeconds(29), subclass["/tzero.v1.payment.ProviderService/ApprovePaymentQuotes"]);

        // A service without the option, and a type that is not a generated client.
        Assert.Empty(NetworkClient.MethodTimeouts(typeof(Health.HealthClient)));
        Assert.Empty(NetworkClient.MethodTimeouts(typeof(CallInvoker)));
    }

    private sealed class SubclassedProviderClient(CallInvoker invoker)
        : PaymentApi.ProviderService.ProviderServiceClient(invoker);

    [Theory]
    [InlineData(10)]
    [InlineData(45)]
    public void MethodOption_ReplacesTheClientTimeout_LongerOrShorter(int timeoutSeconds)
    {
        var options = new NetworkClientOptions { Timeout = TimeSpan.FromSeconds(timeoutSeconds) };
        var inner = new CapturingInvoker();
        var client = new PaymentApi.ProviderService.ProviderServiceClient(inner.Intercept(new DefaultDeadlineInterceptor(
            options, NetworkClient.MethodTimeouts(typeof(PaymentApi.ProviderService.ProviderServiceClient)))));
        var before = DateTime.UtcNow;

        client.ApprovePaymentQuotesAsync(new PaymentApi.ApprovePaymentQuoteRequest());
        AssertDeadlineIn(inner.Options.Deadline, before, TimeSpan.FromSeconds(29));

        // A deadline on the call replaces the option, longer or shorter.
        var longer = DateTime.UtcNow.AddMinutes(1);
        client.ApprovePaymentQuotesAsync(new PaymentApi.ApprovePaymentQuoteRequest(), deadline: longer);
        Assert.Equal(longer, inner.Options.Deadline);
        var shorter = DateTime.UtcNow.AddSeconds(1);
        client.ApprovePaymentQuotesAsync(new PaymentApi.ApprovePaymentQuoteRequest(), deadline: shorter);
        Assert.Equal(shorter, inner.Options.Deadline);
    }

    [Fact]
    public void DuplexStream_IsRejected_WithoutReachingTheInvoker()
    {
        var inner = new CapturingInvoker();
        var invoker = inner.Intercept(new DefaultDeadlineInterceptor(new NetworkClientOptions(), NoMethodTimeouts));

        var ex = Assert.Throws<RpcException>(
            () => invoker.AsyncDuplexStreamingCall(NewMethod(MethodType.DuplexStreaming), null, default));

        Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);
        Assert.Equal("bidirectional streams are not supported", ex.Status.Detail);
        Assert.Null(inner.Type);
    }

    [Theory]
    [InlineData(0L)]
    [InlineData(-10_000L)] // Timeout.InfiniteTimeSpan
    [InlineData(2147483647L * TimeSpan.TicksPerMillisecond + 1)] // 1 tick over 2147483647 ms
    [InlineData(long.MaxValue)] // TimeSpan.MaxValue
    public void TimeoutsOutOfRange_AreRefusedWhenSet(long ticks)
    {
        var timeout = TimeSpan.FromTicks(ticks);
        var options = new NetworkClientOptions();

        var ex = Assert.Throws<ArgumentOutOfRangeException>(() => options.Timeout = timeout);
        Assert.Equal("timeout must be a positive duration of at most 2147483647 ms", ex.Message);
        ex = Assert.Throws<ArgumentOutOfRangeException>(() => options.StreamTimeout = timeout);
        Assert.Equal("stream timeout must be a positive duration of at most 2147483647 ms", ex.Message);

        Assert.Equal(TimeSpan.FromSeconds(30), options.Timeout);
        Assert.Equal(TimeSpan.FromMinutes(5), options.StreamTimeout);
    }

    [Fact]
    public void TimeoutsAtTheUpperBound_AreKept()
    {
        var max = TimeSpan.FromMilliseconds(2147483647L);
        var options = new NetworkClientOptions { Timeout = max, StreamTimeout = max };

        Assert.Equal(max, options.Timeout);
        Assert.Equal(max, options.StreamTimeout);
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

    // UpdateQuote has a timeout option of 30 s, which Timeout does not change; Health/Check has none
    // and gets Timeout.
    [Fact]
    public async Task Helpers_SendTheMethodsTimeoutOrTheConfiguredOne_WithoutValidatingTheRequest()
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

            // Fails buf.validate (payment_client_id empty), and is sent all the same: the network validates.
            ex = await Assert.ThrowsAsync<RpcException>(
                () => payment.CreatePaymentAsync(new PaymentApi.CreatePaymentRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            var health = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = baseUrl, Timeout = TimeSpan.FromSeconds(9) }, signer,
                invoker => new Health.HealthClient(invoker));
            ex = await Assert.ThrowsAsync<RpcException>(
                () => health.CheckAsync(new HealthCheckRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            Assert.Equal(3, timeouts.Count);
            Assert.InRange(ParseGrpcTimeout(timeouts[0]), TimeSpan.FromSeconds(25), TimeSpan.FromSeconds(30));
            Assert.InRange(ParseGrpcTimeout(timeouts[2]), TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(9));
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

            var paymentIntent = NetworkClient.CreatePaymentIntentServiceClient(baseUrl, signer);
            ex = await Assert.ThrowsAsync<RpcException>(
                () => paymentIntent.UpdateQuoteAsync(new PaymentIntentApi.UpdateQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Unimplemented, ex.StatusCode);

            // Both reached the server at baseUrl, each with its method's 30 s.
            Assert.Equal(2, timeouts.Count);
            Assert.All(timeouts, timeout =>
                Assert.InRange(ParseGrpcTimeout(timeout), TimeSpan.FromSeconds(25), TimeSpan.FromSeconds(30)));
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
    private static Task<(WebApplication App, string BaseUrl)> StartTimeoutRecorderAsync(List<string?> timeouts) =>
        TestServer.StartHttp2Async(context =>
        {
            lock (timeouts)
                timeouts.Add(context.Request.Headers["grpc-timeout"].SingleOrDefault());
            // Trailers-only response: UNIMPLEMENTED.
            context.Response.ContentType = "application/grpc";
            context.Response.Headers["grpc-status"] = "12";
            return Task.CompletedTask;
        });

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
