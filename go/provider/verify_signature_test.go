package provider

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
)

func envelopeOf(flags byte, payload []byte) []byte {
	prefix := []byte{flags, 0, 0, 0, 0}
	binary.BigEndian.PutUint32(prefix[1:], uint32(len(payload)))
	return append(prefix, payload...)
}

// signedHeaders signs bytes the way every SDK does: Keccak256(bytes || uint64le(ts_ms)).
func signedHeaders(t *testing.T, key *secp256k1.PrivateKey, signed []byte, ts time.Time) http.Header {
	t.Helper()
	var tsBytes [8]byte
	binary.LittleEndian.PutUint64(tsBytes[:], uint64(ts.UnixMilli()))
	signature, publicKey, err := crypto.NewSigner(key)(crypto.LegacyKeccak256Concat(signed, tsBytes[:]))
	require.NoError(t, err)
	h := http.Header{}
	h.Set(common.PublicKeyHeader, "0x"+hex.EncodeToString(publicKey))
	h.Set(common.SignatureHeader, "0x"+hex.EncodeToString(signature))
	h.Set(common.SignatureTimestampHeader, strconv.FormatInt(ts.UnixMilli(), 10))
	return h
}

func withHeader(h http.Header, name, value string) http.Header {
	h = h.Clone()
	if value == "" {
		h.Del(name)
	} else {
		h.Set(name, value)
	}
	return h
}

