using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;

namespace MyProvider.Services;

// TODO: Step 3A.3 Confirm funds received.
// Pay-In Provider role: call this after you have matched an incoming fiat payment to a payment
// intent, using the payment reference you returned from GetPaymentDetails. Settlement with the
// beneficiary provider proceeds once this confirmation is accepted.
public static class ConfirmFundsReceived
{
    public static async Task ConfirmAsync(
        PaymentIntentService.PaymentIntentServiceClient client,
        ulong paymentIntentId,
        string confirmationCode,
        string transactionReference)
    {
        try
        {
            var response = await client.ConfirmFundsReceivedAsync(new ConfirmFundsReceivedRequest
            {
                PaymentIntentId = paymentIntentId,
                ConfirmationCode = confirmationCode,
                PaymentMethod = PaymentMethodType.Sepa,
                TransactionReference = transactionReference,
                // optional: if your provider has multiple legal entities, set OriginatorProviderLegalEntityId
            });

            switch (response.ResultCase)
            {
                case ConfirmFundsReceivedResponse.ResultOneofCase.Accept:
                    Console.WriteLine($"Funds accepted for payment intent {paymentIntentId}");
                    break;
                case ConfirmFundsReceivedResponse.ResultOneofCase.Reject:
                    Console.WriteLine($"Funds rejected for payment intent {paymentIntentId}: {response.Reject.Reason}");
                    break;
                default:
                    Console.WriteLine("Unknown result type");
                    break;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error confirming funds received: {ex.Message}");
        }
    }
}
