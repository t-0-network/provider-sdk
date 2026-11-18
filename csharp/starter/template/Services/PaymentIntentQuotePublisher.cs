using Google.Protobuf.WellKnownTypes;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// Pay-In Provider role — Step 3A.1. These quotes tell the network what exchange rates you accept
// when an end-user pays via one of your supported payment methods.
// TODO: Step 3A.1 Replace this with fetching pay-in quotes from your systems and publishing them.
// Recommended: publish at least once per 5 seconds, but not more than once per second.
// Each quote expires at publish time + publishing interval + 35 s: a client that gets it just
// before the next publish still has 30 s to use it; the 5 s cover network delay.
public class PaymentIntentQuotePublisher(PaymentIntentService.PaymentIntentServiceClient client)
    : QuotePublisherService(ConfiguredInterval)
{
    protected override Task PublishQuotesAsync(CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;

        // NOTE: Every UpdateQuote request discards all previous payment intent quotes.
        // Combine multiple quotes into a single request.
        client.UpdateQuote(new UpdateQuoteRequest
        {
            PaymentIntentQuotes =
            {
                new UpdateQuoteRequest.Types.Quote
                {
                    Currency = "EUR",
                    PaymentMethod = PaymentMethodType.Sepa,
                    Expiration = Timestamp.FromDateTimeOffset(now + Interval + TimeSpan.FromSeconds(35)),
                    Timestamp = Timestamp.FromDateTimeOffset(now),
                    Bands =
                    {
                        new UpdateQuoteRequest.Types.Quote.Types.Band
                        {
                            ClientQuoteId = Guid.NewGuid().ToString(),
                            MaxAmount = new Decimal { Unscaled = 1000, Exponent = 0 }, // max amount in USD the band applies to
                            // rate is always USD/XXX, so for an EUR quote it is USD/EUR
                            Rate = new Decimal { Unscaled = 92, Exponent = -2 }, // 0.92
                        }
                    }
                }
            }
        });

        return Task.CompletedTask;
    }

    protected override void OnError(Exception ex)
        => Console.Error.WriteLine($"Error publishing payment intent quotes: {ex.Message}");
}
