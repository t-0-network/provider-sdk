using Grpc.Core;
using ProtoValidate;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Provider;
using T0.ProviderSdk.Tests.Proto;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// A predefined rule the application declares in its own protos (Protos/t0/sdk/test) is applied
/// with no configuration, as in Go, Java and Python (V12). ProtoValidate applies one only from the
/// files it is given; the SDK gives it those of the message's imports.
/// </summary>
public class PredefinedRuleTests
{
    private const string Invalid = "response validation failed: to: must start with 0x";

    [Fact]
    public void Check_BrokenPredefinedRule_IsInternalResponseValidationFailed()
    {
        var ex = Assert.Throws<RpcException>(
            () => Validate.Check(new PredefinedRuleReply { To = "abc", Ctrl = "abc" }));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal(Invalid, ex.Status.Detail);
    }

    [Fact]
    public void Check_Valid_ReturnsTheSameMessage()
    {
        var message = new PredefinedRuleReply { To = "0xabc", Ctrl = "abc" };

        Assert.Same(message, Validate.Check(message));
    }

    [Fact]
    public void Check_BrokenStandardRule_FailsAsBefore()
    {
        var message = new PredefinedRuleReply { To = "0xabc", Ctrl = "x" };
        var violation = Assert.Single(new Validator().Validate(message, failFast: false).Violations);

        var ex = Assert.Throws<RpcException>(() => Validate.Check(message));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal($"response validation failed: ctrl: {violation.Message}", ex.Status.Detail);
    }

    [Fact]
    public async Task Interceptor_HandlerBreakingPredefinedRule_IsAnsweredWithInternal()
    {
        var ex = await Assert.ThrowsAsync<RpcException>(() => new ValidationInterceptor().UnaryServerHandler(
            new PayoutRequest(),
            new CallContext(),
            (PayoutRequest _, ServerCallContext _) =>
                Task.FromResult(new PredefinedRuleReply { To = "abc", Ctrl = "abc" })));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Equal(Invalid, ex.Status.Detail);
    }

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
