using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;

namespace MyProvider.Services;

// Payment Intent Flow — Pay-In Provider role. Implement this handler if you receive fiat from
// end-users. Please refer to docs and proto definitions to understand the full flow.
public class PaymentIntentPayInHandler(ILogger<PaymentIntentPayInHandler> logger)
    : PayInProviderService.PayInProviderServiceBase
{
    // TODO: Step 3A.2 Implement how you return payment details for the end-user.
    // The network calls this endpoint while it processes CreatePaymentIntent. Return payment details
    // (bank account, mobile money number, etc.) for each requested payment method, with a unique
    // payment reference that lets you match the incoming fiat payment to this payment intent.
    // Store (payment_intent_id, confirmation_code): ConfirmFundsReceived needs them
    // (see Services/ConfirmFundsReceived.cs).
    public override Task<GetPaymentDetailsResponse> GetPaymentDetails(
        GetPaymentDetailsRequest request, ServerCallContext context)
    {
        logger.LogInformation("GetPaymentDetails: payment_intent_id={PaymentIntentId}, {Count} method(s)",
            request.PaymentIntentId, request.PaymentMethods.Count);

        // Example: an empty Details response. Replace it with your real payment instructions.
        return Task.FromResult(new GetPaymentDetailsResponse { Details = new GetPaymentDetailsResponse.Types.Details() });
    }
}
