package provider

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/sdkversion"
)

// newHealthServer starts a real SDK-built server with a real
// ProviderServiceHandler registered through the public API. The customer's exact
// code shape: they register their service and name nothing else.
func newHealthServer(t *testing.T) (*httptest.Server, *secp256k1.PrivateKey) {
	t.Helper()
	priv, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	mux, err := NewHttpHandler(
		NetworkPublicKeyHexed(crypto.HexPublicKey(priv.PubKey())),
		Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(paymentconnect.UnimplementedProviderServiceHandler{})),
	)
	require.NoError(t, err)

	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	return srv, priv
}

// The no-code-change guarantee: a starter using only the public API gets health
// mounted, behind the same signature verification as its own services.
func TestHealth_ChecksRegisteredServices(t *testing.T) {
	srv, priv := newHealthServer(t)

	client, err := network.NewServiceClient(
		network.PrivateKeyHexed(crypto.HexPrivateKey(priv)),
		grpchealth.NewClient,
		network.WithBaseURL(srv.URL),
	)
	require.NoError(t, err)

	// The customer's own service, health itself, and the whole-process query.
	for _, service := range []string{paymentconnect.ProviderServiceName, grpchealth.HealthV1ServiceName, ""} {
		resp, err := client.Check(context.Background(), &grpchealth.CheckRequest{Service: service})
		require.NoErrorf(t, err, "service %q", service)
		require.Equalf(t, grpchealth.StatusServing, resp.Status, "service %q", service)
	}

	_, err = client.Check(context.Background(), &grpchealth.CheckRequest{Service: "example.v1.NotRegistered"})
	require.Equal(t, connect.CodeNotFound, connect.CodeOf(err))
}

// Response headers are the only place the SDK reports what it is: the health
// contract has a single status field and names its service in the request, so
// the message itself has no room for this.
//
// Asserted over raw HTTP rather than through grpchealth's client, which hides
// response metadata behind its own types — the Network reads these with a plain
// Connect client, which does not.
func TestHealth_ReportsSdkIdentityInResponseHeaders(t *testing.T) {
	srv, priv := newHealthServer(t)

	signFn, err := crypto.NewSignerFromHex(crypto.HexPrivateKey(priv))
	require.NoError(t, err)
	signing := &http.Client{Transport: network.NewSigningTransport(signFn, time.Now)}

	req, err := http.NewRequest(http.MethodPost, srv.URL+"/grpc.health.v1.Health/Check", strings.NewReader("{}"))
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/json")

	resp, err := signing.Do(req)
	require.NoError(t, err)
	defer func() { _ = resp.Body.Close() }()
	require.Equal(t, http.StatusOK, resp.StatusCode)

	require.Equal(t, sdkEcosystem, resp.Header.Get(SDKEcosystemHeader))
	require.Equal(t, sdkversion.Version, resp.Header.Get(SDKVersionHeader))
}

func newHealthServerWithOptions(t *testing.T, opts ...HttpHandlerOption) (*httptest.Server, *secp256k1.PrivateKey) {
	t.Helper()
	priv, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	mux, err := NewHttpHandlerWithOptions(
		NetworkPublicKeyHexed(crypto.HexPublicKey(priv.PubKey())),
		opts,
		Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(paymentconnect.UnimplementedProviderServiceHandler{})),
	)
	require.NoError(t, err)

	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	return srv, priv
}

func TestHealth_ReportsOverriddenSdkVersionInResponseHeaders(t *testing.T) {
	srv, priv := newHealthServerWithOptions(t, WithSDKVersion("9.9.9-test"))

	signFn, err := crypto.NewSignerFromHex(crypto.HexPrivateKey(priv))
	require.NoError(t, err)
	signing := &http.Client{Transport: network.NewSigningTransport(signFn, time.Now)}

	req, err := http.NewRequest(http.MethodPost, srv.URL+"/grpc.health.v1.Health/Check", strings.NewReader("{}"))
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/json")

	resp, err := signing.Do(req)
	require.NoError(t, err)
	defer func() { _ = resp.Body.Close() }()
	require.Equal(t, http.StatusOK, resp.StatusCode)

	require.Equal(t, sdkEcosystem, resp.Header.Get(SDKEcosystemHeader))
	require.Equal(t, "9.9.9-test", resp.Header.Get(SDKVersionHeader))
}

// The probe is signed like every other call the Network makes. Without this the
// transport would be publishing an unauthenticated endpoint on a partner's port.
func TestHealth_RejectsUnsignedRequest(t *testing.T) {
	srv, _ := newHealthServer(t)

	plain := grpchealth.NewClient(http.DefaultClient, srv.URL)
	_, err := plain.Check(context.Background(), &grpchealth.CheckRequest{})
	require.Error(t, err)
	require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}
