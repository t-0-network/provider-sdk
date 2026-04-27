import {type Client, PaymentIntentNetwork} from "@t-0/provider-sdk";
import {toProtoDecimal} from "./lib";
import {randomUUID} from "node:crypto";

export default async function createPaymentIntent(
    paymentIntentClient: Client<typeof PaymentIntentNetwork.PaymentIntentService>,
) {
  // Beneficiary Provider role — Step 3B.2.
  // Store the returned paymentIntentId to correlate with the PaymentIntentUpdate
  // notification you'll receive on your BeneficiaryService handler once the end-user
  // completes the pay-in.
  const response = await paymentIntentClient.createPaymentIntent({
    externalReference: randomUUID(), // idempotency key — reuse to retry without duplicating the intent
    currency: 'EUR',
    amount: toProtoDecimal(500, 0), // end-user pays 500 EUR
    travelRuleData: {
      // TODO: populate real IVMS101 beneficiary information for your end-user.
      beneficiary: [{}],
    },
  })

  switch (response.Result.case) {
    case 'success':
      console.log(
          `Created payment intent id=${response.Result.value.paymentIntentId}`,
          `with ${response.Result.value.payInDetails.length} pay-in option(s)`,
      )
      // TODO: persist (paymentIntentId, externalReference) and present the
      // payInDetails options to your end-user.
      break;
    case 'failure':
      console.log(`Failed to create payment intent: ${response.Result.value.reason}`)
      break;
    default:
      console.error("unexpected result type")
  }
}
