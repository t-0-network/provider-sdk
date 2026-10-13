using Microsoft.Extensions.Hosting;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// Sample quote fetch. Registered with the host so it runs only after the server has bound.
public class GetQuote(
    NetworkService.NetworkServiceClient client,
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
                    Console.WriteLine($"Step 1.4: Got quote id={response.Success.QuoteId?.QuoteId_}");
                    break;
                case GetQuoteResponse.ResultOneofCase.Failure:
                    Console.WriteLine($"Quote failed: {response.Failure.Reason}");
                    break;
                default:
                    Console.WriteLine("Unknown result type");
                    break;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error getting quote: {ex.Message}");
        }
    }
}
