package network

import (
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
)

// A method's timeout option replaces the client's timeout, shorter or longer; a call without a
// method descriptor gets the client's timeout.
func TestTimeoutOf(t *testing.T) {
	const fallback = 45 * time.Second
	approve := payment.File_tzero_v1_payment_provider_proto.Services().ByName("ProviderService").Methods().ByName("ApprovePaymentQuotes")
	updateQuote := payment.File_tzero_v1_payment_network_proto.Services().ByName("NetworkService").Methods().ByName("UpdateQuote")

	require.Equal(t, 29*time.Second, timeoutOf(connect.Spec{Schema: approve}, fallback))
	require.Equal(t, 30*time.Second, timeoutOf(connect.Spec{Schema: updateQuote}, fallback))
	require.Equal(t, fallback, timeoutOf(connect.Spec{}, fallback))
}
