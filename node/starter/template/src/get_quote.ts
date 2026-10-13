import {type Client, NetworkService, PaymentMethodType, QuoteType} from "@t-0/provider-sdk";
import {decimalFromString, decimalToString} from "./lib.js";

export default async function getQuote(networkClient: Client<typeof NetworkService>) {
  try {
    const usdgbp = await networkClient.getQuote({
      payOutCurrency: "GBP",
      payOutMethod: PaymentMethodType.SEPA,
      quoteType: QuoteType.REALTIME,
      amount: {
        amount: {
          case: 'payOutAmount',
          value: decimalFromString("50")
        },
      }
    })

    switch (usdgbp.result.case) {
      case 'success': {
        const rate = usdgbp.result.value.rate;
        console.log(`USD/GBP: ${rate === undefined ? "not provided" : decimalToString(rate)}`)
        break;
      }
      case 'failure':
        console.error(usdgbp.result.value.reason)
        break;
      default:
        console.error("unexpected result type")
    }
  } catch (error) {
    // A failed call is logged, and the provider keeps serving.
    console.error(`Error getting quote: ${error instanceof Error ? error.message : String(error)}`)
  }
}
