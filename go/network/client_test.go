package network

import (
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

type roundTripperFunc func(*http.Request) (*http.Response, error)

func (f roundTripperFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

type stubClient struct{}

func testSignFn(t *testing.T) crypto.SignFn {
	t.Helper()
	pk, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	return crypto.NewSigner(pk)
}

func capturingFactory() (ClientFactory[stubClient], func() connect.HTTPClient) {
	var captured connect.HTTPClient
	factory := func(httpClient connect.HTTPClient, _ string, _ ...connect.ClientOption) stubClient {
		captured = httpClient
		return stubClient{}
	}
	return factory, func() connect.HTTPClient { return captured }
}

func TestNewServiceClient_SignsRequests(t *testing.T) {
	var captured *http.Request
	recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		captured = r.Clone(r.Context())
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader("")),
		}, nil
	})

	factory, getHTTPClient := capturingFactory()
	_, err := NewServiceClient("", factory,
		WithSignatureFunction(testSignFn(t)),
		WithBaseURL("http://localhost"),
		withHTTPTransport(recorder),
	)
	require.NoError(t, err)

	req, err := http.NewRequest("POST", "http://localhost/test", strings.NewReader("hello"))
	require.NoError(t, err)
	resp, err := getHTTPClient().Do(req)
	require.NoError(t, err)
	resp.Body.Close()

	require.NotNil(t, captured, "custom transport must be invoked")

	body, err := io.ReadAll(captured.Body)
	require.NoError(t, err)
	require.Equal(t, "hello", string(body))

	tsMs, err := strconv.ParseInt(captured.Header.Get(common.SignatureTimestampHeader), 10, 64)
	require.NoError(t, err)
	var tsBytes [8]byte
	binary.LittleEndian.PutUint64(tsBytes[:], uint64(tsMs))
	digest := crypto.LegacyKeccak256(append(body, tsBytes[:]...))

	pubKeyHex := strings.TrimPrefix(captured.Header.Get(common.PublicKeyHeader), "0x")
	pubKeyBytes, err := hex.DecodeString(pubKeyHex)
	require.NoError(t, err)
	pubKey, err := secp256k1.ParsePubKey(pubKeyBytes)
	require.NoError(t, err)

	sigHex := strings.TrimPrefix(captured.Header.Get(common.SignatureHeader), "0x")
	sig, err := hex.DecodeString(sigHex)
	require.NoError(t, err)

	require.True(t, crypto.VerifySignature(pubKey, digest, sig), "signature must verify")
}

func TestNewServiceClient_DefaultTransport(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer ts.Close()

	factory, getHTTPClient := capturingFactory()
	_, err := NewServiceClient("", factory,
		WithSignatureFunction(testSignFn(t)),
		WithBaseURL(ts.URL),
	)
	require.NoError(t, err)

	req, err := http.NewRequest("POST", ts.URL+"/test", strings.NewReader("hello"))
	require.NoError(t, err)
	resp, err := getHTTPClient().Do(req)
	require.NoError(t, err)
	resp.Body.Close()
	require.Equal(t, http.StatusOK, resp.StatusCode)
}

func TestNewServiceClient_NilTransportIgnored(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer ts.Close()

	factory, getHTTPClient := capturingFactory()
	_, err := NewServiceClient("", factory,
		WithSignatureFunction(testSignFn(t)),
		WithBaseURL(ts.URL),
		withHTTPTransport(nil),
	)
	require.NoError(t, err)

	req, err := http.NewRequest("POST", ts.URL+"/test", strings.NewReader("hello"))
	require.NoError(t, err)
	resp, err := getHTTPClient().Do(req)
	require.NoError(t, err)
	resp.Body.Close()
	require.Equal(t, http.StatusOK, resp.StatusCode)
}

func TestSigningTransport_NoBody(t *testing.T) {
	var captured *http.Request
	recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		captured = r
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader("")),
		}, nil
	})

	st := NewSigningTransport(testSignFn(t), func() time.Time { return time.Now() }, WithTransport(recorder))

	req, err := http.NewRequest("POST", "http://localhost/health", http.NoBody)
	require.NoError(t, err)
	resp, err := st.RoundTrip(req)
	require.NoError(t, err)
	resp.Body.Close()

	require.NotNil(t, captured)
	require.NotEmpty(t, captured.Header.Get(common.SignatureHeader))
	require.Equal(t, http.NoBody, captured.Body, "http.NoBody sentinel must be preserved to avoid chunked encoding")
}

