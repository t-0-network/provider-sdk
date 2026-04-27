import {
    HandlerContext,
    PaymentIntentBeneficiary,
} from "@t-0/provider-sdk";

/*
  Payment Intent Flow — Beneficiary Provider role.

  Implement this service if you are a beneficiary provider (you receive settlement for the crypto side).
  Please refer to docs and proto definition comments to understand the full flow.
 */
const CreateBeneficiaryService = () => {
    return {
        // TODO: Step 3B.3 Implement how you handle notifications about your payment intents.
        //
        // The network calls this endpoint when the status of one of your payment intents
        // changes (e.g. funds received from the end-user). Correlate req.paymentIntentId
        // with the id you stored after calling createPaymentIntent and update your
        // internal state accordingly.
        async paymentIntentUpdate(req: PaymentIntentBeneficiary.PaymentIntentUpdateRequest, _: HandlerContext) {
            switch (req.update.case) {
                case 'fundsReceived':
                    console.log(
                        `Payment intent ${req.paymentIntentId}: funds received,`,
                        `settlementAmount=${JSON.stringify(req.update.value.settlementAmount)},`,
                        `paymentMethod=${req.update.value.paymentMethod},`,
                        `transactionReference=${req.update.value.transactionReference}`,
                    )
                    break;
                default:
                    console.log(`Payment intent ${req.paymentIntentId}: unknown update variant`)
            }
            return {} as PaymentIntentBeneficiary.PaymentIntentUpdateResponse
        },
    }
};

export default CreateBeneficiaryService;
