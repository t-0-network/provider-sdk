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
		Body              string `json:"body"`
		TimestampMs       uint64 `json:"timestamp_ms"`
		ExpectedHash      string `json:"expected_hash"`
		ExpectedSignature string `json:"expected_signature"`
	} `json:"request_signing"`
	RequestSigningCases []struct {
		Name              string `json:"name"`
		BodyHex           string `json:"body_hex"`
		TimestampMs       uint64 `json:"timestamp_ms"`
		ExpectedHash      string `json:"expected_hash"`
		ExpectedSignature string `json:"expected_signature"`
	} `json:"request_signing_cases"`
	SignatureVerification []struct {
		Name        string `json:"name"`
		BodyHex     string `json:"body_hex"`
		TimestampMs uint64 `json:"timestamp_ms"`
		PublicKey   string `json:"public_key"`
		Signature   string `json:"signature"`
		Valid       bool   `json:"valid"`
	} `json:"signature_verification"`
}

// requestDigest is what a provider hashes: the raw body with the little-endian
// millisecond timestamp appended.
func requestDigest(t *testing.T, bodyHex string, timestampMs uint64) []byte {
	t.Helper()
	body, err := hex.DecodeString(bodyHex)
	require.NoError(t, err)

	tsBytes := make([]byte, 8)
	binary.LittleEndian.PutUint64(tsBytes, timestampMs)

	return crypto.LegacyKeccak256(append(body, tsBytes...))
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

func TestCrossVectors_RequestSignature(t *testing.T) {
	v := loadVectors(t)
	sign, err := crypto.NewSignerFromHex(v.Keys.PrivateKey)
	require.NoError(t, err)

	body := []byte(v.RequestSigning.Body)
	tsBytes := make([]byte, 8)
	binary.LittleEndian.PutUint64(tsBytes, v.RequestSigning.TimestampMs)

	combined := append(body, tsBytes...)
	digest := crypto.LegacyKeccak256(combined)
	require.Equal(t, v.RequestSigning.ExpectedHash, hex.EncodeToString(digest))

	signature, _, err := sign(digest)
	require.NoError(t, err)
	// Compare first 64 bytes (r+s) against the cross-language test vector
	require.Equal(t, v.RequestSigning.ExpectedSignature, hex.EncodeToString(signature[:64]))
}

// Bodies that are not text: binary, gRPC-framed, empty, and one whose signature has a
// leading zero byte. RFC 6979 makes the signature a function of key and digest alone, so
// these are exact bytes every SDK has to reproduce, not round-trips.
func TestCrossVectors_RequestSigningCases(t *testing.T) {
	v := loadVectors(t)
	require.NotEmpty(t, v.RequestSigningCases)

	sign, err := crypto.NewSignerFromHex(v.Keys.PrivateKey)
	require.NoError(t, err)

	for _, tc := range v.RequestSigningCases {
		t.Run(tc.Name, func(t *testing.T) {
			digest := requestDigest(t, tc.BodyHex, tc.TimestampMs)
			require.Equal(t, tc.ExpectedHash, hex.EncodeToString(digest))

			signature, _, err := sign(digest)
			require.NoError(t, err)
			require.Equal(t, tc.ExpectedSignature, hex.EncodeToString(signature[:64]))
		})
	}
}

// The presented-request cases, including the ones a provider has to refuse.
func TestCrossVectors_SignatureVerification(t *testing.T) {
	v := loadVectors(t)
	require.NotEmpty(t, v.SignatureVerification)

	for _, tc := range v.SignatureVerification {
		t.Run(tc.Name, func(t *testing.T) {
			pubKeyBytes, err := hex.DecodeString(tc.PublicKey)
			require.NoError(t, err)
			pubKey, err := crypto.GetPublicKeyFromBytes(pubKeyBytes)
			require.NoError(t, err)

			signature, err := hex.DecodeString(tc.Signature)
			require.NoError(t, err)

			digest := requestDigest(t, tc.BodyHex, tc.TimestampMs)
			require.Equal(t, tc.Valid, crypto.VerifySignature(pubKey, digest, signature))
		})
	}
}
