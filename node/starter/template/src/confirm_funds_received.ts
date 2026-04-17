import {type Client, PaymentIntentNetwork, PaymentMethodType} from "@t-0/provider-sdk";

export default async function confirmFundsReceived(
    paymentIntentClient: Client<typeof PaymentIntentNetwork.PaymentIntentService>,
    paymentIntentId: bigint,
    confirmationCode: string,
    transactionReference: string,
) {
  // Pay-In Provider role — Step 3A.3.
  // Call this after you have matched an incoming fiat payment to a payment intent
  // (using the payment reference you returned from getPaymentDetails). Settlement
  // with the beneficiary provider will proceed once this confirmation is accepted.
  const response = await paymentIntentClient.confirmFundsReceived({
    paymentIntentId,
    confirmationCode,
    paymentMethod: PaymentMethodType.SEPA,
    transactionReference,
    // optional: if your provider has multiple legal entities, set originatorProviderLegalEntityId
  })

  switch (response.Result.case) {
    case 'accept':
      console.log(`Funds accepted for payment intent ${paymentIntentId}`)
      break;
    case 'reject':
      console.log(`Funds rejected for payment intent ${paymentIntentId}: ${response.Result.value.reason}`)
      break;
    default:
      console.error("unexpected result type")
  }
}
