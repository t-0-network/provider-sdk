package provider

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
)

func TestValidationInterceptor(t *testing.T) {
	t.Run("interceptor is created successfully", func(t *testing.T) {
		interceptor := newValidationInterceptor()
		require.NotNil(t, interceptor)
	})

	t.Run("rejects invalid response with CodeInternal", func(t *testing.T) {
		interceptor := newValidationInterceptor()
		procedure := "/test.v1.TestService/GetDecimal"
		handler := connect.NewUnaryHandler(
			procedure,
			func(_ context.Context, _ *connect.Request[common.Decimal]) (*connect.Response[common.Decimal], error) {
				return connect.NewResponse(&common.Decimal{Exponent: 100}), nil // invalid
			},
			connect.WithInterceptors(interceptor),
		)
		mux := http.NewServeMux()
		mux.Handle(procedure, handler)
		srv := httptest.NewServer(mux)
		defer srv.Close()

		client := connect.NewClient[common.Decimal, common.Decimal](srv.Client(), srv.URL+procedure)
		_, err := client.CallUnary(context.Background(), connect.NewRequest(&common.Decimal{Exponent: 2}))
		require.Error(t, err)
		require.Equal(t, connect.CodeInternal, connect.CodeOf(err))
	})

	t.Run("passes valid response through", func(t *testing.T) {
		interceptor := newValidationInterceptor()
		procedure := "/test.v1.TestService/GetDecimal"
		handler := connect.NewUnaryHandler(
			procedure,
			func(_ context.Context, _ *connect.Request[common.Decimal]) (*connect.Response[common.Decimal], error) {
				return connect.NewResponse(&common.Decimal{Exponent: 2}), nil // valid
			},
			connect.WithInterceptors(interceptor),
		)
		mux := http.NewServeMux()
		mux.Handle(procedure, handler)
		srv := httptest.NewServer(mux)
		defer srv.Close()

		client := connect.NewClient[common.Decimal, common.Decimal](srv.Client(), srv.URL+procedure)
		resp, err := client.CallUnary(context.Background(), connect.NewRequest(&common.Decimal{Exponent: 2}))
		require.NoError(t, err)
		require.Equal(t, int32(2), resp.Msg.Exponent)
	})

	t.Run("rejects invalid request with CodeInvalidArgument", func(t *testing.T) {
		interceptor := newValidationInterceptor()
		procedure := "/test.v1.TestService/GetDecimal"
		called := false
		handler := connect.NewUnaryHandler(
			procedure,
			func(_ context.Context, _ *connect.Request[common.Decimal]) (*connect.Response[common.Decimal], error) {
				called = true
				return connect.NewResponse(&common.Decimal{Exponent: 2}), nil
			},
			connect.WithInterceptors(interceptor),
		)
		mux := http.NewServeMux()
		mux.Handle(procedure, handler)
		srv := httptest.NewServer(mux)
		defer srv.Close()

		client := connect.NewClient[common.Decimal, common.Decimal](srv.Client(), srv.URL+procedure)
		_, err := client.CallUnary(context.Background(), connect.NewRequest(&common.Decimal{Exponent: 100})) // invalid request
		require.Error(t, err)
		require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
		require.False(t, called, "handler should not be called for invalid request")
	})
}

func TestProtovalidateResponses(t *testing.T) {
	t.Run("valid Decimal response passes", func(t *testing.T) {
		msg := &common.Decimal{Unscaled: 12345, Exponent: 2}
		err := protovalidate.Validate(msg)
		require.NoError(t, err)
	})

	t.Run("invalid Decimal response fails - exponent too high", func(t *testing.T) {
		msg := &common.Decimal{Exponent: 100}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
	})

	t.Run("invalid Decimal response fails - exponent too low", func(t *testing.T) {
		msg := &common.Decimal{Exponent: -20}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
	})

	t.Run("boundary Decimal values pass", func(t *testing.T) {
		require.NoError(t, protovalidate.Validate(&common.Decimal{Exponent: -8}))
		require.NoError(t, protovalidate.Validate(&common.Decimal{Exponent: 8}))
		require.NoError(t, protovalidate.Validate(&common.Decimal{Exponent: 0}))
	})

	t.Run("boundary Decimal values fail", func(t *testing.T) {
		require.Error(t, protovalidate.Validate(&common.Decimal{Exponent: -9}))
		require.Error(t, protovalidate.Validate(&common.Decimal{Exponent: 9}))
	})

	t.Run("valid PayoutResponse passes", func(t *testing.T) {
		msg := &payment.PayoutResponse{
			Result: &payment.PayoutResponse_Accepted_{
				Accepted: &payment.PayoutResponse_Accepted{},
			},
		}
		err := protovalidate.Validate(msg)
		require.NoError(t, err)
	})

	t.Run("empty response passes when no constraints", func(t *testing.T) {
		msg := &payment.UpdatePaymentResponse{}
		err := protovalidate.Validate(msg)
		require.NoError(t, err)
	})
}

func TestProtovalidateRequests(t *testing.T) {
	t.Run("valid AppendLedgerEntriesRequest passes", func(t *testing.T) {
		msg := &payment.AppendLedgerEntriesRequest{
			Transactions: []*payment.AppendLedgerEntriesRequest_Transaction{
				{
					TransactionId: 1,
					Entries: []*payment.AppendLedgerEntriesRequest_LedgerEntry{
						{},
					},
					TransactionDetails: &payment.AppendLedgerEntriesRequest_Transaction_Payout_{
						Payout: &payment.AppendLedgerEntriesRequest_Transaction_Payout{
							PaymentId: 1,
						},
					},
				},
			},
		}
		err := protovalidate.Validate(msg)
		require.NoError(t, err)
	})

	t.Run("invalid AppendLedgerEntriesRequest fails - empty transactions", func(t *testing.T) {
		msg := &payment.AppendLedgerEntriesRequest{}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
	})

	t.Run("invalid request fails - transaction_id is zero", func(t *testing.T) {
		msg := &payment.AppendLedgerEntriesRequest{
			Transactions: []*payment.AppendLedgerEntriesRequest_Transaction{
				{
					TransactionId: 0, // must be > 0
					Entries: []*payment.AppendLedgerEntriesRequest_LedgerEntry{
						{},
					},
					TransactionDetails: &payment.AppendLedgerEntriesRequest_Transaction_Payout_{
						Payout: &payment.AppendLedgerEntriesRequest_Transaction_Payout{
							PaymentId: 1,
						},
					},
				},
			},
		}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
	})
}
