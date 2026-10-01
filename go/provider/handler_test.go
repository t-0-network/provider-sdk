package provider

import (
	"errors"
	"strings"
	"testing"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"

	"github.com/t-0-network/provider-sdk/go/crypto"
)

func TestWithVerifySignatureFn_NilPanics(t *testing.T) {
	require.PanicsWithValue(t, "provider: WithVerifySignatureFn: nil verifier", func() {
		WithVerifySignatureFn(nil)
	})
}

func TestNewHttpHandler_NetworkPublicKey(t *testing.T) {
	priv, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	validKey := crypto.HexPublicKey(priv.PubKey())

	tests := []struct {
		name     string
		key      string
		required bool // expect ErrNetworkPublicKeyIsRequired
		invalid  bool // expect an "invalid network public key" error
	}{
		{name: "empty", key: "", required: true},
		{name: "whitespace only", key: "  \n", required: true},
		{name: "non-hex", key: "0xnot-a-key", invalid: true},
		{name: "off-curve", key: "0x04" + strings.Repeat("0", 128), invalid: true},
		{name: "valid", key: validKey},
		{name: "valid with surrounding whitespace", key: "  " + validKey + "\n"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mux, err := NewHttpHandler(NetworkPublicKeyHexed(tt.key))
			switch {
			case tt.required:
				require.ErrorIs(t, err, ErrNetworkPublicKeyIsRequired)
				require.Nil(t, mux)
			case tt.invalid:
				require.Error(t, err)
				require.False(t, errors.Is(err, ErrNetworkPublicKeyIsRequired))
				require.Contains(t, err.Error(), "invalid network public key")
				require.Nil(t, mux)
			default:
				require.NoError(t, err)
				require.NotNil(t, mux)
			}
		})
	}
}
