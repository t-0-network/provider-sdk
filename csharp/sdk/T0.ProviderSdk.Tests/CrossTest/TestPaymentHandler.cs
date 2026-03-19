using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.Payment;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// Minimal ProviderService implementation that records PayOut calls.
/// Used by cross-language integration tests.
/// </summary>
public class TestPaymentHandler : ProviderService.ProviderServiceBase
{
    public List<PayoutRequest> PayOutCalls { get; } = new();

    public override Task<PayoutResponse> PayOut(PayoutRequest request, ServerCallContext context)
    {
        PayOutCalls.Add(request);
        return Task.FromResult(new PayoutResponse());
    }

    public override Task<UpdatePaymentResponse> UpdatePayment(UpdatePaymentRequest request, ServerCallContext context)
        => Task.FromResult(new UpdatePaymentResponse());

    public override Task<UpdateLimitResponse> UpdateLimit(UpdateLimitRequest request, ServerCallContext context)
        => Task.FromResult(new UpdateLimitResponse());

    public override Task<AppendLedgerEntriesResponse> AppendLedgerEntries(AppendLedgerEntriesRequest request, ServerCallContext context)
        => Task.FromResult(new AppendLedgerEntriesResponse());

    public override Task<ApprovePaymentQuoteResponse> ApprovePaymentQuotes(ApprovePaymentQuoteRequest request, ServerCallContext context)
        => Task.FromResult(new ApprovePaymentQuoteResponse());
}
