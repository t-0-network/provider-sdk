package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/google/uuid"
	"github.com/t-0-network/provider-sdk/go/api/ivms101/v1/ivms"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
)

// CreatePaymentIntent initiates a new payment intent with the t-0 Network.
//
// Beneficiary Provider role — Step 3B.2.
// Store the returned PaymentIntentId to correlate with the PaymentIntentUpdate
// notification you'll receive on your BeneficiaryService handler once the end-user
// completes the pay-in.
func CreatePaymentIntent(ctx context.Context, paymentIntentClient payment_intentconnect.PaymentIntentServiceClient) {
	response, err := paymentIntentClient.CreatePaymentIntent(ctx, connect.NewRequest(&payment_intent.CreatePaymentIntentRequest{
		ExternalReference: uuid.NewString(), // idempotency key — reuse to retry without duplicating the intent
		Currency:          "EUR",
		Amount:            &common.Decimal{Unscaled: 500, Exponent: 0}, // end-user pays 500 EUR
		TravelRuleData: &payment_intent.CreatePaymentIntentRequest_TravelRuleData{
			// TODO: populate real IVMS101 beneficiary information for your end-user.
			Beneficiary: []*ivms.Person{{}},
		},
	}))
	if err != nil {
		log.Printf("Error creating payment intent: %s\n", err.Error())
		return
	}

	switch r := response.Msg.Result.(type) {
	case *payment_intent.CreatePaymentIntentResponse_Success_:
		log.Printf("Created payment intent id=%d with %d pay-in option(s)\n",
			r.Success.GetPaymentIntentId(),
			len(r.Success.GetPayInDetails()),
		)
		// TODO: persist (payment_intent_id, external_reference) and present the
		// PayInDetails options to your end-user.
	case *payment_intent.CreatePaymentIntentResponse_Failure_:
		log.Printf("Failed to create payment intent: %s\n", r.Failure.GetReason())
	default:
		log.Println("Unknown response type")
	}
}
