package crypto_test

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

func Test_PrivateKeyHelpers(t *testing.T) {
	// Generated using Ethereum crypto package
	privateKeyHex := "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"

	pk, err := crypto.GetPrivateKeyFromHex(privateKeyHex)
	require.NoError(t, err, "failed to get private key from hex")

	hexedPK := crypto.HexPrivateKey(pk)
	require.NoError(t, err, "failed to hex private key")
	require.Equal(t, privateKeyHex, hexedPK)
}

func Test_PublicKeyHelpers(t *testing.T) {
	// Generated using Ethereum crypto package
	publicKeyHex := "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"

	pk, err := crypto.GetPublicKeyFromHex(publicKeyHex)
	require.NoError(t, err, "failed to get public key from hex")

	hexedPK := crypto.HexPublicKey(pk)
	require.NoError(t, err, "failed to hex public key")
	require.Equal(t, publicKeyHex, hexedPK)

	pkBytes := crypto.GetPublicKeyBytes(pk)
	pkFromBytes, err := crypto.GetPublicKeyFromBytes(pkBytes)
	require.NoError(t, err, "failed to get public key from bytes")

	require.True(t, pk.IsEqual(pkFromBytes))
}

func TestGetPrivateKeyFromHex_ChecksTheKey(t *testing.T) {
	const key = "691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
	for _, valid := range []string{"0x" + key, "0X" + key, key} {
		pk, err := crypto.GetPrivateKeyFromHex(valid)
		require.NoError(t, err, valid)
		require.Equal(t, "0x"+key, crypto.HexPrivateKey(pk), valid)
	}

	const (
		empty    = "private key must not be null or empty"
		size     = "private key must be 32 bytes (64 hex characters)"
		outRange = "private key must be in range [1, n-1]"
	)
	for _, row := range []struct{ input, want string }{
		{"", empty},
		{key[:62], size},
		{key + "00", size},
		{key[:62] + "  ", size},
		{"zz" + key[:62], size},
		{"0x0x" + key, size},
		{strings.Repeat("0", 64), outRange},
		{"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", outRange},
	} {
		_, err := crypto.GetPrivateKeyFromHex(row.input)
		require.EqualError(t, err, row.want, "input %q", row.input)

		_, err = crypto.NewSignerFromHex(row.input)
		require.EqualError(t, err, row.want, "input %q", row.input)
	}
}
