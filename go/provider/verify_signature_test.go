package provider

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
)

func TestNewSignatureVerifierMiddleware(t *testing.T) {
	// Mock verify signature function for testing
	mockVerifySignature := func(returnError bool) VerifySignature {
		return func(publicKey, message, signature []byte) error {
			if returnError {
				return fmt.Errorf("signature verification failed")
			}
			return nil
		}
	}

	// Helper to create valid headers
	createValidHeaders := func() http.Header {
		headers := http.Header{}
		headers.Set(common.PublicKeyHeader, "0x"+hex.EncodeToString([]byte("validpublickey")))
		headers.Set(common.SignatureHeader, "0x"+hex.EncodeToString([]byte("validsignature")))

		timestamp := time.Now().UnixMilli()
		headers.Set(common.SignatureTimestampHeader, strconv.FormatInt(timestamp, 10))

		return headers
	}

	tests := []struct {
		name                string
		setupHeaders        func() http.Header
		requestBody         string
		verifySignatureFunc VerifySignature
		expectedError       *SignatureError
	}{
		{
			name:                "valid request with all headers",
			setupHeaders:        createValidHeaders,
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError:       nil,
		},
		{
			name: "missing public key header",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Del(common.PublicKeyHeader)
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrMissingRequiredHeader.Error(), common.PublicKeyHeader),
			},
		},
		{
			name: "missing signature header",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Del(common.SignatureHeader)
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrMissingRequiredHeader.Error(), common.SignatureHeader),
			},
		},
		{
			name: "missing timestamp header",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Del(common.SignatureTimestampHeader)
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrMissingRequiredHeader.Error(), common.SignatureTimestampHeader),
			},
		},
		{
			name: "invalid public key header encoding",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Set(common.PublicKeyHeader, "0xINVALIDHEX")
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrInvalidHeaderEncoding.Error(), common.PublicKeyHeader),
			},
		},
		{
			name: "invalid signature header encoding",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Set(common.SignatureHeader, "0xINVALIDHEX")
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrInvalidHeaderEncoding.Error(), common.SignatureHeader),
			},
		},
		{
			name: "public key header too short",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Set(common.PublicKeyHeader, "0")
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     fmt.Sprintf("%s: %s", ErrInvalidHeaderEncoding.Error(), common.PublicKeyHeader),
			},
		},
		{
			name: "invalid timestamp format",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				headers.Set(common.SignatureTimestampHeader, "invalid-timestamp")
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     "invalid timestamp",
			},
		},
		{
			name: "timestamp outside allowed window (too old)",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				oldTimestamp := time.Now().Add(-2 * time.Minute).UnixMilli()
				headers.Set(common.SignatureTimestampHeader, strconv.FormatInt(oldTimestamp, 10))
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     "timestamp is outside the allowed time window",
			},
		},
		{
			name: "timestamp outside allowed window (too new)",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				futureTimestamp := time.Now().Add(2 * time.Minute).UnixMilli()
				headers.Set(common.SignatureTimestampHeader, strconv.FormatInt(futureTimestamp, 10))
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeInvalidArgument,
				Message:     "timestamp is outside the allowed time window",
			},
		},
		{
			name:                "signature verification fails",
			setupHeaders:        createValidHeaders,
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(true),
			expectedError: &SignatureError{
				ConnectCode: connect.CodeUnauthenticated,
				Message:     "signature verification failed",
			},
		},
		{
			name:                "empty body success",
			setupHeaders:        createValidHeaders,
			requestBody:         "",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError:       nil,
		},
		{
			name: "timestamp exactly at boundary (valid)",
			setupHeaders: func() http.Header {
				headers := createValidHeaders()
				boundaryTimestamp := time.Now().Add(-59 * time.Second).UnixMilli()
				headers.Set(common.SignatureTimestampHeader, strconv.FormatInt(boundaryTimestamp, 10))
				return headers
			},
			requestBody:         "test body",
			verifySignatureFunc: mockVerifySignature(false),
			expectedError:       nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create middleware
			middleware := newSignatureVerifierMiddleware(tt.verifySignatureFunc, 1024*1024)

			// Create test handler that checks for signature errors
			var capturedError *SignatureError
			testHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				sigErr, exists := getSignatureErrorFromContext(r.Context())
				if exists {
					capturedError = sigErr
				}
				w.WriteHeader(http.StatusOK)
			})

			// Wrap handler with middleware
			wrappedHandler := middleware(testHandler)

			// Create request
			req := httptest.NewRequest(http.MethodPost, "/test", bytes.NewReader([]byte(tt.requestBody)))
			req.Header = tt.setupHeaders()

			// Create response recorder
			rr := httptest.NewRecorder()

			// Execute request
			wrappedHandler.ServeHTTP(rr, req)

			// Verify results
			if tt.expectedError == nil {
				require.Nil(t, capturedError)
			} else {
				require.NotNil(t, capturedError)
				require.Equal(t, tt.expectedError.ConnectCode, capturedError.ConnectCode)
				require.Contains(t, capturedError.Message, tt.expectedError.Message)
			}
		})
	}
}

