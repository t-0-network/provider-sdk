package internal

import (
	"context"
	"log"
	"time"

	"connectrpc.com/connect"
	"github.com/google/uuid"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// PublishPaymentIntentQuotes publishes pay-in quotes into the t-0 Network.
//
// Pay-In Provider role — Step 3A.1.
// These quotes tell the network what exchange rates you're willing to accept
// when an end-user pays via one of your supported payment methods.
func PublishPaymentIntentQuotes(ctx context.Context, paymentIntentClient payment_intentconnect.PaymentIntentServiceClient) {
	// TODO: Step 3A.1 replace this with fetching pay-in quotes from your systems and publishing them.
	// We recommend publishing at least once per 5 seconds, but not more than once per second.

	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			currency := "EUR"
			paymentMethod := common.PaymentMethodType_PAYMENT_METHOD_TYPE_SEPA
			expiration := timestamppb.New(time.Now().Add(30 * time.Second)) // expiration time - 30 seconds from now
			timestamp := timestamppb.New(time.Now())                        // current timestamp

			// NOTE: Every UpdateQuote request discards all previous payment intent quotes
			// that were published before. Combine multiple quotes into a single request.
			_, err := paymentIntentClient.UpdateQuote(ctx, connect.NewRequest(&payment_intent.UpdateQuoteRequest{
				PaymentIntentQuotes: []*payment_intent.UpdateQuoteRequest_Quote{
					{
						Currency:      currency,
						PaymentMethod: paymentMethod,
						Expiration:    expiration,
						Timestamp:     timestamp,
						Bands: []*payment_intent.UpdateQuoteRequest_Quote_Band{
							{
								ClientQuoteId: uuid.NewString(),
								MaxAmount: &common.Decimal{
									Unscaled: 1000, // max amount in USD the band applies to
									Exponent: 0,
								},
								// rate is always USD/XXX, so for EUR quote should be USD/EUR
								Rate: &common.Decimal{ // rate 0.92
									Unscaled: 92,
									Exponent: -2,
								},
							},
						},
					},
				},
			}))
			if err != nil {
				log.Printf("Error updating payment intent quote: %s\n", err.Error())
				return
			}
		}
	}
}
