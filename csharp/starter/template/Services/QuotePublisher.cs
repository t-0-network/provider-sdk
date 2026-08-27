using Google.Protobuf.WellKnownTypes;
using T0.ProviderSdk.Api.Tzero.V1.Common;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using T0.ProviderSdk.Hosting;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider.Services;

// TODO: Step 1.3 Replace this with fetching quotes from your systems and publishing them into the T-0 Network.
// Recommended: publish at least once per 5 seconds, but not more than once per second.
public class QuotePublisher(NetworkService.NetworkServiceClient client)
    : QuotePublisherService(TimeSpan.FromSeconds(5))
{
    protected override Task PublishQuotesAsync(CancellationToken ct)
    {
        var currency = "EUR";
        var paymentMethod = PaymentMethodType.Sepa;
        var now = DateTimeOffset.UtcNow;
        var expiration = Timestamp.FromDateTimeOffset(now.AddSeconds(30));
        var timestamp = Timestamp.FromDateTimeOffset(now);

        // NOTE: Every UpdateQuote request discards all previous quotes.
        // Combine multiple quotes into a single request.
        client.UpdateQuote(new UpdateQuoteRequest
        {
            PayOut = // Quote at which you take USDT and pay out local currency (off-ramp)
            {
                new UpdateQuoteRequest.Types.Quote
                {
                    Currency = currency,
                    QuoteType = QuoteType.Realtime,
                    PaymentMethod = paymentMethod,
                    Expiration = expiration,
                    Timestamp = timestamp,
                    Bands =
                    {
                        new UpdateQuoteRequest.Types.Quote.Types.Band
                        {
                            ClientQuoteId = Guid.NewGuid().ToString(),
                            MaxAmount = new Decimal { Unscaled = 1000, Exponent = 0 },
                            Rate = new Decimal { Unscaled = 88, Exponent = -2 }, // 0.88
                        // optional: set Fix to charge a fixed fee per transfer (e.g. wire fees)
                        // Fix = new Decimal { Unscaled = 5, Exponent = 0 } // $5 fixed charge
                        }
                    }
                }
            }
        });

        return Task.CompletedTask;
    }
}
