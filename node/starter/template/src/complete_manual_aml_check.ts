import {type Client, NetworkService} from "@t-0/provider-sdk";

export default async function completeManualAmlCheck(
    networkClient: Client<typeof NetworkService>,
    paymentId: bigint,
): Promise<void> {
  // Pay-Out Provider role — Step 2.6 (optional). If your payOut handler returned
  // manualAmlCheck instead of accepted (see ./service.ts), run your AML check
  // out-of-band and report the outcome with this call.
  const response = await networkClient.completeManualAmlCheck({
    paymentId,
    result: {case: 'approved', value: {}},
    // If your check failed, report a rejection instead:
    // result: {case: 'rejected', value: {reason: 'AML check failed'}},
  })

  switch (response.result.case) {
    case 'approved': {
      const approved = response.result.value;
      // Pay-in provider approved the updated quotes — proceed with the payout using
      // the updated amounts, then report the outcome via finalizePayout.
      console.log(`Manual AML check approved for payment ${paymentId}: quoteId=${approved.payOutQuoteId}, clientQuoteId=${approved.payOutClientQuoteId}`, 'payOutAmount=', approved.payOutAmount, 'settlementAmount=', approved.settlementAmount)
      break;
    }
    case 'rejected':
      // Pay-in provider rejected the updated quotes — do NOT proceed with the payout.
      console.log(`Updated quotes rejected for payment ${paymentId}, do not proceed with the payout`)
      break;
    default:
      console.error("unexpected result type")
  }
}
