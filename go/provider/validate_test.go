package provider

import (
	"errors"
	"testing"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"github.com/stretchr/testify/require"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
)

func TestValidate(t *testing.T) {
	t.Run("valid message round-trips unchanged", func(t *testing.T) {
		in := &common.Decimal{Unscaled: 12345, Exponent: 2}
		out, err := Validate(in)
		require.NoError(t, err)
		require.Same(t, in, out, "Validate should return the same pointer on success")
	})

	t.Run("valid empty PayoutResponse round-trips", func(t *testing.T) {
		in := &payment.PayoutResponse{
			Result: &payment.PayoutResponse_Accepted_{
				Accepted: &payment.PayoutResponse_Accepted{},
			},
		}
		out, err := Validate(in)
		require.NoError(t, err)
		require.Same(t, in, out)
	})

	t.Run("invalid message returns zero value and prefixed error", func(t *testing.T) {
		in := &common.Decimal{Exponent: 100} // out-of-range; matches validate_response_test
		out, err := Validate(in)
		require.Error(t, err)
		require.Nil(t, out, "expected zero value on failure")
		// Error message carries the documented "response validation failed: "
		// prefix on the underlying error; *connect.Error.Error() prepends the
		// code name ("internal: "), so we match on substring rather than prefix.
		require.Contains(t, err.Error(), "response validation failed: ",
			"error message must carry the documented prefix for parity with the interceptor safety net; got %q", err.Error(),
		)
		// Wire-contract: when a handler propagates this error via
		// `return nil, err`, connect-go must produce CodeInternal — not
		// CodeUnknown — so the wire response matches what the interceptor
		// safety net would produce on the same failure.
		require.Equal(t, connect.CodeInternal, connect.CodeOf(err),
			"Validate must return a *connect.Error with CodeInternal so propagation preserves wire shape")
	})

	t.Run("error wraps protovalidate.ValidationError so callers can inspect violations", func(t *testing.T) {
		_, err := Validate(&common.Decimal{Exponent: 100})
		require.Error(t, err)
		var ve *protovalidate.ValidationError
		require.True(t, errors.As(err, &ve), "expected ValidationError to be wrapped")
		require.NotEmpty(t, ve.Violations)
	})
}
