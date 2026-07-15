package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
)

// CompleteManualAmlCheck reports the outcome of a manual AML check to the t-0 Network.
//
// Pay-Out Provider role — Step 2.6 (optional).
// Call this if your PayOut handler returned manual_aml_check instead of accepted
// (see internal/handler/payment.go) and you have finished the AML check out-of-band.
// On approval the response carries the updated pay-out amounts and quote ids —
// proceed with the payout using those. On rejection do not proceed with the payout.
func CompleteManualAmlCheck(
	ctx context.Context,
	networkClient paymentconnect.NetworkServiceClient,
	paymentId uint64,
) {
	response, err := networkClient.CompleteManualAmlCheck(ctx, connect.NewRequest(&payment.CompleteManualAmlCheckRequest{
		PaymentId: paymentId,
		Result: &payment.CompleteManualAmlCheckRequest_Approved_{
			Approved: &payment.CompleteManualAmlCheckRequest_Approved{},
		},
		// If your check failed, report a rejection instead:
		// Result: &payment.CompleteManualAmlCheckRequest_Rejected_{
		// 	Rejected: &payment.CompleteManualAmlCheckRequest_Rejected{Reason: "AML check failed"},
		// },
	}))
	if err != nil {
		log.Printf("Error completing manual AML check: %s\n", err.Error())
		return
	}

	switch r := response.Msg.Result.(type) {
	case *payment.CompleteManualAmlCheckResponse_Approved_:
		// Pay-in provider approved the updated quotes — proceed with the payout using
		// the updated amounts, then report the outcome via FinalizePayout.
		log.Printf("Manual AML check approved for payment %d: pay-out amount %v, settlement amount %v, quote id %d, client quote id %s\n",
			paymentId, r.Approved.GetPayOutAmount(), r.Approved.GetSettlementAmount(),
			r.Approved.GetPayOutQuoteId(), r.Approved.GetPayOutClientQuoteId())
	case *payment.CompleteManualAmlCheckResponse_Rejected_:
		// Pay-in provider rejected the updated quotes — do NOT proceed with the payout.
		log.Printf("Updated quotes rejected for payment %d, do not proceed with the payout\n", paymentId)
	default:
		log.Println("Unknown response type")
	}
}
