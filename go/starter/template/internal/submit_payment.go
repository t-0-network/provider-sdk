package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/google/uuid"
	"github.com/t-0-network/provider-sdk/go/api/ivms101/v1/ivms"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
)

// naturalPerson is a schema-valid sample IVMS101 natural person.
func naturalPerson(primaryIdentifier, secondaryIdentifier string) *ivms.Person {
	return &ivms.Person{Person: &ivms.Person_NaturalPerson{NaturalPerson: &ivms.NaturalPerson{
		Name: &ivms.NaturalPersonName{NameIdentifiers: []*ivms.NaturalPersonNameId{{
			PrimaryIdentifier:   primaryIdentifier,
			SecondaryIdentifier: secondaryIdentifier,
			NameIdentifierType:  ivms.NaturalPersonNameTypeCode_NATURAL_PERSON_NAME_TYPE_CODE_LEGL,
		}}},
	}}}
}

// SubmitPayment sends a test payment to the t-0 Network.
//
// Payout Provider role — Step 2.3.
// The network accepts the payment for processing: an accepted result is not a
// completed payout — follow the PaymentUpdate notifications on your
// ProviderService handler for the outcome (see internal/handler/payment.go).
// A retry must resend the same payment client id and request — the network
// deduplicates on it. Running this demo again creates a new id and therefore
// a new payment.
func SubmitPayment(ctx context.Context, networkClient paymentconnect.NetworkServiceClient) {
	paymentClientId := uuid.NewString()

	response, err := networkClient.CreatePayment(ctx, connect.NewRequest(&payment.CreatePaymentRequest{
		PaymentClientId: paymentClientId, // idempotency key — reuse to retry without duplicating the payment
		Amount: &payment.PaymentAmount{Amount: &payment.PaymentAmount_PayOutAmount{
			PayOutAmount: &common.Decimal{Unscaled: 10, Exponent: 0}, // payout 10 GBP
		}},
		Currency: "GBP",
		PaymentDetails: &common.PaymentDetails{Details: &common.PaymentDetails_Sepa_{
			Sepa: &common.PaymentDetails_Sepa{
				Iban:            "GB29NWBK60161331926819",
				BeneficiaryName: "Max Mustermann",
			},
		}},
		TravelRuleData: &payment.CreatePaymentRequest_TravelRuleData{
			// TODO: populate real IVMS101 information for your originating customer
			// and the beneficiary. These sample persons are schema-valid placeholders.
			Originator:  []*ivms.Person{naturalPerson("Doe", "John")},
			Beneficiary: []*ivms.Person{naturalPerson("Mustermann", "Max")},
		},
	}))
	if err != nil {
		log.Printf("Error submitting payment: %s\n", err.Error()) // handle errors appropriately
		return
	}

	switch r := response.Msg.Result.(type) {
	case *payment.CreatePaymentResponse_Accepted_:
		log.Printf("Payment accepted, id=%d\n", r.Accepted.GetPaymentId())
		// TODO: an accepted result is not a completed payout — wait for the
		// PaymentUpdate notifications for the outcome.
	case *payment.CreatePaymentResponse_Failure_:
		log.Printf("Payment failed with reason: %s\n", r.Failure.GetReason())
	default:
		log.Println("Unknown result type")
	}
}
