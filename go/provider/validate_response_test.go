package provider

import (
	"bytes"
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/sdkversion"
)

func TestValidationInterceptor(t *testing.T) {
	t.Run("interceptor is created successfully", func(t *testing.T) {
		interceptor := newValidationInterceptor(nil)
		require.NotNil(t, interceptor)
	})

	t.Run("rejects invalid response with CodeInternal", func(t *testing.T) {
		interceptor := newValidationInterceptor(nil)
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
		interceptor := newValidationInterceptor(nil)
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
		interceptor := newValidationInterceptor(nil)
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

	t.Run("custom logger receives one structured error line on response-validation failure", func(t *testing.T) {
		// Capture slog output to a buffer via a TextHandler so we can match
		// the structured fields by substring without coupling to a specific
		// JSON encoder shape.
		var buf bytes.Buffer
		logger := slog.New(slog.NewTextHandler(&buf, &slog.HandlerOptions{Level: slog.LevelError}))

		interceptor := newValidationInterceptor(logger)
		procedure := "/test.v1.TestService/GetDecimal"
		handler := connect.NewUnaryHandler(
			procedure,
			func(_ context.Context, _ *connect.Request[common.Decimal]) (*connect.Response[common.Decimal], error) {
				return connect.NewResponse(&common.Decimal{Exponent: 100}), nil // invalid response
			},
			connect.WithInterceptors(interceptor),
		)
		mux := http.NewServeMux()
		mux.Handle(procedure, handler)
		srv := httptest.NewServer(mux)
		defer srv.Close()

		client := connect.NewClient[common.Decimal, common.Decimal](srv.Client(), srv.URL+procedure)
		_, err := client.CallUnary(context.Background(), connect.NewRequest(&common.Decimal{Exponent: 2}))

		// Wire behaviour is unchanged.
		require.Error(t, err)
		require.Equal(t, connect.CodeInternal, connect.CodeOf(err))

		// Exactly one log line, with the documented fields.
		out := buf.String()
		require.Equal(t, 1, strings.Count(out, "\n"), "expected exactly one slog record, got %q", out)
		require.Contains(t, out, `level=ERROR`)
		require.Contains(t, out, `msg="response validation failed"`)
		require.Contains(t, out, `rpc_method=`+procedure)
		require.Contains(t, out, `response_type=tzero.v1.common.Decimal`)
		require.Contains(t, out, "violations=")
		require.Contains(t, out, `sdk_version=`+sdkversion.Version)
	})

	t.Run("WithLogger option threads through NewHttpHandlerWithOptions to newDefaultHandlerOptions", func(t *testing.T) {
		// Verify the wiring: WithLogger mutates providerHandlerOptions.logger,
		// and that logger flows into newDefaultHandlerOptions so the
		// validation interceptor is constructed with it. The full
		// request-path behaviour is covered by the sibling sub-test that
		// builds the interceptor directly with a custom logger.
		var buf bytes.Buffer
		logger := slog.New(slog.NewTextHandler(&buf, &slog.HandlerOptions{Level: slog.LevelError}))

		scratch := providerHandlerOptions{}
		WithLogger(logger)(&scratch)
		require.Same(t, logger, scratch.logger, "WithLogger should set providerHandlerOptions.logger")

		opts, err := newDefaultHandlerOptions(nil, scratch.logger)
		require.NoError(t, err)
		require.Same(t, logger, opts.logger, "newDefaultHandlerOptions should preserve the caller's logger")

		// nil from the caller falls back to slog.Default(), preserving the
		// "logger is always non-nil downstream" invariant.
		fallback, err := newDefaultHandlerOptions(nil, nil)
		require.NoError(t, err)
		require.NotNil(t, fallback.logger, "newDefaultHandlerOptions should default to slog.Default() when nil")
	})

	t.Run("handler that propagates Validate error returns CodeInternal on the wire", func(t *testing.T) {
		// Handler-propagation regression: a developer who calls
		// provider.Validate(invalidResp) and does `return nil, err` must see
		// connect.CodeInternal on the wire (not CodeUnknown). This is the
		// wire-contract that Validate's *connect.Error wrapping protects.
		// The validation interceptor is included so the path matches the
		// production handler stack.
		interceptor := newValidationInterceptor(nil)
		procedure := "/test.v1.TestService/GetDecimal"
		handler := connect.NewUnaryHandler(
			procedure,
			func(_ context.Context, _ *connect.Request[common.Decimal]) (*connect.Response[common.Decimal], error) {
				resp, err := Validate(&common.Decimal{Exponent: 100}) // invalid
				if err != nil {
					return nil, err
				}
				return connect.NewResponse(resp), nil
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
		require.Equal(t, connect.CodeInternal, connect.CodeOf(err),
			"handler-propagated Validate error must surface as CodeInternal, not CodeUnknown")
		require.Contains(t, err.Error(), "response validation failed",
			"wire error message must carry the documented prefix")
	})

	t.Run("NewHttpHandler continues to accept the old signature", func(t *testing.T) {
		// Backward-compat smoke test: the original NewHttpHandler signature
		// (no options) must keep working unchanged.
		mux, err := NewHttpHandler("")
		require.NoError(t, err)
		require.NotNil(t, mux)
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

	t.Run("multiple violations surface a non-empty ValidationError", func(t *testing.T) {
		msg := &payment.AppendLedgerEntriesRequest{
			Transactions: []*payment.AppendLedgerEntriesRequest_Transaction{
				{
					TransactionId:      0, // must be > 0
					Entries:            nil,
					TransactionDetails: nil,
				},
			},
		}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
		var ve *protovalidate.ValidationError
		require.ErrorAs(t, err, &ve)
		require.NotEmpty(t, ve.Violations, "expected at least one violation in ValidationError")
	})

	t.Run("nested oneof: invalid Decimal inside a valid request still fails", func(t *testing.T) {
		msg := &payment.AppendLedgerEntriesRequest{
			Transactions: []*payment.AppendLedgerEntriesRequest_Transaction{
				{
					TransactionId: 1,
					Entries: []*payment.AppendLedgerEntriesRequest_LedgerEntry{
						{Debit: &common.Decimal{Exponent: 999}}, // out-of-range exponent on nested Decimal
					},
					TransactionDetails: &payment.AppendLedgerEntriesRequest_Transaction_Payout_{
						Payout: &payment.AppendLedgerEntriesRequest_Transaction_Payout{PaymentId: 1},
					},
				},
			},
		}
		err := protovalidate.Validate(msg)
		require.Error(t, err)
	})
}
