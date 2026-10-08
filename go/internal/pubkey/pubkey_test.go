package pubkey

import (
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"

	"github.com/stretchr/testify/require"
)

type publicKeyParsingVectors struct {
	PublicKeyParsing []struct {
		Name         string `json:"name"`
		Input        string `json:"input"`
		Valid        bool   `json:"valid"`
		Uncompressed string `json:"uncompressed"`
		Error        string `json:"error"`
	} `json:"public_key_parsing"`
}

// The parser behind the configured network key and the X-Public-Key header
// accepts and rejects the same inputs in every SDK.
func TestCrossVectors_PublicKeyParsing(t *testing.T) {
	data, err := os.ReadFile("../../../cross_test/test_vectors.json")
	require.NoError(t, err, "failed to read test vectors")
	var v publicKeyParsingVectors
	require.NoError(t, json.Unmarshal(data, &v))
	require.Len(t, v.PublicKeyParsing, 20)

	for _, tc := range v.PublicKeyParsing {
		t.Run(tc.Name, func(t *testing.T) {
			publicKey, err := ParseHex(tc.Input)
			if !tc.Valid {
				require.EqualError(t, err, tc.Error)
				return
			}
			require.NoError(t, err)
			require.Equal(t, tc.Uncompressed, hex.EncodeToString(publicKey.SerializeUncompressed()))
		})
	}
}
