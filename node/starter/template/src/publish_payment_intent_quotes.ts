import {type Client, PaymentIntentNetwork, PaymentMethodType} from "@t-0/provider-sdk";
import {toProtoDecimal} from "./lib";
import {randomUUID} from "node:crypto";
import {timestampFromDate} from "@bufbuild/protobuf/wkt";

export default async function publishPaymentIntentQuotes(
    paymentIntentClient: Client<typeof PaymentIntentNetwork.PaymentIntentService>,
    quotePublishingInterval: number,
): Promise<void> {
  // TODO: Step 3A.1 replace this with fetching pay-in quotes from your systems and publishing them into t-0 Network.
  // We recommend publishing at least once per 5 seconds, but not more than once per second.
  const tick = async () => {
    try {
      // NOTE: Every updateQuote request discards all previous payment intent quotes
      // that were published before. Combine multiple quotes into a single request.
      await paymentIntentClient.updateQuote({
        paymentIntentQuotes: [{
          currency: 'EUR',
          paymentMethod: PaymentMethodType.SEPA,
          expiration: timestampFromDate(new Date(Date.now() + 30 * 1000)), // 30 seconds from now
          timestamp: timestampFromDate(new Date()),
          bands: [{
            clientQuoteId: randomUUID(),
            maxAmount: toProtoDecimal(1000, 0), // max 1000 USD for this band
            // rate is always USD/XXX, so for EUR quote should be USD/EUR
            rate: toProtoDecimal(92, -2), // rate 0.92
          }],
        }],
      })
    } catch (error) {
      console.error(error);
      return
    }
    console.log("payment intent quote published")
  }

  await tick()
  setInterval(tick, quotePublishingInterval);
}