// The verdict of the middleware for each kind of request, with real signatures: what the signature
// covers (the whole body, or the first envelope with or without its prefix) and every rejection.
func TestSignatureVerifierMiddleware(t *testing.T) {
	const limit = 1024
	networkKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	otherKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	verifier, err := newSignatureVerifier(crypto.HexPublicKey(networkKey.PubKey()))
	require.NoError(t, err)

	now := time.Now()
	sign := func(signed []byte) http.Header { return signedHeaders(t, networkKey, signed, now) }

	p1, p2 := []byte("\x0a\x02m1"), []byte("\x0a\x02m2")
	env1, env2 := envelopeOf(0, p1), envelopeOf(0, p2)
	stream := slices.Concat(env1, env2)
	// The codec does not matter: Connect JSON streams are enveloped and signed alike.
	jsonEnv1 := envelopeOf(0, []byte(`"m1"`))
	jsonStream := slices.Concat(jsonEnv1, envelopeOf(0, []byte(`"m2"`)))
	compressedEnv1 := envelopeOf(1, p1)
	atLimit := envelopeOf(0, bytes.Repeat([]byte("x"), limit-5))
	overLimit := envelopeOf(0, bytes.Repeat([]byte("x"), limit-4))
	longStream := slices.Concat(env1, bytes.Repeat(env2, limit)) // the first envelope is what is limited
	unaryBody := []byte("\x08\x2a\x10\x01\x1a\x03EUR")
	fullSignature := sign(p1).Get(common.SignatureHeader)

	cases := []struct {
		name        string
		contentType string
		body        []byte
		headers     http.Header
		noLength    bool         // sent without a Content-Length
		part        SignedPart   // accepted over this
		code        connect.Code // or rejected with this code
		reason      string       // and this in its message
	}{
		// Not enveloped: the whole body is signed.
		{name: "unary, whole body", contentType: "application/proto", body: unaryBody, headers: sign(unaryBody), part: SignedBody},
		{name: "unary json, whole body", contentType: "application/json", body: []byte(`{"a":1}`), headers: sign([]byte(`{"a":1}`)), part: SignedBody},
		{name: "unary, no content type", body: unaryBody, headers: sign(unaryBody), part: SignedBody},
		{name: "unary, empty body", contentType: "application/proto", headers: sign(nil), part: SignedBody},
		{name: "unary, signed over something else", contentType: "application/proto", body: unaryBody, headers: sign(p1), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "unary, frame-like body signed over its payload", contentType: "application/proto", body: env1, headers: sign(p1), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "unary, body at the limit", contentType: "application/proto", body: atLimit, headers: sign(atLimit), noLength: true, part: SignedBody},
		{name: "unary, body over the limit", contentType: "application/proto", body: overLimit, headers: sign(overLimit), code: connect.CodeResourceExhausted, reason: "max payload size of 1024 bytes exceeded"},
		{name: "unary, body over the limit, no Content-Length", contentType: "application/proto", body: overLimit, headers: sign(overLimit), noLength: true, code: connect.CodeResourceExhausted, reason: "max payload size of 1024 bytes exceeded"},

		// Enveloped: the first envelope is signed, prefix included; over gRPC also its payload alone.
		{name: "connect, first envelope", contentType: "application/connect+proto", body: stream, headers: sign(env1), part: SignedEnvelope},
		{name: "connect+json, first envelope", contentType: "application/connect+json", body: jsonStream, headers: sign(jsonEnv1), part: SignedEnvelope},
		{name: "connect, content type with parameters", contentType: "Application/Connect+proto; x=y", body: stream, headers: sign(env1), part: SignedEnvelope},
		{name: "connect, compressed first envelope", contentType: "application/connect+proto", body: compressedEnv1, headers: sign(compressedEnv1), part: SignedEnvelope},
		{name: "grpc, first envelope", contentType: "application/grpc", body: stream, headers: sign(env1), part: SignedEnvelope},
		{name: "grpc+proto, first payload", contentType: "application/grpc+proto", body: stream, headers: sign(p1), part: SignedPayload},
		{name: "grpc unary, envelope", contentType: "application/grpc", body: env1, headers: sign(env1), part: SignedEnvelope},
		{name: "grpc unary, payload", contentType: "application/grpc", body: env1, headers: sign(p1), part: SignedPayload},
		{name: "grpc, compressed, payload", contentType: "application/grpc", body: compressedEnv1, headers: sign(p1), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "connect, first payload", contentType: "application/connect+proto", body: stream, headers: sign(p1), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "connect+json, whole body", contentType: "application/connect+json", body: jsonStream, headers: sign(jsonStream), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "grpc, whole body", contentType: "application/grpc+proto", body: stream, headers: sign(stream), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "second envelope", contentType: "application/connect+proto", body: stream, headers: sign(env2), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "no first message", contentType: "application/connect+proto", headers: sign(nil), code: connect.CodeUnauthenticated, reason: "no first message"},
		{name: "part of a prefix", contentType: "application/grpc", body: env1[:3], headers: sign(env1), code: connect.CodeUnauthenticated, reason: "no first message"},
		{name: "truncated first message", contentType: "application/connect+proto", body: env1[:len(env1)-1], headers: sign(env1), code: connect.CodeUnauthenticated, reason: "truncated first message"},
		{name: "first envelope at the limit", contentType: "application/connect+proto", body: atLimit, headers: sign(atLimit), part: SignedEnvelope},
		{name: "first envelope over the limit", contentType: "application/connect+proto", body: overLimit, headers: sign(overLimit), code: connect.CodeResourceExhausted, reason: "max payload size of 1024 bytes exceeded"},
		{name: "stream over the limit as a whole", contentType: "application/connect+proto", body: longStream, headers: sign(env1), noLength: true, part: SignedEnvelope},

		// The headers, checked before any of the body is read.
		{name: "missing public key", contentType: "application/connect+proto", body: stream, headers: withHeader(sign(env1), common.PublicKeyHeader, ""), code: connect.CodeInvalidArgument, reason: "missing required header: X-Public-Key"},
		{name: "missing signature", contentType: "application/connect+proto", body: stream, headers: withHeader(sign(env1), common.SignatureHeader, ""), code: connect.CodeInvalidArgument, reason: "missing required header: X-Signature"},
		{name: "missing timestamp", contentType: "application/connect+proto", body: stream, headers: withHeader(sign(env1), common.SignatureTimestampHeader, ""), code: connect.CodeInvalidArgument, reason: "missing required header: X-Signature-Timestamp"},
		{name: "signature not hex", contentType: "application/proto", body: unaryBody, headers: withHeader(sign(unaryBody), common.SignatureHeader, "0xINVALIDHEX"), code: connect.CodeInvalidArgument, reason: "invalid header encoding: X-Signature"},
		{name: "timestamp not a number", contentType: "application/proto", body: unaryBody, headers: withHeader(sign(unaryBody), common.SignatureTimestampHeader, "invalid-timestamp"), code: connect.CodeInvalidArgument, reason: "invalid timestamp header"},
		{name: "stale timestamp", contentType: "application/connect+proto", body: stream, headers: signedHeaders(t, networkKey, env1, now.Add(-2*time.Minute)), code: connect.CodeInvalidArgument, reason: "timestamp is outside the allowed time window"},
		{name: "future timestamp", contentType: "application/proto", body: unaryBody, headers: signedHeaders(t, networkKey, unaryBody, now.Add(2*time.Minute)), code: connect.CodeInvalidArgument, reason: "timestamp is outside the allowed time window"},
		{name: "timestamp inside the window", contentType: "application/connect+proto", body: stream, headers: signedHeaders(t, networkKey, env1, now.Add(-59*time.Second)), part: SignedEnvelope},
		{name: "other key", contentType: "application/connect+proto", body: stream, headers: signedHeaders(t, otherKey, env1, now), code: connect.CodeUnauthenticated, reason: "request signed with unknown public key"},
		{name: "other key and stale timestamp", contentType: "application/connect+proto", body: stream, headers: signedHeaders(t, otherKey, env1, now.Add(-2*time.Minute)), code: connect.CodeInvalidArgument, reason: "timestamp is outside the allowed time window"},
		{name: "public key not a key", contentType: "application/proto", body: unaryBody, headers: withHeader(sign(unaryBody), common.PublicKeyHeader, "0xzz"), code: connect.CodeUnauthenticated, reason: "request signed with unknown public key"},
		{name: "64-byte signature", contentType: "application/grpc", body: stream, headers: withHeader(sign(p1), common.SignatureHeader, fullSignature[:2+128]), part: SignedPayload},
		{name: "63-byte signature", contentType: "application/grpc", body: stream, headers: withHeader(sign(p1), common.SignatureHeader, fullSignature[:2+126]), code: connect.CodeUnauthenticated, reason: "signature verification failed"},
		{name: "rejected headers, body over the limit", contentType: "application/proto", body: overLimit, headers: http.Header{}, code: connect.CodeInvalidArgument, reason: "missing required header"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var part SignedPart
			var verifyErr error
			var read []byte
			var readErr error
			handlerRan := false
			handler := newSignatureVerifierMiddleware(verifier, limit)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				handlerRan = true
				part, verifyErr = SignatureVerification(r.Context())
				read, readErr = io.ReadAll(r.Body)
			}))

			req := httptest.NewRequest(http.MethodPost, "/test.v1.StreamTest/ClientStream", bytes.NewReader(tc.body))
			if tc.noLength {
				req.ContentLength = -1
			}
			for name, values := range tc.headers {
				req.Header[name] = values
			}
			if tc.contentType != "" {
				req.Header.Set("Content-Type", tc.contentType)
			}
			recorder := httptest.NewRecorder()
			handler.ServeHTTP(recorder, req)

			if tc.part == "" {
				// The middleware answers a rejected request itself, in the request's protocol.
				require.False(t, handlerRan, "a rejected request reached the handler")
				code, message := recordedError(t, recorder, tc.contentType)
				require.Equal(t, tc.code, code, "message: %s", message)
				require.Contains(t, message, tc.reason)
				return
			}
			require.True(t, handlerRan)
			require.NoError(t, verifyErr)
			require.Equal(t, tc.part, part)

			// The handler reads the whole body, the part read for the signature included.
			require.NoError(t, readErr)
			require.Equal(t, len(tc.body), len(read))
			require.True(t, bytes.Equal(tc.body, read), "the handler reads another body")
		})
	}
}