func TestSigningTransport_NilAndNoBodyProduceSameSignature(t *testing.T) {
	fixedTime := time.Date(2025, 1, 1, 0, 0, 0, 0, time.UTC)
	signFn := testSignFn(t)

	var signatures []string
	recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		signatures = append(signatures, r.Header.Get(common.SignatureHeader))
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader("")),
		}, nil
	})

	st := NewSigningTransport(signFn, func() time.Time { return fixedTime }, WithTransport(recorder))

	reqNil, err := http.NewRequest("POST", "http://localhost/test", nil)
	require.NoError(t, err)
	resp, err := st.RoundTrip(reqNil)
	require.NoError(t, err)
	resp.Body.Close()

	reqNoBody, err := http.NewRequest("POST", "http://localhost/test", http.NoBody)
	require.NoError(t, err)
	resp, err = st.RoundTrip(reqNoBody)
	require.NoError(t, err)
	resp.Body.Close()

	require.Len(t, signatures, 2)
	require.NotEmpty(t, signatures[0])
	require.Equal(t, signatures[0], signatures[1], "nil body and http.NoBody must produce identical signatures")
}

// Every SDK checks its base URL against the rows in cross_test/test_vectors.json.
func TestNewServiceClient_BaseURLVectors(t *testing.T) {
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err)
	var vectors struct {
		BaseURLParsing []struct {
			Name, Input, Error string
			Valid              bool
		} `json:"base_url_parsing"`
	}
	require.NoError(t, json.Unmarshal(data, &vectors))
	require.NotEmpty(t, vectors.BaseURLParsing)

	for _, row := range vectors.BaseURLParsing {
		t.Run(row.Name, func(t *testing.T) {
			var used string
			capture := func(_ connect.HTTPClient, baseURL string, _ ...connect.ClientOption) stubClient {
				used = baseURL
				return stubClient{}
			}
			_, err := NewServiceClient("", capture, WithSignatureFunction(testSignFn(t)), WithBaseURL(row.Input))
			if !row.Valid {
				require.EqualError(t, err, row.Error)
				return
			}
			require.NoError(t, err)
			want := row.Input
			if !strings.Contains(want, "://") {
				want = "https://" + want // a base URL without a scheme is read as https
			}
			require.Equal(t, want, used)
		})
	}
}

func TestNewServiceClient_ValidationErrors(t *testing.T) {
	factory := func(_ connect.HTTPClient, _ string, _ ...connect.ClientOption) stubClient {
		return stubClient{}
	}

	t.Run("a key that is not 64 hex characters is refused", func(t *testing.T) {
		_, err := NewServiceClient("0x1234", factory)
		require.EqualError(t, err, "private key must be 32 bytes (64 hex characters)")
	})

	t.Run("a timeout of zero is refused", func(t *testing.T) {
		_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithTimeout(0))
		require.ErrorIs(t, err, ErrInvalidTimeOut)
		require.EqualError(t, err, "WithTimeout must be a positive duration")

		_, err = NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithStreamTimeout(0))
		require.ErrorIs(t, err, ErrInvalidStreamTimeout)
		require.EqualError(t, err, "WithStreamTimeout must be a positive duration")
	})

	t.Run("empty key and no signFn", func(t *testing.T) {
		_, err := NewServiceClient("", factory)
		require.ErrorIs(t, err, ErrEmptyPrivateKey)
		require.EqualError(t, err, "private key must not be null or empty")
	})
}

// Every gRPC client on an http:// base URL shares one transport, with the default dial and idle
// timeouts, so a client per request does not leave a connection behind each time.
func TestNewServiceClient_GRPCOverHTTPSharesOneTransport(t *testing.T) {
	transportOf := func() http.RoundTripper {
		factory, captured := capturingFactory()
		_, err := NewServiceClient("", factory,
			WithSignatureFunction(testSignFn(t)), WithBaseURL("http://localhost:1"), WithProtocol(ProtocolGRPC))
		require.NoError(t, err)
		return captured().(*http.Client).Transport.(*SigningTransport).transport
	}

	first, second := transportOf(), transportOf()
	require.Same(t, first, second)
	shared, ok := first.(*http.Transport)
	require.True(t, ok)
	require.NotNil(t, shared.DialContext)
	require.Equal(t, http.DefaultTransport.(*http.Transport).IdleConnTimeout, shared.IdleConnTimeout)
	require.Nil(t, shared.Proxy)
}
