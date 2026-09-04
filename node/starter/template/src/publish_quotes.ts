import {type Client, NetworkService, PaymentMethodType, QuoteType} from "@t-0/provider-sdk";
import {decimalFromString} from "./lib";
import {randomUUID} from "node:crypto";
import {timestampFromDate} from "@bufbuild/protobuf/wkt";

export default async function publishQuotes(networkClient: Client<typeof NetworkService>, quotePublishingInterval: number): Promise<void> {
  // TODO: Step 1.3 replace this with receiving quotes from you systems and publishing them into t-0 Network. We recommend publishing at least once per 5 seconds, but not more than once per second
  const sampleRate = decimalFromString("0.873");
  const sampleMaxAmount = decimalFromString("25000");
  const tick =  async () => {
    try {
      //NOTE: Every update quote request discard all previous quotes that were published before.
      // So if you want to publish multiple quotes, you need to combine them into a single request.
      // Otherwise, if you send multiple requests, only the quotes from the last one will be available.
      await networkClient.updateQuote({
        payOut: [{
          bands: [{
            // note that rate is always USD/XXX, os that for EUR quote should be USD/EUR
            rate: sampleRate, // rate 0.873
            maxAmount: sampleMaxAmount, // maximum amount in USD, could be 1000,5000,10000 or 25000
            clientQuoteId: randomUUID(),
            // optional: set fix to charge a fixed fee per transfer (e.g. wire fees)
            // fix: decimalFromString("5"), // $5 fixed charge
          }],
          currency: 'EUR',
          expiration: timestampFromDate(new Date(Date.now() + 30 * 1000)), // expiration time (30 seconds from now)
          quoteType: QuoteType.REALTIME, // REALTIME is only one supported right now
          paymentMethod: PaymentMethodType.SEPA,
          timestamp: timestampFromDate(new Date()), // Current timestamp
        }]
      })
    } catch (error) {
      console.error(error);
      return
    }
    console.log("quote published")
  }

  await tick()
  setInterval(tick, quotePublishingInterval);
}