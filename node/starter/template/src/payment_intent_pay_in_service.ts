import {
    type Client,
    HandlerContext,
    PaymentIntentNetwork,
    PaymentIntentPayInProvider,
} from "@t-0/provider-sdk";

/*
  Payment Intent Flow — Pay-In Provider role.

  Implement this service if you are a pay-in provider (you receive fiat from end-users).
  Please refer to docs and proto definition comments to understand the full flow.
 */
const CreatePayInProviderService = (paymentIntentClient: Client<typeof PaymentIntentNetwork.PaymentIntentService>) => {
    return {
        // TODO: Step 3A.2 Implement how you return payment details for the end-user.
        //
        // The network calls this endpoint during CreatePaymentIntent processing. Return
        // payment details (bank account, mobile money number, etc.) for each requested
        // paymentMethod, including a unique payment reference that will let you match
        // the incoming fiat payment back to this payment intent.
        //
        // Store (paymentIntentId, confirmationCode) so you can validate it later in
        // confirmFundsReceived.
        async getPaymentDetails(req: PaymentIntentPayInProvider.GetPaymentDetailsRequest, _: HandlerContext) {
            console.log(`Received GetPaymentDetails for payment intent ${req.paymentIntentId}, methods: ${req.paymentMethods.join(',')}`)
            return {
                result: {
                    case: 'details',
                    value: {
                        paymentDetails: [], // TODO: populate one PaymentDetails per requested paymentMethod
                    },
                },
            } as unknown as PaymentIntentPayInProvider.GetPaymentDetailsResponse
        },
    }
};

export default CreatePayInProviderService;
