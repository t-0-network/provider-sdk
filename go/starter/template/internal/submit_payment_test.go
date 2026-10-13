package internal

import (
	"bytes"
	"context"
	"log"
	"os"
	"regexp"
	"strings"
	"testing"

	"connectrpc.com/connect"
	"github.com/google/uuid"
	"github.com/t-0-network/provider-sdk/go/api/ivms101/v1/ivms"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
)

// recordingClient embeds the full interface and overrides only CreatePayment,
// so a signature change elsewhere still compiles and this fake records every
// call it receives.
type recordingClient struct {
	paymentconnect.NetworkServiceClient
	calls   int
	lastReq *payment.CreatePaymentRequest
	resp    *payment.CreatePaymentResponse
	err     error
}

func (c *recordingClient) CreatePayment(_ context.Context, req *connect.Request[payment.CreatePaymentRequest]) (*connect.Response[payment.CreatePaymentResponse], error) {
	c.calls++
	c.lastReq = req.Msg
	if c.err != nil {
		return nil, c.err
	}
	return connect.NewResponse(c.resp), nil
}

// runCapture runs SubmitPayment against the fake and returns what the standard
// logger wrote, with no stack trace expected in it.
func runCapture(t *testing.T, client *recordingClient) string {
	t.Helper()
	var out bytes.Buffer
	log.SetOutput(&out)
	log.SetFlags(0)
	t.Cleanup(func() { log.SetOutput(os.Stderr); log.SetFlags(log.LstdFlags) })

	SubmitPayment(context.Background(), client)
	return out.String()
}

// The same response the network sends: Accepted carries its amounts and the
// selected provider, because the proto marks them required.
func acceptedResponse() *payment.CreatePaymentResponse {
	return &payment.CreatePaymentResponse{Result: &payment.CreatePaymentResponse_Accepted_{
		Accepted: &payment.CreatePaymentResponse_Accepted{
			PaymentId:        42,
			SettlementAmount: &common.Decimal{Unscaled: 1250, Exponent: -2}, // 12.50 USD
			PayoutAmount:     &common.Decimal{Unscaled: 10},                 // 10 GBP
			PayoutProviderId: 1,
		},
	}}
}

func TestSubmitPayment_Request(t *testing.T) {
	client := &recordingClient{resp: acceptedResponse()}
	out := runCapture(t, client)

	if client.calls != 1 {
		t.Fatalf("CreatePayment called %d times, want exactly 1", client.calls)
	}
	req := client.lastReq

	if _, err := uuid.Parse(req.GetPaymentClientId()); err != nil {
		t.Errorf("payment_client_id %q is not a UUID: %v", req.GetPaymentClientId(), err)
	}

	payout, ok := req.GetAmount().GetAmount().(*payment.PaymentAmount_PayOutAmount)
	if !ok {
		t.Fatalf("amount is %T, want a pay_out_amount", req.GetAmount().GetAmount())
	}
	if payout.PayOutAmount.GetUnscaled() != 10 || payout.PayOutAmount.GetExponent() != 0 {
		t.Errorf("payout = %v, want unscaled 10 exponent 0", payout.PayOutAmount)
	}
	if req.GetCurrency() != "GBP" {
		t.Errorf("currency = %q, want GBP", req.GetCurrency())
	}

	sepa, ok := req.GetPaymentDetails().GetDetails().(*common.PaymentDetails_Sepa_)
	if !ok {
		t.Fatalf("payment_details is %T, want a sepa", req.GetPaymentDetails().GetDetails())
	}
	// The same checks the network's buf.validate rules apply to the SEPA demo data.
	if l := len(sepa.Sepa.GetIban()); l < 15 || l > 34 {
		t.Errorf("iban %q length %d, want 15..34", sepa.Sepa.GetIban(), l)
	}
	if !regexp.MustCompile(`^[A-Z]{2}[0-9]{2}[A-Z0-9]+$`).MatchString(sepa.Sepa.GetIban()) {
		t.Errorf("iban %q does not match the SEPA pattern", sepa.Sepa.GetIban())
	}
	if sepa.Sepa.GetIban() != "GB29NWBK60161331926819" {
		t.Errorf("iban = %q, want the sandbox IBAN the demo pins", sepa.Sepa.GetIban())
	}
	if sepa.Sepa.GetBeneficiaryName() != "Max Mustermann" {
		t.Errorf("beneficiary_name = %q, want Max Mustermann", sepa.Sepa.GetBeneficiaryName())
	}

	travel := req.GetTravelRuleData()
	if travel == nil {
		t.Fatal("travel_rule_data missing, it is required")
	}
	assertLegalName(t, "originator", travel.GetOriginator(), "Doe", "John")
	assertLegalName(t, "beneficiary", travel.GetBeneficiary(), "Mustermann", "Max")
	// The SEPA beneficiary name and the IVMS101 beneficiary describe the same person.
	if b := travel.GetBeneficiary()[0].GetNaturalPerson().GetName().GetNameIdentifiers()[0]; b.GetSecondaryIdentifier()+" "+b.GetPrimaryIdentifier() != sepa.Sepa.GetBeneficiaryName() {
		t.Errorf("IVMS101 beneficiary %q %q does not match sepa beneficiary_name %q",
			b.GetSecondaryIdentifier(), b.GetPrimaryIdentifier(), sepa.Sepa.GetBeneficiaryName())
	}

	if !strings.Contains(out, "Payment accepted") || !strings.Contains(out, "id=42") {
		t.Errorf("accepted result not logged, got %q", out)
	}
}

