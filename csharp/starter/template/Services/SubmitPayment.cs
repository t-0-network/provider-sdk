using T0.ProviderSdk.Api.Ivms101.V1.Ivms;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// TODO: Step 2.3 Test payment submission
// An accepted result is not a completed payout — the PaymentUpdate notifications on the
// ProviderService handler carry the outcome. A retry must resend the same payment client id
// and request — the network deduplicates on it; running this demo again generates a new id
// and therefore a new payment.
public static class SubmitPayment
{
    /// <summary>A schema-valid sample IVMS101 natural person.</summary>
    private static Person NaturalPersonSample(string primaryIdentifier, string secondaryIdentifier) => new()
    {
        NaturalPerson = new NaturalPerson
        {
            Name = new NaturalPersonName
            {
                NameIdentifiers =
                {
                    new NaturalPersonNameId
                    {
                        PrimaryIdentifier = primaryIdentifier,
                        SecondaryIdentifier = secondaryIdentifier,
                        NameIdentifierType = NaturalPersonNameTypeCode.Legl
                    }
                }
            }
        }
    };

    public static async Task SubmitAsync(NetworkService.NetworkServiceClient client)
    {
        try
        {
            var clientId = Guid.NewGuid().ToString();
            var response = await client.CreatePaymentAsync(new CreatePaymentRequest
            {
                PaymentClientId = clientId, // idempotency key — reuse to retry without duplicating the payment
                Amount = new PaymentAmount
                {
                    PayOutAmount = new Decimal { Unscaled = 10, Exponent = 0 }
                },
                Currency = "GBP",
                PaymentDetails = new PaymentDetails
                {
                    Sepa = new PaymentDetails.Types.Sepa
                    {
                        Iban = "GB29NWBK60161331926819",
                        BeneficiaryName = "Max Mustermann"
                    }
                },
                // TODO: populate real IVMS101 information for your originating customer
                // and the beneficiary. These sample persons are schema-valid placeholders.
                TravelRuleData = new CreatePaymentRequest.Types.TravelRuleData
                {
                    Originator = { NaturalPersonSample("Doe", "John") },
                    Beneficiary = { NaturalPersonSample("Mustermann", "Max") }
                }
            });

            switch (response.ResultCase)
            {
                case CreatePaymentResponse.ResultOneofCase.Accepted:
                    Console.WriteLine($"Step 2.3: Payment accepted, id={response.Accepted.PaymentId}");
                    break;
                case CreatePaymentResponse.ResultOneofCase.Failure:
                    Console.WriteLine($"Payment failed: {response.Failure.Reason}");
                    break;
                default:
                    Console.WriteLine("Unknown result type");
                    break;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error submitting payment: {ex.Message}");
        }
    }
}
