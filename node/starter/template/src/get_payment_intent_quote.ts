import {type Client, PaymentIntentNetwork} from "@t-0/provider-sdk";
import {toProtoDecimal} from "./lib";

export default async function getPaymentIntentQuote(
    paymentIntentClient: Client<typeof PaymentIntentNetwork.PaymentIntentService>,
) {
  // Beneficiary Provider role — Step 3B.1.
  // Use this to check available rates before creating a payment intent. The actual
  // settlement rate is determined when the pay-in provider confirms funds received.
  const response = await paymentIntentClient.getQuote({
    currency: "EUR",
    amount: toProtoDecimal(500, 0), // end-user pays 500 EUR
  })

  switch (response.Result.case) {
    case 'success':
      console.log(
          `Got ${response.Result.value.bestQuotes.length} best pay-in quotes`,
          `and ${response.Result.value.allQuotes.length} total quotes`,
      )
      break;
    case 'quoteNotFound':
      console.log("No pay-in quotes available for this currency/amount")
      break;
    default:
      console.error("unexpected result type")
  }
}