// recordedError reads the code and message of the error response the middleware wrote: gRPC
// trailers-only headers, a Connect end-stream message, or a Connect unary JSON body.
func recordedError(t *testing.T, recorder *httptest.ResponseRecorder, contentType string) (connect.Code, string) {
	t.Helper()
	var wire struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	}
	switch ct := strings.ToLower(contentType); {
	case strings.HasPrefix(ct, "application/grpc"):
		status, err := strconv.Atoi(recorder.Header().Get("Grpc-Status"))
		require.NoError(t, err, "grpc-status of %v", recorder.Header())
		message, err := url.PathUnescape(recorder.Header().Get("Grpc-Message"))
		require.NoError(t, err)
		return connect.Code(status), message
	case strings.HasPrefix(ct, "application/connect+"):
		body := recorder.Body.Bytes()
		require.GreaterOrEqual(t, len(body), 5)
		require.Equal(t, byte(2), body[0], "an end-stream message")
		var endStream struct {
			Error json.RawMessage `json:"error"`
		}
		require.NoError(t, json.Unmarshal(body[5:], &endStream))
		require.NoError(t, json.Unmarshal(endStream.Error, &wire))
	default:
		require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &wire), "body: %s", recorder.Body.String())
	}
	var code connect.Code
	require.NoError(t, code.UnmarshalText([]byte(wire.Code)))
	return code, wire.Message
}

