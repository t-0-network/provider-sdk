package provider

import (
	"encoding/binary"
	"encoding/json"
	"net/http"
	"os"
	"strconv"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/common"
)

type timestampParsingVectors struct {
	TimestampParsing []struct {
		Name  string `json:"name"`
		Input string `json:"input"`
		Valid bool   `json:"valid"`
		Value string `json:"value"`
	} `json:"timestamp_parsing"`
}

// X-Signature-Timestamp is parsed by the same rule in every SDK. The header map
// is built by hand: net/http strips the spaces around a header value before the
// SDK sees it, http.Header does not.
func TestCrossVectors_TimestampParsing(t *testing.T) {
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err, "failed to read test vectors")
	var v timestampParsingVectors
	require.NoError(t, json.Unmarshal(data, &v))
	require.Len(t, v.TimestampParsing, 14)

	for _, tc := range v.TimestampParsing {
		t.Run(tc.Name, func(t *testing.T) {
			headers := http.Header{common.SignatureTimestampHeader: {tc.Input}}
			timestamp, timestampBytes, err := parseTimestamp(headers)
			if !tc.Valid {
				require.Error(t, err)
				if tc.Input == "" {
					require.ErrorIs(t, err, ErrMissingRequiredHeader)
				}
				return
			}
			require.NoError(t, err)
			want, err := strconv.ParseInt(tc.Value, 10, 64)
			require.NoError(t, err)
			require.Equal(t, want, timestamp.UnixMilli())
			require.Equal(t, uint64(want), binary.LittleEndian.Uint64(timestampBytes[:]))
		})
	}
}
