import {
    AppendLedgerEntriesRequest,
    AppendLedgerEntriesResponse,
    ApprovePaymentQuoteRequest,
    ApprovePaymentQuoteResponse,
    type Client,
    HandlerContext,
    NetworkService,
    PayoutRequest,
    PayoutResponse,
    UpdateLimitRequest,
    UpdateLimitResponse,
    UpdatePaymentRequest,
    UpdatePaymentResponse,
} from "@t-0/provider-sdk";

/*
  Please refer to docs, proto definition comments or source code comments to understand purpose of fields
 */
const CreateProviderService = (networkClient: Client<typeof NetworkService>) => {
    return {
        async updatePayment(req: UpdatePaymentRequest, _: HandlerContext) {
            // TODO: Step 2.1 implement how you handle updates of payment initiated by you
            console.log(`Received payment update for ${req.paymentId}, payment ${req.result.case}`)
            return {} as UpdatePaymentResponse
        },

        async payOut(req: PayoutRequest, _: HandlerContext) {
            // TODO: Step 2.4 implement how you do payouts (payments initiated by your counterparts)
            console.log(`Received payout request ${req.paymentId}`)

            // TODO: finalizePayout should be called when your system completes (or fails) the payout
            setInterval(() => {
                networkClient.finalizePayout({
                    paymentId: req.paymentId,
                    result: {
                        case: 'success',
                        value: {
                            receipt: {
                                details: {
                                    case: 'sepa',
                                    value: {
                                        bankingTransactionReferenceId: '1234567890',
                                    }
                                }
                            },
                        },
                    }
                })
            }, 2000);
            // optional: if your provider has multiple legal entities, set beneficiaryProviderLegalEntityId
            return {
                result: {
                    case: "accepted",
                    value: {},
                },
            } as PayoutResponse
        },

        async updateLimit(req: UpdateLimitRequest, _: HandlerContext) {
            // TODO: optionally implement updates on your limits and limits usage
            console.log(`Received update of limits with provider ${req.limits[0].counterpartId}`)
            return {} as UpdateLimitResponse
        },

        async appendLedgerEntries(req: AppendLedgerEntriesRequest, _: HandlerContext) {
            // TODO: optionally implement handling of new ledger transactions and new ledger entries
            console.log(`Received ledger entries for ${req.transactions} transaction(s)`)
            return {} as AppendLedgerEntriesResponse
        },

        async approvePaymentQuotes(req: ApprovePaymentQuoteRequest, _: HandlerContext) {
            // TODO: when the payment goes through the Manual AML Check on the pay-out provider side, the provider submitted the payment will have a last look to approve final quote
            // The request includes payOutFix — the fixed charge in USD for this payout.
            // Consider it alongside payOutRate and payOutAmount when deciding to accept.
            console.log(`Received approve payment quote request for ${req.paymentId}`)
            return {
                result: {
                    case: "accepted",
                    value: {}
                }
            } as ApprovePaymentQuoteResponse
        }
    }
};

export default CreateProviderService;
