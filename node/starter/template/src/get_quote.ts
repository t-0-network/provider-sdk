import {type Client, NetworkService, PaymentMethodType, QuoteType} from "@t-0/provider-sdk";
import {fromProtoDecimal, toProtoDecimal} from "./lib";

export default async function getQuote(networkClient: Client<typeof NetworkService>) {
  const usdgbp = await networkClient.getQuote({
    payOutCurrency: "GBP",
    payOutMethod: PaymentMethodType.SEPA,
    quoteType: QuoteType.REALTIME,
    amount: {
      amount: {
        case: 'payOutAmount',
        value: toProtoDecimal(50, 0)
      },
    }
  })

  switch (usdgbp.result.case) {
    case 'success':
      console.log(`USD/GBP: ${fromProtoDecimal(usdgbp.result.value.rate!)}`)
      break;
    case 'failure':
      console.error(usdgbp.result.value.reason)
      break;
    default:
      console.error("unexpected result type")
  }
}