func makeGRPCFrame(payload []byte) []byte {
	frame := make([]byte, 5+len(payload))
	frame[0] = 0
	binary.BigEndian.PutUint32(frame[1:5], uint32(len(payload)))
	copy(frame[5:], payload)
	return frame
}

func TestIsGRPCRequest(t *testing.T) {
	tests := []struct {
		contentType string
		expected    bool
	}{
		{"application/grpc", true},
		{"application/grpc+proto", true},
		{"application/grpc+json", true},
		{"application/proto", false},
		{"application/json", false},
		{"", false},
		{"application/grp", false},
	}
	for _, tt := range tests {
		t.Run(tt.contentType, func(t *testing.T) {
			req := httptest.NewRequest("POST", "/test", nil)
			req.Header.Set("Content-Type", tt.contentType)
			require.Equal(t, tt.expected, isGRPCRequest(req))
		})
	}
}

func TestHasGRPCFramePrefix(t *testing.T) {
	tests := []struct {
		name     string
		body     []byte
		expected bool
	}{
		{"valid frame with payload", makeGRPCFrame([]byte{0x08, 0x2a}), true},
		{"valid frame empty payload", makeGRPCFrame(nil), true},
		{"compressed flag 1 rejected", append([]byte{1, 0, 0, 0, 0}, []byte{}...), false},
		{"compressed flag 2 rejected", append([]byte{2, 0, 0, 0, 0}, []byte{}...), false},
		{"too short", []byte{0, 0, 0}, false},
		{"length mismatch", []byte{0, 0, 0, 0, 10}, false},
		{"nil body", nil, false},
		{"plain protobuf not a frame", []byte{0x08, 0x2a, 0x12, 0x03, 0x45, 0x55, 0x52}, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			require.Equal(t, tt.expected, hasGRPCFramePrefix(tt.body))
		})
	}
}

func TestDualFramingFallback(t *testing.T) {
	protoPayload := []byte{0x08, 0x2a, 0x10, 0x01, 0x1a, 0x03, 0x45, 0x55, 0x52}
	framedBody := makeGRPCFrame(protoPayload)

	createValidHeaders := func() http.Header {
		headers := http.Header{}
		headers.Set(common.PublicKeyHeader, "0x"+hex.EncodeToString([]byte("validpublickey")))
		headers.Set(common.SignatureHeader, "0x"+hex.EncodeToString([]byte("validsignature")))
		timestamp := time.Now().UnixMilli()
		headers.Set(common.SignatureTimestampHeader, strconv.FormatInt(timestamp, 10))
		return headers
	}

	tsFromHeaders := func(h http.Header) [8]byte {
		ts, _ := strconv.ParseInt(h.Get(common.SignatureTimestampHeader), 10, 64)
		var b [8]byte
		binary.LittleEndian.PutUint64(b[:], uint64(ts))
		return b
	}

	tests := []struct {
		name          string
		contentType   string
		body          []byte
		verifyFn      func(headers http.Header) VerifySignature
		expectedError *SignatureError
	}{
		{
			name:        "gRPC request signed unframed passes via fallback",
			contentType: "application/grpc",
			body:        framedBody,
			verifyFn: func(h http.Header) VerifySignature {
				tsBytes := tsFromHeaders(h)
				unframedMsg := append(protoPayload, tsBytes[:]...)
				return func(_, message, _ []byte) error {
					if bytes.Equal(message, unframedMsg) {
						return nil
					}
					return fmt.Errorf("signature verification failed")
				}
			},
			expectedError: nil,
		},
		{
			name:        "gRPC request with invalid signature fails after fallback",
			contentType: "application/grpc",
			body:        framedBody,
			verifyFn: func(_ http.Header) VerifySignature {
				return func(_, _, _ []byte) error {
					return fmt.Errorf("signature verification failed")
				}
			},
			expectedError: &SignatureError{
				ConnectCode: connect.CodeUnauthenticated,
				Message:     "signature verification failed",
			},
		},
		{
			name:        "non-gRPC request with frame-like body skips fallback",
			contentType: "application/proto",
			body:        framedBody,
			verifyFn: func(h http.Header) VerifySignature {
				tsBytes := tsFromHeaders(h)
				framedMsg := append(append([]byte{}, framedBody...), tsBytes[:]...)
				callCount := 0
				return func(_, message, _ []byte) error {
					callCount++
					if callCount > 1 {
						t.Fatal("fallback attempted on non-gRPC request")
					}
					if bytes.Equal(message, framedMsg) {
						return fmt.Errorf("signature verification failed")
					}
					return nil
				}
			},
			expectedError: &SignatureError{
				ConnectCode: connect.CodeUnauthenticated,
				Message:     "signature verification failed",
			},
		},
		{
			name:        "gRPC request with framed signature passes on first try",
			contentType: "application/grpc",
			body:        framedBody,
			verifyFn: func(_ http.Header) VerifySignature {
				return func(_, _, _ []byte) error { return nil }
			},
			expectedError: nil,
		},
		{
			name:        "gRPC request with body too short for frame skips fallback",
			contentType: "application/grpc",
			body:        []byte{0x08, 0x2a},
			verifyFn: func(_ http.Header) VerifySignature {
				callCount := 0
				return func(_, _, _ []byte) error {
					callCount++
					if callCount > 1 {
						t.Fatal("fallback attempted with non-frame body")
					}
					return fmt.Errorf("signature verification failed")
				}
			},
			expectedError: &SignatureError{
				ConnectCode: connect.CodeUnauthenticated,
				Message:     "signature verification failed",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			headers := createValidHeaders()
			verifyFn := tt.verifyFn(headers)
			middleware := newSignatureVerifierMiddleware(verifyFn, 1024*1024)

			var capturedError *SignatureError
			testHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				sigErr, exists := getSignatureErrorFromContext(r.Context())
				if exists {
					capturedError = sigErr
				}
				w.WriteHeader(http.StatusOK)
			})

			wrappedHandler := middleware(testHandler)
			req := httptest.NewRequest(http.MethodPost, "/test", bytes.NewReader(tt.body))
			req.Header = headers
			req.Header.Set("Content-Type", tt.contentType)
			rr := httptest.NewRecorder()
			wrappedHandler.ServeHTTP(rr, req)

			if tt.expectedError == nil {
				require.Nil(t, capturedError)
			} else {
				require.NotNil(t, capturedError)
				require.Equal(t, tt.expectedError.ConnectCode, capturedError.ConnectCode)
				require.Contains(t, capturedError.Message, tt.expectedError.Message)
			}
		})
	}
}

