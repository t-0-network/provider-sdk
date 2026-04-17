package handler

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
)

/*
  Payment Intent Flow — Beneficiary Provider role.

  Implement this handler if you are a beneficiary provider (you receive settlement
  for the crypto side). Please refer to docs and proto definition comments to
  understand the full flow.
*/

type BeneficiaryServiceImplementation struct{}

func NewBeneficiaryServiceImplementation() *BeneficiaryServiceImplementation {
	return &BeneficiaryServiceImplementation{}
}

var _ payment_intentconnect.BeneficiaryServiceHandler = (*BeneficiaryServiceImplementation)(nil)

// TODO: Step 3B.3 Implement how you handle notifications about your payment intents.
//
// The network calls this endpoint when the status of one of your payment intents
// changes (e.g. funds received from the end-user). Correlate req.PaymentIntentId
// with the id you stored after calling CreatePaymentIntent and update your
// internal state accordingly.
func (s *BeneficiaryServiceImplementation) PaymentIntentUpdate(
	ctx context.Context, req *connect.Request[payment_intent.PaymentIntentUpdateRequest],
) (*connect.Response[payment_intent.PaymentIntentUpdateResponse], error) {
	switch u := req.Msg.Update.(type) {
	case *payment_intent.PaymentIntentUpdateRequest_FundsReceived_:
		log.Printf("payment intent %d: funds received, settlement_amount=%v payment_method=%s transaction_reference=%s\n",
			req.Msg.PaymentIntentId,
			u.FundsReceived.GetSettlementAmount(),
			u.FundsReceived.GetPaymentMethod(),
			u.FundsReceived.GetTransactionReference(),
		)
	default:
		log.Printf("payment intent %d: unknown update variant\n", req.Msg.PaymentIntentId)
	}
	return connect.NewResponse(&payment_intent.PaymentIntentUpdateResponse{}), nil
}
