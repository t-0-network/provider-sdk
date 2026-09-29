using Grpc.Core;
using Grpc.Core.Interceptors;
using ProtoValidate;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Provider;
using ProtoDecimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace T0.ProviderSdk.Tests.Validation;

public class ValidationTests
{
    private readonly Validator _validator = new();

    // ==================== Response Validation ====================

    [Fact]
    public void ValidDecimalResponse_Passes()
    {
        var msg = new ProtoDecimal { Unscaled = 12345, Exponent = 2 };
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    [Fact]
    public void InvalidDecimalResponse_ExponentTooHigh_Fails()
    {
        var msg = new ProtoDecimal { Exponent = 100 };
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
        Assert.NotEmpty(result.Violations);
    }

    [Fact]
    public void InvalidDecimalResponse_ExponentTooLow_Fails()
    {
        var msg = new ProtoDecimal { Exponent = -20 };
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
    }

    [Theory]
    [InlineData(-8)]
    [InlineData(0)]
    [InlineData(8)]
    public void BoundaryExponentValues_Pass(int exponent)
    {
        var msg = new ProtoDecimal { Exponent = exponent };
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    [Theory]
    [InlineData(-9)]
    [InlineData(9)]
    public void BoundaryExponentValues_Fail(int exponent)
    {
        var msg = new ProtoDecimal { Exponent = exponent };
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
    }

    [Fact]
    public void ValidPayoutResponse_Passes()
    {
        var msg = new PayoutResponse { Accepted = new PayoutResponse.Types.Accepted() };
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    [Fact]
    public void EmptyResponseWithoutConstraints_Passes()
    {
        var msg = new UpdatePaymentResponse();
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    // ==================== Request Validation ====================

    [Fact]
    public void ValidAppendLedgerEntriesRequest_Passes()
    {
        var msg = new AppendLedgerEntriesRequest
        {
            Transactions =
            {
                new AppendLedgerEntriesRequest.Types.Transaction
                {
                    TransactionId = 1,
                    Entries = { new AppendLedgerEntriesRequest.Types.LedgerEntry() },
                    Payout = new AppendLedgerEntriesRequest.Types.Transaction.Types.Payout
                    {
                        PaymentId = 1,
                    },
                }
            }
        };
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    [Fact]
    public void InvalidAppendLedgerEntriesRequest_EmptyTransactions_Fails()
    {
        var msg = new AppendLedgerEntriesRequest();
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
        Assert.NotEmpty(result.Violations);
    }

    [Fact]
    public void InvalidRequest_TransactionIdZero_Fails()
    {
        var msg = new AppendLedgerEntriesRequest
        {
            Transactions =
            {
                new AppendLedgerEntriesRequest.Types.Transaction
                {
                    TransactionId = 0, // must be > 0
                    Entries = { new AppendLedgerEntriesRequest.Types.LedgerEntry() },
                    Payout = new AppendLedgerEntriesRequest.Types.Transaction.Types.Payout
                    {
                        PaymentId = 1,
                    },
                }
            }
        };
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
    }

    [Fact]
    public void ValidDecimalRequest_Passes()
    {
        var msg = new ProtoDecimal { Unscaled = 100, Exponent = 2 };
        var result = _validator.Validate(msg, failFast: false);
        Assert.True(result.IsSuccess);
    }

    [Fact]
    public void InvalidDecimalRequest_Fails()
    {
        var msg = new ProtoDecimal { Exponent = 100 };
        var result = _validator.Validate(msg, failFast: false);
        Assert.False(result.IsSuccess);
    }

    // ==================== Server Interceptor Tests ====================

    [Fact]
    public async Task ServerInterceptor_InvalidResponse_ThrowsInternal()
    {
        var interceptor = new ValidationInterceptor();

        Task<ProtoDecimal> Continuation(ProtoDecimal request, ServerCallContext ctx)
            => Task.FromResult(new ProtoDecimal { Exponent = 100 }); // invalid

        var ex = await Assert.ThrowsAsync<RpcException>(() =>
            interceptor.UnaryServerHandler(
                new ProtoDecimal { Exponent = 2 },
                TestServerCallContext.Create(),
                Continuation));

        Assert.Equal(StatusCode.Internal, ex.StatusCode);
        Assert.Contains("response validation failed", ex.Status.Detail);
    }

    [Fact]
    public async Task ServerInterceptor_ValidResponse_PassesThrough()
    {
        var interceptor = new ValidationInterceptor();

        Task<ProtoDecimal> Continuation(ProtoDecimal request, ServerCallContext ctx)
            => Task.FromResult(new ProtoDecimal { Exponent = 2 }); // valid

        var result = await interceptor.UnaryServerHandler(
            new ProtoDecimal { Exponent = 2 },
            TestServerCallContext.Create(),
            Continuation);

        Assert.Equal(2, result.Exponent);
    }

    // ==================== Test Helpers ====================

    private class TestServerCallContext : ServerCallContext
    {
        private TestServerCallContext() { }
        public static TestServerCallContext Create() => new();

        protected override Task WriteResponseHeadersAsyncCore(Metadata responseHeaders) => Task.CompletedTask;
        protected override ContextPropagationToken CreatePropagationTokenCore(ContextPropagationOptions? options) => null!;
        protected override string MethodCore => "/test/Method";
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
