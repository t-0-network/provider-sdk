package provider

import (
	"context"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/system"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/system/systemconnect"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/sdkversion"
)

// TestSystem_AutoRegisteredEndToEnd starts a real SDK-built server with a
// real paymentconnect.ProviderServiceHandler registered through the public
// API (provider.Handler + provider.NewHttpHandler), then issues a signed
// SystemService.Health() call through network.NewServiceClient. This proves
// the no-code-change guarantee: a starter using only the public API gets
// SystemService auto-registered, behind the same signature verification.
func TestSystem_AutoRegisteredEndToEnd(t *testing.T) {
	priv, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	privateKeyHex := crypto.HexPrivateKey(priv)
	publicKeyHex := crypto.HexPublicKey(priv.PubKey())

	// Customer's exact code shape: register their service via the SDK's
	// public API. They never name SystemService.
	mux, err := NewHttpHandler(
		NetworkPublicKeyHexed(publicKeyHex),
		Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(paymentconnect.UnimplementedProviderServiceHandler{})),
	)
	require.NoError(t, err)

	srv := httptest.NewServer(mux)
	defer srv.Close()

	systemClient, err := network.NewServiceClient(
		network.PrivateKeyHexed(privateKeyHex),
		systemconnect.NewSystemServiceClient,
		network.WithBaseURL(srv.URL),
	)
	require.NoError(t, err)

	resp, err := systemClient.Health(context.Background(), connect.NewRequest(&system.HealthRequest{}))
	require.NoError(t, err)

	got := resp.Msg
	require.Truef(t, slices.Contains(got.Services, paymentconnect.ProviderServiceName),
		"services missing %q: %v", paymentconnect.ProviderServiceName, got.Services)
	require.Truef(t, slices.Contains(got.Services, systemconnect.SystemServiceName),
		"services missing %q: %v", systemconnect.SystemServiceName, got.Services)
	require.Equal(t, sdkversion.Version, got.SdkVersion)
	require.Equal(t, system.SdkEcosystem_SDK_ECOSYSTEM_GO, got.SdkEcosystem)
	require.NotNil(t, got.CurrentTime)
	require.Less(t, time.Since(got.CurrentTime.AsTime()).Abs(), 5*time.Second)
}

// TestSystem_RejectsUnsignedRequest verifies that the auto-registered
// SystemService inherits the signature middleware: an unsigned request is
// rejected, proving Health is not accidentally exposed unauthenticated.
func TestSystem_RejectsUnsignedRequest(t *testing.T) {
	priv, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	publicKeyHex := crypto.HexPublicKey(priv.PubKey())

	mux, err := NewHttpHandler(NetworkPublicKeyHexed(publicKeyHex))
	require.NoError(t, err)

	srv := httptest.NewServer(mux)
	defer srv.Close()

	plain := systemconnect.NewSystemServiceClient(http.DefaultClient, srv.URL)
	_, err = plain.Health(context.Background(), connect.NewRequest(&system.HealthRequest{}))
	require.Error(t, err)
	require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

// TestSystem_ImplResponseShape directly tests the impl in isolation.
func TestSystem_ImplResponseShape(t *testing.T) {
	services := []string{"example.v1.Foo", systemconnect.SystemServiceName}
	impl := newSystemServiceImpl(services)

	resp, err := impl.Health(context.Background(), connect.NewRequest(&system.HealthRequest{}))
	require.NoError(t, err)

	require.Equal(t, services, resp.Msg.Services)
	require.Equal(t, sdkversion.Version, resp.Msg.SdkVersion)
	require.Equal(t, system.SdkEcosystem_SDK_ECOSYSTEM_GO, resp.Msg.SdkEcosystem)
	require.NotNil(t, resp.Msg.CurrentTime)
}
