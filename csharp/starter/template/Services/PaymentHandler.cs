using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;

namespace {{PROJECT_NAME_PASCAL}}.Services;

// Please refer to docs and proto definitions to understand the purpose of each method.
public class PaymentHandler(
    NetworkService.NetworkServiceClient networkClient,
    ILogger<PaymentHandler> logger) : ProviderService.ProviderServiceBase
{
    // TODO: Step 2.1 Implement how you handle updates of payments initiated by you
    public override Task<UpdatePaymentResponse> UpdatePayment(
        UpdatePaymentRequest request, ServerCallContext context)
    {
        logger.LogInformation("UpdatePayment: payment_id={PaymentId}", request.PaymentId);
        return Task.FromResult(new UpdatePaymentResponse());
    }

    // TODO: Step 2.4 Implement how you do payouts (payments initiated by your counterparts)
    public override async Task<PayoutResponse> PayOut(
        PayoutRequest request, ServerCallContext context)
    {
        logger.LogInformation("PayOut: payment_id={PaymentId}, currency={Currency}",
            request.PaymentId, request.Currency);

        // optional: if this payment needs a manual AML check on your side, respond with
        // ManualAmlCheck here, before making the payout, and report the outcome later via
        // CompleteManualAmlCheck (see Services/CompleteManualAmlCheck.cs). Do not finalize on
        // this path — the payout only proceeds once the check is approved.
        // return new PayoutResponse { ManualAmlCheck = new PayoutResponse.Types.ManualAmlCheck() };

        // TODO: FinalizePayout should be called when your system completes the payout
        await networkClient.FinalizePayoutAsync(new FinalizePayoutRequest
        {
            PaymentId = request.PaymentId,
            Success = new FinalizePayoutRequest.Types.Success
            {
                Receipt = new PaymentReceipt
                {
                    Sepa = new PaymentReceipt.Types.Sepa
                    {
                        BankingTransactionReferenceId = "123456"
                    }
                }
            }
        });

        // optional: if your provider has multiple legal entities, set BeneficiaryProviderLegalEntityId
        return new PayoutResponse { Accepted = new PayoutResponse.Types.Accepted() };
    }

    // TODO: Optionally implement handling of limit update notifications
    public override Task<UpdateLimitResponse> UpdateLimit(
        UpdateLimitRequest request, ServerCallContext context)
    {
        return Task.FromResult(new UpdateLimitResponse());
    }

    // TODO: Optionally implement handling of new ledger transactions
    public override Task<AppendLedgerEntriesResponse> AppendLedgerEntries(
        AppendLedgerEntriesRequest request, ServerCallContext context)
    {
        return Task.FromResult(new AppendLedgerEntriesResponse());
    }

    // TODO: Implement "Last Look" — verify final rates and approve after AML check.
    // The request includes PayOutFix — the fixed charge in USD for this payout.
    // Consider it alongside PayOutRate and PayOutAmount when deciding to accept.
    public override Task<ApprovePaymentQuoteResponse> ApprovePaymentQuotes(
        ApprovePaymentQuoteRequest request, ServerCallContext context)
    {
        return Task.FromResult(new ApprovePaymentQuoteResponse
        {
            Accepted = new ApprovePaymentQuoteResponse.Types.Accepted()
        });
    }
}
