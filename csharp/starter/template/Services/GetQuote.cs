using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace {{PROJECT_NAME_PASCAL}}.Services;

public static class GetQuote
{
    public static async Task FetchAsync(NetworkService.NetworkServiceClient client)
    {
        try
        {
            var response = await client.GetQuoteAsync(new GetQuoteRequest
            {
                PayOutCurrency = "GBP",
                PayOutMethod = PaymentMethodType.Swift,
                QuoteType = QuoteType.Realtime,
                Amount = new PaymentAmount
                {
                    SettlementAmount = new Decimal { Unscaled = 500, Exponent = 0 } // amount in USD
                }
            });

            switch (response.ResultCase)
            {
                case GetQuoteResponse.ResultOneofCase.Success:
                    Console.WriteLine($"Step 1.4: Got quote id={response.Success.QuoteId.QuoteId_}");
                    break;
                case GetQuoteResponse.ResultOneofCase.Failure:
                    Console.WriteLine($"Quote failed: {response.Failure.Reason}");
                    break;
            }
        }
        catch (Grpc.Core.RpcException ex)
        {
            Console.WriteLine($"Error getting quote: {ex.Status.StatusCode} - {ex.Message}");
        }
    }
}
