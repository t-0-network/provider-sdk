using Grpc.Core;
using ProtoValidate;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.CrossTest;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// <see cref="Validate.Check"/>, the helper a handler validates its own response with (Go
/// <c>provider.Validate</c>, Node and Python <c>validate</c>, Java <c>Validate.check</c>): it returns a
/// valid message itself and fails an invalid one with the error <see cref="ValidationInterceptor"/>
/// gives for it.
/// </summary>
public class ValidateTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    // Two violations: the legal entity id must be greater than 0, the details at most 1024 characters.
    private static PayoutResponse TwoViolations() => new()
    {
        BeneficiaryProviderLegalEntityId = 0,
        Failed = new PayoutResponse.Types.Failed { Details = new string('x', 1025) },
    };

    [Fact]
    public void Valid_ReturnsTheSameMessage()
    {
        var message = new PayoutResponse { Accepted = new PayoutResponse.Types.Accepted() };

        Assert.Same(message, Validate.Check(message));
    }

    [Fact]
    public void Null_IsReturned_AsTheInterceptorPassesItOn()
    {
        Assert.Null(Validate.Check<PayoutResponse>(null!));
    }

    [Fact]
    public async Task Violations_AreInternalResponseValidationFailed_AsTheInterceptorAnswers()
    {
        var violations = new Validator().Validate(TwoViolations(), failFast: false).Violations;
        Assert.Equal(2, violations.Count);
        var expected = "response validation failed: "
            + $"beneficiary_provider_legal_entity_id: {violations.Single(v => v.Field.GetPath() == "beneficiary_provider_legal_entity_id").Message}; "
            + $"failed.details: {violations.Single(v => v.Field.GetPath() == "failed.details").Message}";

        var ex = Assert.Throws<RpcException>(() => Validate.Check(TwoViolations()));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal(expected, ex.Status.Detail);
        var fromInterceptor = await InterceptorError(TwoViolations());
        Assert.Equal(fromInterceptor.StatusCode, ex.StatusCode);
        Assert.Equal(fromInterceptor.Status.Detail, ex.Status.Detail);
    }

    [Fact]
    public async Task UnevaluableRule_IsInternalResponseValidationError_AsTheInterceptorAnswers()
    {
        var ex = Assert.Throws<RpcException>(() => Validate.Check(new UnevaluableRule()));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal($"response validation error: {UnevaluableRule.Cause()}", ex.Status.Detail);
        var fromInterceptor = await InterceptorError(new UnevaluableRule());
        Assert.Equal(fromInterceptor.StatusCode, ex.StatusCode);
        Assert.Equal(fromInterceptor.Status.Detail, ex.Status.Detail);
    }

    /// <summary>
    /// In a handler on a <see cref="T0ProviderServer"/>: a caught failure becomes the handler's own
    /// answer, and an uncaught one reaches the caller as the interceptor's error.
    /// </summary>
    [Fact]
    public async Task InAHandler_ACaughtFailureIsTheHandlersAnswer_AnUncaughtOneTheInterceptorsError()
    {
        var expected = Assert.Throws<RpcException>(() => Validate.Check(TwoViolations())).Status.Detail;
        var port = TestPorts.FindFreePort();
        var signer = Signer.FromHex(PrivateKey);
        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = signer.GetPublicKeyHexPrefixed(),
            Port = port,
        };
        var server = new T0ProviderServer(config, signer)
            .MapPaymentService<CheckingHandler>(NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer));
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var serverTask = server.RunAsync(cts.Token);

        try
        {
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            var client = NetworkClient.Create(
                new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" }, signer,
                invoker => new ProviderService.ProviderServiceClient(invoker));

            // PayOut catches the failure and answers with it.
            var caught = await client.PayOutAsync(new PayoutRequest());
            Assert.Equal(PayoutResponse.ResultOneofCase.Failed, caught.ResultCase);
            Assert.Equal(expected, caught.Failed.Details);

            // ApprovePaymentQuotes lets it propagate: an empty response, whose result is required.
            var expectedUncaught = Assert.Throws<RpcException>(
                () => Validate.Check(new ApprovePaymentQuoteResponse())).Status.Detail;
            var uncaught = await Assert.ThrowsAsync<RpcException>(
                () => client.ApprovePaymentQuotesAsync(new ApprovePaymentQuoteRequest()).ResponseAsync);
            Assert.Equal(StatusCode.Internal, uncaught.StatusCode);
            Assert.Equal(expectedUncaught, uncaught.Status.Detail);
        }
        finally
        {
            cts.Cancel();
            try { await serverTask; }
            catch (OperationCanceledException) { }
        }
    }

    public sealed class CheckingHandler : TestPaymentHandler
    {
        public override Task<PayoutResponse> PayOut(PayoutRequest request, ServerCallContext context)
        {
            try
            {
                return Task.FromResult(Validate.Check(TwoViolations()));
            }
            catch (RpcException e)
            {
                return Task.FromResult(new PayoutResponse
                {
                    Failed = new PayoutResponse.Types.Failed { Details = e.Status.Detail },
                });
            }
        }

        public override Task<ApprovePaymentQuoteResponse> ApprovePaymentQuotes(
            ApprovePaymentQuoteRequest request, ServerCallContext context) =>
            Task.FromResult(Validate.Check(new ApprovePaymentQuoteResponse()));
    }

    // The error ValidationInterceptor gives when a handler returns response.
    private static async Task<RpcException> InterceptorError<TResponse>(TResponse response) where TResponse : class =>
        await Assert.ThrowsAsync<RpcException>(() => new ValidationInterceptor().UnaryServerHandler(
            new PayoutRequest(),
            new CallContext(),
            (PayoutRequest _, ServerCallContext _) => Task.FromResult(response)));

    private sealed class CallContext : ServerCallContext
    {
        protected override Task WriteResponseHeadersAsyncCore(Metadata responseHeaders) => Task.CompletedTask;
        protected override ContextPropagationToken CreatePropagationTokenCore(ContextPropagationOptions? options) => null!;
        protected override string MethodCore => "/tzero.v1.payment.ProviderService/PayOut";
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
