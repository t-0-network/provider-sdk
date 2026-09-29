package network

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
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
	pubKey, err := crypto.GetPublicKeyFromBytes(pubKeyBytes)
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

// A GET request carries its message in the URL, where the signature does not cover it.
func TestSigningTransport_RefusesGET(t *testing.T) {
	st := NewSigningTransport(testSignFn(t), time.Now, WithTransport(roundTripperFunc(func(*http.Request) (*http.Response, error) {
		t.Fatal("a GET request must not be sent")
		return nil, nil
	})))

	for _, method := range []string{http.MethodGet, ""} {
		req, err := http.NewRequest(method, "http://localhost/health", nil)
		require.NoError(t, err)
		_, err = st.RoundTrip(req)
		require.Equal(t, connect.CodeUnimplemented, connect.CodeOf(err), "method %q: %v", method, err)
		require.ErrorContains(t, err, "GET requests are not supported")
	}
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

func TestNewServiceClient_ValidationErrors(t *testing.T) {
	factory := func(_ connect.HTTPClient, _ string, _ ...connect.ClientOption) stubClient {
		return stubClient{}
	}

	t.Run("empty base URL", func(t *testing.T) {
		_, err := NewServiceClient("", factory,
			WithSignatureFunction(testSignFn(t)),
			WithBaseURL(""),
		)
		require.ErrorIs(t, err, ErrEmptyBaseURL)
		require.EqualError(t, err, "base URL is not set")
	})

	for _, bad := range []string{"api.t-0.network", "ftp://api.t-0.network", "https://", "http:///path", "://api.t-0.network"} {
		t.Run(fmt.Sprintf("base URL %q is refused", bad), func(t *testing.T) {
			_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithBaseURL(bad))
			require.ErrorIs(t, err, ErrInvalidBaseURL)
			require.EqualError(t, err, "base URL is not valid")
		})
	}

	t.Run("http and https base URLs are accepted", func(t *testing.T) {
		for _, good := range []string{"http://localhost:8080", "HTTPS://api.t-0.network/"} {
			_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithBaseURL(good))
			require.NoError(t, err, good)
		}
	})

	t.Run("a key that is not 64 hex characters is refused", func(t *testing.T) {
		_, err := NewServiceClient("0x1234", factory)
		require.EqualError(t, err, "private key must be 32 bytes (64 hex characters)")
	})

	const maxTimeout = 2147483647 * time.Millisecond
	for _, bad := range []time.Duration{0, -time.Second, maxTimeout + time.Millisecond} {
		t.Run(fmt.Sprintf("timeout %v is refused", bad), func(t *testing.T) {
			_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithTimeout(bad))
			require.ErrorIs(t, err, ErrInvalidTimeOut)
			require.EqualError(t, err, "WithTimeout must be a positive duration of at most 2147483647 ms")

			_, err = NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithStreamTimeout(bad))
			require.ErrorIs(t, err, ErrInvalidStreamTimeout)
			require.EqualError(t, err, "WithStreamTimeout must be a positive duration of at most 2147483647 ms")
		})
	}

	t.Run("unknown wire format and protocol", func(t *testing.T) {
		_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithWireFormat(WireFormat(7)))
		require.EqualError(t, err, "unknown wire format 7")
		_, err = NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)), WithProtocol(Protocol(7)))
		require.EqualError(t, err, "unknown protocol 7")
	})

	t.Run("the largest timeouts are accepted", func(t *testing.T) {
		_, err := NewServiceClient("", factory, WithSignatureFunction(testSignFn(t)),
			WithTimeout(maxTimeout), WithStreamTimeout(maxTimeout))
		require.NoError(t, err)
	})

	t.Run("empty key and no signFn", func(t *testing.T) {
		_, err := NewServiceClient("", factory)
		require.ErrorIs(t, err, ErrEmptyPrivateKey)
		require.EqualError(t, err, "private key must not be null or empty")
	})
}
