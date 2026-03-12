package crypto_test

import (
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

type testVectors struct {
	Keys struct {
		PrivateKey string `json:"private_key"`
		PublicKey  string `json:"public_key"`
	} `json:"keys"`
	Keccak256 []struct {
		Input string `json:"input"`
		Hash  string `json:"hash"`
	} `json:"keccak256"`
	RequestSigning struct {
		Body         string `json:"body"`
		TimestampMs  uint64 `json:"timestamp_ms"`
		ExpectedHash string `json:"expected_hash"`
	} `json:"request_signing"`
}

func loadVectors(t *testing.T) testVectors {
	t.Helper()
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err, "failed to read test vectors")
	var v testVectors
	require.NoError(t, json.Unmarshal(data, &v))
	return v
}

func TestCrossVectors_Keccak256(t *testing.T) {
	v := loadVectors(t)
	for _, tc := range v.Keccak256 {
		t.Run(tc.Input, func(t *testing.T) {
			hash := crypto.LegacyKeccak256([]byte(tc.Input))
			require.Equal(t, tc.Hash, hex.EncodeToString(hash))
		})
	}
}

func TestCrossVectors_KeyDerivation(t *testing.T) {
	v := loadVectors(t)
	sign, err := crypto.NewSignerFromHex(v.Keys.PrivateKey)
	require.NoError(t, err)

	digest := crypto.LegacyKeccak256([]byte("test"))
	_, pubKeyBytes, err := sign(digest)
	require.NoError(t, err)
	require.Equal(t, v.Keys.PublicKey, hex.EncodeToString(pubKeyBytes))
}

func TestCrossVectors_RequestHash(t *testing.T) {
	v := loadVectors(t)

	body := []byte(v.RequestSigning.Body)
	tsBytes := make([]byte, 8)
	binary.LittleEndian.PutUint64(tsBytes, v.RequestSigning.TimestampMs)

	combined := append(body, tsBytes...)
	hash := crypto.LegacyKeccak256(combined)
	require.Equal(t, v.RequestSigning.ExpectedHash, hex.EncodeToString(hash))
}

func TestCrossVectors_SignVerifyRoundTrip(t *testing.T) {
	v := loadVectors(t)
	sign, err := crypto.NewSignerFromHex(v.Keys.PrivateKey)
	require.NoError(t, err)

	digest := crypto.LegacyKeccak256([]byte("round trip test"))
	signature, pubKeyBytes, err := sign(digest)
	require.NoError(t, err)

	pubKey, err := crypto.GetPublicKeyFromBytes(pubKeyBytes)
	require.NoError(t, err)
	require.True(t, crypto.VerifySignature(pubKey, digest, signature))
}
