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
    /// <summary>Set from Program before the host builds this service.</summary>
    public static int Port { get; set; }

    private CancellationTokenRegistration _started;

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // ApplicationStarted fires after the bind. A failed bind never gets here.
        _started = lifetime.ApplicationStarted.Register(() =>
        {
            Console.WriteLine($"Step 1.1: Provider server initialized on port {Port}");
            _ = FetchAsync(client);
        });
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
