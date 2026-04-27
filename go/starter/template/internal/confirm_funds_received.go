package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
)

// ConfirmFundsReceived notifies the t-0 Network that an end-user has paid.
//
// Pay-In Provider role — Step 3A.3.
// Call this after you have matched an incoming fiat payment to a payment intent
// (using the payment reference you returned from GetPaymentDetails). Settlement
// with the beneficiary provider will proceed once this confirmation is accepted.
func ConfirmFundsReceived(
	ctx context.Context,
	paymentIntentClient payment_intentconnect.PaymentIntentServiceClient,
	paymentIntentId uint64,
	confirmationCode string,
	transactionReference string,
) {
	response, err := paymentIntentClient.ConfirmFundsReceived(ctx, connect.NewRequest(&payment_intent.ConfirmFundsReceivedRequest{
		PaymentIntentId:      paymentIntentId,
		ConfirmationCode:     confirmationCode,
		PaymentMethod:        common.PaymentMethodType_PAYMENT_METHOD_TYPE_SEPA,
		TransactionReference: transactionReference,
		// optional: if your provider has multiple legal entities, set OriginatorProviderLegalEntityId
	}))
	if err != nil {
		log.Printf("Error confirming funds received: %s\n", err.Error())
		return
	}

	switch r := response.Msg.Result.(type) {
	case *payment_intent.ConfirmFundsReceivedResponse_Accept_:
		log.Printf("Funds accepted for payment intent %d\n", paymentIntentId)
	case *payment_intent.ConfirmFundsReceivedResponse_Reject_:
		log.Printf("Funds rejected for payment intent %d: %s\n", paymentIntentId, r.Reject.GetReason())
	default:
		log.Println("Unknown response type")
	}
}