type roundTripperFunc func(*http.Request) (*http.Response, error)

func (f roundTripperFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

// End to end through NewHttpHandler: a signed request is sent with the
// X-Public-Key value of each case instead of the one its signer set.
func TestSignatureVerification_PublicKeyHeader(t *testing.T) {
	networkKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	otherKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	mux, err := NewHttpHandler(NetworkPublicKeyHexed(crypto.HexPublicKey(networkKey.PubKey())))
	require.NoError(t, err)
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)

	uncompressedBytes := networkKey.PubKey().SerializeUncompressed()
	uncompressed := hex.EncodeToString(uncompressedBytes)
	// Same point; the prefix carries the parity of y, so only the explicit prefix check rejects it.
	hybrid := hex.EncodeToString([]byte{0x06 | uncompressedBytes[64]&1}) + uncompressed[2:]

	tests := []struct {
		name      string
		signer    *secp256k1.PrivateKey // nil: the network key
		publicKey string                // empty: header removed
		code      connect.Code          // 0: accepted
	}{
		{name: "uncompressed", publicKey: "0x" + uncompressed},
		{name: "0X prefix", publicKey: "0X" + uncompressed},
		{name: "no prefix", publicKey: uncompressed},
		{name: "compressed", publicKey: "0x" + hex.EncodeToString(networkKey.PubKey().SerializeCompressed())},
		{name: "missing", publicKey: "", code: connect.CodeInvalidArgument},
		{name: "not hex", publicKey: "0xzz", code: connect.CodeInvalidArgument},
		{name: "prefix only", publicKey: "0x", code: connect.CodeInvalidArgument},
		{name: "odd length", publicKey: "0x" + uncompressed[1:], code: connect.CodeInvalidArgument},
		{name: "whitespace inside", publicKey: "0x" + uncompressed[:66] + " " + uncompressed[66:], code: connect.CodeInvalidArgument},
		{name: "wrong length", publicKey: "0x" + uncompressed[:128], code: connect.CodeUnauthenticated},
		{name: "hybrid", publicKey: "0x" + hybrid, code: connect.CodeUnauthenticated},
		{name: "02 prefix on 65 bytes", publicKey: "0x02" + uncompressed[2:], code: connect.CodeUnauthenticated},
		{name: "off-curve", publicKey: "0x04" + strings.Repeat("0", 128), code: connect.CodeUnauthenticated},
		{name: "other key", signer: otherKey, publicKey: crypto.HexPublicKey(otherKey.PubKey()), code: connect.CodeUnauthenticated},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			signer := tt.signer
			if signer == nil {
				signer = networkKey
			}
			setPublicKey := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
				r.Header.Del(common.PublicKeyHeader)
				if tt.publicKey != "" {
					r.Header.Set(common.PublicKeyHeader, tt.publicKey)
				}
				return http.DefaultTransport.RoundTrip(r)
			})
			httpClient := &http.Client{
				Transport: network.NewSigningTransport(crypto.NewSigner(signer), time.Now, network.WithTransport(setPublicKey)),
			}

			resp, err := grpchealth.NewClient(httpClient, srv.URL).Check(context.Background(), &grpchealth.CheckRequest{})
			if tt.code == 0 {
				require.NoError(t, err)
				require.Equal(t, grpchealth.StatusServing, resp.Status)
				return
			}
			require.Equal(t, tt.code, connect.CodeOf(err), "error: %v", err)
		})
	}
}