func TestSignatureVerification_OutsideTheMiddleware(t *testing.T) {
	part, err := SignatureVerification(context.Background())
	require.Empty(t, part)
	require.Equal(t, connect.CodeInternal, connect.CodeOf(err))
	require.ErrorIs(t, err, ErrNoSignatureResult)
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

	uncompressed := hex.EncodeToString(networkKey.PubKey().SerializeUncompressed())

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
		{name: "not hex", publicKey: "0xzz", code: connect.CodeUnauthenticated},
		{name: "prefix only", publicKey: "0x", code: connect.CodeUnauthenticated},
		{name: "odd length", publicKey: "0x" + uncompressed[1:], code: connect.CodeUnauthenticated},
		{name: "whitespace inside", publicKey: "0x" + uncompressed[:66] + " " + uncompressed[66:], code: connect.CodeUnauthenticated},
		{name: "wrong length", publicKey: "0x" + uncompressed[:128], code: connect.CodeUnauthenticated},
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

type countingBody struct {
	io.ReadCloser
	read *atomic.Int64
}

func (b countingBody) Read(p []byte) (int, error) {
	n, err := b.ReadCloser.Read(p)
	b.read.Add(int64(n))
	return n, err
}

// A request rejected for its headers goes on to connect-go with its body
// unread, and connect-go reads the rest of a body that is too large, so the
// limit applies to every read: a body over it is ResourceExhausted, rejected
// after at most limit+1 bytes.
func TestSignatureVerification_BodyOverTheLimit(t *testing.T) {
	const limit = 1024
	networkKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	verify, err := newSignatureVerifier(crypto.HexPublicKey(networkKey.PubKey()))
	require.NoError(t, err)
	defaultOptions, err := newDefaultHandlerOptions(verify, nil, "")
	require.NoError(t, err)
	path, handler := Handler(grpchealth.NewHandler, grpchealth.Checker(grpchealth.NewStaticChecker()), WithMaxBodySize(limit))(defaultOptions)

	var read atomic.Int64
	mux := http.NewServeMux()
	mux.Handle(path, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Body = countingBody{ReadCloser: r.Body, read: &read}
		handler.ServeHTTP(w, r)
	}))
	srv := httptest.NewUnstartedServer(mux)
	srv.EnableHTTP2 = true
	srv.StartTLS()
	t.Cleanup(srv.Close)

	signed := &http.Client{
		Transport: network.NewSigningTransport(crypto.NewSigner(networkKey), time.Now, network.WithTransport(srv.Client().Transport)),
	}
	request := &grpchealth.CheckRequest{Service: strings.Repeat("x", 1024*limit)}
	for _, protocol := range []struct {
		name string
		opts []connect.ClientOption
	}{
		{"connect", nil},
		{"grpc", []connect.ClientOption{connect.WithGRPC()}},
	} {
		for _, client := range []struct {
			name       string
			httpClient *http.Client
			code       connect.Code
		}{
			// The headers are checked first: a request they reject is refused without its body.
			{"no signature headers", srv.Client(), connect.CodeInvalidArgument},
			{"signed", signed, connect.CodeResourceExhausted},
		} {
			t.Run(protocol.name+"/"+client.name, func(t *testing.T) {
				read.Store(0)
				_, err := grpchealth.NewClient(client.httpClient, srv.URL, protocol.opts...).Check(context.Background(), request)
				require.Equal(t, client.code, connect.CodeOf(err), "error: %v", err)
				require.LessOrEqual(t, read.Load(), int64(limit+1))
			})
		}
	}
}

// WithVerifySignatureFn is kept for compatibility only: a function that
// accepts everything does not let in a request signed by another key.
func TestWithVerifySignatureFn_HasNoEffect(t *testing.T) {
	networkKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	otherKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	acceptAll := WithVerifySignatureFn(func(_, _, _ []byte) error { return nil })
	mux, err := NewHttpHandler(
		NetworkPublicKeyHexed(crypto.HexPublicKey(networkKey.PubKey())),
		Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(paymentconnect.UnimplementedProviderServiceHandler{}), acceptAll),
	)
	require.NoError(t, err)
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)

	httpClient := &http.Client{Transport: network.NewSigningTransport(crypto.NewSigner(otherKey), time.Now)}
	_, err = paymentconnect.NewProviderServiceClient(httpClient, srv.URL).UpdatePayment(context.Background(), connect.NewRequest(&payment.UpdatePaymentRequest{}))
	require.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err), "error: %v", err)
}
