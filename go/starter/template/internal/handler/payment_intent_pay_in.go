package handler

import (
	"context"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
)

/*
  Payment Intent Flow — Pay-In Provider role.

  Implement this handler if you are a pay-in provider (you receive fiat from end-users).
  Please refer to docs and proto definition comments to understand the full flow.
*/

type PayInProviderServiceImplementation struct {
	paymentIntentClient payment_intentconnect.PaymentIntentServiceClient
}

func NewPayInProviderServiceImplementation(
	paymentIntentClient payment_intentconnect.PaymentIntentServiceClient,
) *PayInProviderServiceImplementation {
	return &PayInProviderServiceImplementation{
		paymentIntentClient: paymentIntentClient,
	}
}

var _ payment_intentconnect.PayInProviderServiceHandler = (*PayInProviderServiceImplementation)(nil)

// TODO: Step 3A.2 Implement how you return payment details for the end-user.
//
// The network calls this endpoint during CreatePaymentIntent processing. Return
// payment details (bank account, mobile money number, etc.) for each requested
// payment method, including a unique payment reference that will let you match
// the incoming fiat payment back to this payment intent.
//
// Store (payment_intent_id, confirmation_code) so you can validate it later
// in ConfirmFundsReceived.
func (s *PayInProviderServiceImplementation) GetPaymentDetails(
	ctx context.Context, req *connect.Request[payment_intent.GetPaymentDetailsRequest],
) (*connect.Response[payment_intent.GetPaymentDetailsResponse], error) {
	// Example: return an empty Details response — replace with your real payment instructions.
	return connect.NewResponse(&payment_intent.GetPaymentDetailsResponse{
		Result: &payment_intent.GetPaymentDetailsResponse_Details_{
			Details: &payment_intent.GetPaymentDetailsResponse_Details{
				PaymentDetails: nil, // TODO: populate one PaymentDetails per requested payment_method
			},
		},
	}), nil
}
