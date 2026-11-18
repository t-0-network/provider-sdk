using T0.ProviderSdk.Api.Ivms101.V1.Ivms;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// TODO: Step 3B.2 Create a payment intent for a real end-user when they want to pay.
// Beneficiary Provider role: store the returned payment_intent_id to match it with the
// PaymentIntentUpdate notification your BeneficiaryService handler receives once the end-user
// completes the pay-in (see Services/PaymentIntentBeneficiaryHandler.cs).
public static class CreatePaymentIntent
{
    public static async Task CreateAsync(PaymentIntentService.PaymentIntentServiceClient client)
    {
        try
        {
            var response = await client.CreatePaymentIntentAsync(new CreatePaymentIntentRequest
            {
                ExternalReference = Guid.NewGuid().ToString(), // idempotency key
                Currency = "EUR",
                Amount = new Decimal { Unscaled = 500, Exponent = 0 }, // 500 EUR
                TravelRuleData = new CreatePaymentIntentRequest.Types.TravelRuleData
                {
                    // TODO: populate real IVMS101 beneficiary information for your end-user.
                    Beneficiary = { new Person() }
                }
            });

            switch (response.ResultCase)
            {
                case CreatePaymentIntentResponse.ResultOneofCase.Success:
                    Console.WriteLine($"Created payment intent id={response.Success.PaymentIntentId} with {response.Success.PayInDetails.Count} pay-in option(s)");
                    break;
                case CreatePaymentIntentResponse.ResultOneofCase.Failure:
                    Console.WriteLine($"Failed to create payment intent: {response.Failure.Reason}");
                    break;
                default:
                    Console.WriteLine("Unknown result type");
                    break;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error creating payment intent: {ex.Message}");
        }
    }
}
