using Microsoft.Extensions.Hosting;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// Beneficiary Provider role — Step 3B.1. Fetches indicative pay-in quotes for a currency and amount,
// to check the available rates before you create a payment intent. The settlement rate is set when
// the pay-in provider confirms funds received. Registered with the host so it runs only after the
// server has bound.
public class GetPaymentIntentQuote(
    PaymentIntentService.PaymentIntentServiceClient client,
    IHostApplicationLifetime lifetime) : IHostedService
{
    private CancellationTokenRegistration _started;

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // ApplicationStarted fires after the bind. A failed bind never gets here.
        _started = lifetime.ApplicationStarted.Register(() => _ = FetchAsync(client));
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _started.Dispose();
        return Task.CompletedTask;
    }

    public static async Task FetchAsync(PaymentIntentService.PaymentIntentServiceClient client)
    {
        try
        {
            var response = await client.GetQuoteAsync(new GetQuoteRequest
            {
                Currency = "EUR",
                Amount = new Decimal { Unscaled = 500, Exponent = 0 } // 500 EUR
            });

            switch (response.ResultCase)
            {
                case GetQuoteResponse.ResultOneofCase.Success:
                    Console.WriteLine($"Step 3B.1: Got {response.Success.BestQuotes.Count} best pay-in quotes and {response.Success.AllQuotes.Count} total quotes");
                    break;
                case GetQuoteResponse.ResultOneofCase.QuoteNotFound:
                    Console.WriteLine("No pay-in quotes available for this currency and amount");
                    break;
                default:
                    Console.WriteLine("Unknown result type");
                    break;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error getting payment intent quote: {ex.Message}");
        }
    }
}
