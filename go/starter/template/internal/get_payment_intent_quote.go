package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
)

// GetPaymentIntentQuote fetches indicative quotes for a pay-in currency/amount.
//
// Beneficiary Provider role — Step 3B.1.
// Use this to check available rates before creating a payment intent. The actual
// settlement rate is determined when the pay-in provider confirms funds received.
func GetPaymentIntentQuote(ctx context.Context, paymentIntentClient payment_intentconnect.PaymentIntentServiceClient) {
	quote, err := paymentIntentClient.GetQuote(ctx, connect.NewRequest(&payment_intent.GetQuoteRequest{
		Currency: "EUR",
		Amount:   &common.Decimal{Unscaled: 500, Exponent: 0}, // end-user pays 500 EUR
	}))
	if err != nil {
		log.Printf("Error getting payment intent quote: %s\n", err.Error())
		return
	}

	switch quote.Msg.Result.(type) {
	case *payment_intent.GetQuoteResponse_Success_:
		log.Printf("Got %d best pay-in quotes and %d total quotes\n",
			len(quote.Msg.GetSuccess().GetBestQuotes()),
			len(quote.Msg.GetSuccess().GetAllQuotes()),
		)
	case *payment_intent.GetQuoteResponse_QuoteNotFound_:
		log.Println("No pay-in quotes available for this currency/amount")
	default:
		log.Println("Unknown response type")
	}
}
