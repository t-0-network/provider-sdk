package handler

import (
	"context"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
)

type ProviderServiceImplementation struct {
	networkClient paymentconnect.NetworkServiceClient
}

func NewProviderServiceImplementation(networkClient paymentconnect.NetworkServiceClient) *ProviderServiceImplementation {
	return &ProviderServiceImplementation{
		networkClient: networkClient,
	}
}

/*
  Please refer to docs, proto definition comments or source code comments to understand purpose of functions
*/

var _ paymentconnect.ProviderServiceHandler = (*ProviderServiceImplementation)(nil)

// TODO: Step 2.1 implement how you handle updates of payment initiated by you
func (s *ProviderServiceImplementation) UpdatePayment(
	ctx context.Context, req *connect.Request[payment.UpdatePaymentRequest],
) (*connect.Response[payment.UpdatePaymentResponse], error) {
	return connect.NewResponse(&payment.UpdatePaymentResponse{}), nil
}

// TODO: Step 2.4 implement how you do payouts (payments initiated by your counterparts)
func (s *ProviderServiceImplementation) PayOut(ctx context.Context, req *connect.Request[payment.PayoutRequest],
) (*connect.Response[payment.PayoutResponse], error) {

	//TODO: FinalizePayout should be called when your system notifies that payout has been made successfully
	_, err := s.networkClient.FinalizePayout(ctx, connect.NewRequest(&payment.FinalizePayoutRequest{
		PaymentId: req.Msg.PaymentId,
		Result: &payment.FinalizePayoutRequest_Success_{
			Success: &payment.FinalizePayoutRequest_Success{
				Receipt: &common.PaymentReceipt{
					Details: &common.PaymentReceipt_Sepa_{
						Sepa: &common.PaymentReceipt_Sepa{
							BankingTransactionReferenceId: ref("123456"),
						}},
				},
			}},
	}))

	if err != nil {
		return nil, err
	}
	return connect.NewResponse(&payment.PayoutResponse{}), nil
}

func (s *ProviderServiceImplementation) UpdateLimit(
	ctx context.Context, req *connect.Request[payment.UpdateLimitRequest],
) (*connect.Response[payment.UpdateLimitResponse], error) {
	// TODO: optionally implement handling of the notifications about updates on your limits and limits usage
	return connect.NewResponse(&payment.UpdateLimitResponse{}), nil
}

func (s *ProviderServiceImplementation) AppendLedgerEntries(
	ctx context.Context, req *connect.Request[payment.AppendLedgerEntriesRequest],
) (*connect.Response[payment.AppendLedgerEntriesResponse], error) {
	// TODO: optionally implement handling of the notifications about new ledger transactions and new ledger entries
	return connect.NewResponse(&payment.AppendLedgerEntriesResponse{}), nil
}

func (s *ProviderServiceImplementation) ApprovePaymentQuotes(ctx context.Context, c *connect.Request[payment.ApprovePaymentQuoteRequest]) (*connect.Response[payment.ApprovePaymentQuoteResponse], error) {
	//TODO: this is the endpoint to have a last look at quote and approve after AML check is done
	return connect.NewResponse(&payment.ApprovePaymentQuoteResponse{}), nil
}

func ref(s string) *string {
	return &s
}