func assertLegalName(t *testing.T, who string, persons []*ivms.Person, primary, secondary string) {
	t.Helper()
	if len(persons) != 1 {
		t.Fatalf("%s has %d persons, want 1", who, len(persons))
	}
	id := persons[0].GetNaturalPerson().GetName().GetNameIdentifiers()
	if len(id) != 1 {
		t.Fatalf("%s has %d name identifiers, want 1", who, len(id))
	}
	if id[0].GetNameIdentifierType() != ivms.NaturalPersonNameTypeCode_NATURAL_PERSON_NAME_TYPE_CODE_LEGL {
		t.Errorf("%s name identifier type = %v, want LEGL", who, id[0].GetNameIdentifierType())
	}
	if id[0].GetPrimaryIdentifier() != primary || id[0].GetSecondaryIdentifier() != secondary {
		t.Errorf("%s name = %q %q, want %q %q", who, id[0].GetSecondaryIdentifier(), id[0].GetPrimaryIdentifier(), secondary, primary)
	}
}

func TestSubmitPayment_Failure(t *testing.T) {
	client := &recordingClient{resp: &payment.CreatePaymentResponse{Result: &payment.CreatePaymentResponse_Failure_{
		Failure: &payment.CreatePaymentResponse_Failure{
			Reason: payment.CreatePaymentResponse_Failure_REASON_QUOTE_NOT_FOUND,
		},
	}}}
	out := runCapture(t, client)

	if client.calls != 1 {
		t.Fatalf("CreatePayment called %d times, want exactly 1", client.calls)
	}
	if !strings.Contains(out, "Payment failed") || !strings.Contains(out, "REASON_QUOTE_NOT_FOUND") {
		t.Errorf("failure not logged, got %q", out)
	}
}

func TestSubmitPayment_UnknownResult(t *testing.T) {
	// A response without a result (a deprecated or unset oneof arm) is unknown,
	// not a crash and not a silent success.
	client := &recordingClient{resp: &payment.CreatePaymentResponse{}}
	out := runCapture(t, client)

	if client.calls != 1 {
		t.Fatalf("CreatePayment called %d times, want exactly 1", client.calls)
	}
	if !strings.Contains(out, "Unknown result type") {
		t.Errorf("unset result not logged as unknown, got %q", out)
	}
}

func TestSubmitPayment_RPCError(t *testing.T) {
	client := &recordingClient{err: connect.NewError(connect.CodeUnavailable, nil)}
	out := runCapture(t, client)

	if client.calls != 1 {
		t.Fatalf("CreatePayment called %d times, want exactly 1 (no retry loop)", client.calls)
	}
	if !strings.Contains(out, "Error submitting payment:") {
		t.Errorf("RPC error not logged, got %q", out)
	}
	if strings.Contains(out, "goroutine") || strings.Contains(out, ".go:") {
		t.Errorf("RPC error logged a stack trace: %q", out)
	}
}
