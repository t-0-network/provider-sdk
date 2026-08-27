using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace {{PROJECT_NAME_PASCAL}}.Services;

// TODO: Step 2.3 Test payment submission
public static class SubmitPayment
{
    public static async Task SubmitAsync(NetworkService.NetworkServiceClient client)
    {
        try
        {
            var clientId = Guid.NewGuid().ToString();
            var response = await client.CreatePaymentAsync(new CreatePaymentRequest
            {
                PaymentClientId = clientId,
                Amount = new PaymentAmount
                {
                    PayOutAmount = new Decimal { Unscaled = 10, Exponent = 0 }
                },
                Currency = "GBP",
                PaymentDetails = new PaymentDetails
                {
                    Sepa = new PaymentDetails.Types.Sepa
                    {
                        Iban = "GB12345567890",
                        BeneficiaryName = "Max Mustermann"
                    }
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
            }
        }
        catch (Grpc.Core.RpcException ex)
        {
            Console.WriteLine($"Error submitting payment: {ex.Status.StatusCode} - {ex.Message}");
        }
    }
}
