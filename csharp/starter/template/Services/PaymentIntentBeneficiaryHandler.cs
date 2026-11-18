using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;

namespace MyProvider.Services;

// Payment Intent Flow — Beneficiary Provider role. Implement this handler if you receive the
// settlement of payment intents you created. Please refer to docs and proto definitions to
// understand the full flow.
public class PaymentIntentBeneficiaryHandler(ILogger<PaymentIntentBeneficiaryHandler> logger)
    : BeneficiaryService.BeneficiaryServiceBase
{
    // TODO: Step 3B.3 Implement how you handle notifications about your payment intents.
    // The network calls this endpoint when the status of one of your payment intents changes (e.g.
    // funds received from the end-user). Match request.PaymentIntentId with the id you stored after
    // CreatePaymentIntent and update your internal state.
    public override Task<PaymentIntentUpdateResponse> PaymentIntentUpdate(
        PaymentIntentUpdateRequest request, ServerCallContext context)
    {
        switch (request.UpdateCase)
        {
            case PaymentIntentUpdateRequest.UpdateOneofCase.FundsReceived:
                logger.LogInformation(
                    "payment intent {PaymentIntentId}: funds received, payment_method={PaymentMethod} transaction_reference={TransactionReference}",
                    request.PaymentIntentId, request.FundsReceived.PaymentMethod, request.FundsReceived.TransactionReference);
                break;
            default:
                logger.LogInformation("payment intent {PaymentIntentId}: unknown update variant", request.PaymentIntentId);
                break;
        }

        return Task.FromResult(new PaymentIntentUpdateResponse());
    }
}
