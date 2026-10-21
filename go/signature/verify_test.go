package signature

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"slices"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/types/known/wrapperspb"

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

type countingBody struct {
	io.Reader
	read *atomic.Int64
}

func (b countingBody) Read(p []byte) (int, error) {
	n, err := b.Reader.Read(p)
	b.read.Add(int64(n))
	return n, err
}

type callerContextKey struct{}

// The resolver gets the X-Public-Key value once the headers pass and before any of the body is
// read. The handler gets the context it returned, with the verdict: the signed part, the
// timestamp and the bytes read for the signature.
func TestNewMiddleware_KeyResolver(t *testing.T) {
	signer, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	other, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	ts := time.UnixMilli(time.Now().UnixMilli())
	sign := func(signed []byte) http.Header { return signedHeaders(t, signer, signed, ts) }
	with := func(h http.Header, name, value string) http.Header {
		h = h.Clone()
		h.Set(name, value)
		return h
	}

	p1 := []byte("\x0a\x02m1")
	env1 := envelopeOf(0, p1)
	stream := slices.Concat(env1, envelopeOf(0, []byte("\x0a\x02m2")))
	unaryBody := []byte("\x08\x2a\x10\x01\x1a\x03EUR")
	lookupFailed := connect.NewError(connect.CodeInternal, errors.New("lookup failed"))

	cases := []struct {
		name        string
		contentType string
		body        []byte
		headers     http.Header
		resolveErr  error
		resolved    bool         // the resolver was called
		part        SignedPart   // accepted over this
		read        []byte       // with these bytes in the verdict
		code        connect.Code // or rejected with this code
		message     string       // and this message
	}{
		{name: "unary", contentType: "application/proto", body: unaryBody, headers: sign(unaryBody), resolved: true, part: SignedBody, read: unaryBody},
		{name: "connect stream", contentType: "application/connect+proto", body: stream, headers: sign(env1), resolved: true, part: SignedEnvelope, read: env1},
		{name: "grpc, payload", contentType: "application/grpc", body: stream, headers: sign(p1), resolved: true, part: SignedPayload, read: env1},

		{name: "key rejected", contentType: "application/proto", body: unaryBody, headers: sign(unaryBody), resolveErr: errors.New("no such key"), resolved: true, code: connect.CodeUnauthenticated, message: "request signed with unknown public key"},
		{name: "resolver's connect error", contentType: "application/proto", body: unaryBody, headers: sign(unaryBody), resolveErr: lookupFailed, resolved: true, code: connect.CodeInternal, message: "lookup failed"},
		{name: "resolver's wrapped connect error", contentType: "application/proto", body: unaryBody, headers: sign(unaryBody), resolveErr: fmt.Errorf("lookup: %w", lookupFailed), resolved: true, code: connect.CodeInternal, message: "lookup failed"},
		{name: "key rejected before the signature length", contentType: "application/proto", body: unaryBody, headers: with(sign(unaryBody), common.SignatureHeader, "0x00"), resolveErr: errors.New("no such key"), resolved: true, code: connect.CodeUnauthenticated, message: "request signed with unknown public key"},
		{name: "key accepted, signed by another", contentType: "application/proto", body: unaryBody, headers: with(sign(unaryBody), common.PublicKeyHeader, crypto.HexPublicKey(other.PubKey())), resolved: true, code: connect.CodeUnauthenticated, message: "signature verification failed"},
		{name: "public key not a key", contentType: "application/proto", body: unaryBody, headers: with(sign(unaryBody), common.PublicKeyHeader, "0xzz"), code: connect.CodeUnauthenticated, message: "request signed with unknown public key"},
		{name: "stale timestamp", contentType: "application/proto", body: unaryBody, headers: signedHeaders(t, signer, unaryBody, ts.Add(-2*time.Minute)), code: connect.CodeInvalidArgument, message: "timestamp is outside the allowed time window"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var bodyRead atomic.Int64
			resolvedWith, readBeforeResolve := "", int64(-1)
			resolve := func(ctx context.Context, publicKey string) (context.Context, error) {
				resolvedWith, readBeforeResolve = publicKey, bodyRead.Load()
				if tc.resolveErr != nil {
					return nil, tc.resolveErr
				}
				return context.WithValue(ctx, callerContextKey{}, publicKey), nil
			}

			var verdict Verdict
			var verdictOK bool
			var caller any
			var handlerBody []byte
			handlerRan := false
			handler := NewMiddleware(resolve, 1024)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				handlerRan = true
				verdict, verdictOK = FromContext(r.Context())
				caller = r.Context().Value(callerContextKey{})
				handlerBody, _ = io.ReadAll(r.Body)
			}))

			req := httptest.NewRequest(http.MethodPost, "/test.v1.Test/Call", countingBody{bytes.NewReader(tc.body), &bodyRead})
			req.Header = tc.headers.Clone()
			req.Header.Set("Content-Type", tc.contentType)
			recorder := httptest.NewRecorder()
			handler.ServeHTTP(recorder, req)

			if tc.resolved {
				require.Equal(t, tc.headers.Get(common.PublicKeyHeader), resolvedWith)
				require.Zero(t, readBeforeResolve, "the body was read before the resolver ran")
			} else {
				require.Equal(t, int64(-1), readBeforeResolve, "the resolver ran")
			}

			if tc.part == "" {
				require.False(t, handlerRan, "a rejected request reached the handler")
				var wire struct {
					Code    string `json:"code"`
					Message string `json:"message"`
				}
				require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &wire), "body: %s", recorder.Body.String())
				require.Equal(t, tc.code.String(), wire.Code)
				require.Equal(t, tc.message, wire.Message)
				return
			}
			require.True(t, handlerRan)
			require.True(t, verdictOK)
			require.Equal(t, tc.part, verdict.Part)
			require.Equal(t, ts.UnixMilli(), verdict.Timestamp.UnixMilli())
			require.Equal(t, tc.read, verdict.Body)
			require.Equal(t, resolvedWith, caller, "the handler got another context than the resolver's")
			require.Equal(t, tc.body, handlerBody)
		})
	}
}

func TestFromContext_OutsideTheMiddleware(t *testing.T) {
	_, ok := FromContext(context.Background())
	require.False(t, ok)
}

// A *connect.Error from the resolver is the answer to the request in its own protocol: Connect
// unary, Connect stream and gRPC.
func TestNewMiddleware_ResolverConnectError(t *testing.T) {
	key, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	resolve := func(context.Context, string) (context.Context, error) {
		return nil, connect.NewError(connect.CodeInternal, errors.New("lookup failed"))
	}
	handler := NewMiddleware(resolve, 1024)(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
		t.Error("a rejected request reached the handler")
	}))
	srv := httptest.NewUnstartedServer(handler)
	srv.Config.Protocols = new(http.Protocols)
	srv.Config.Protocols.SetHTTP1(true)
	srv.Config.Protocols.SetUnencryptedHTTP2(true)
	srv.Start()
	t.Cleanup(srv.Close)

	newClient := func(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue] {
		return connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+"/test.v1.Test/Call", opts...)
	}
	for _, protocol := range []struct {
		name string
		opts []network.ClientOption
	}{
		{"connect", nil},
		{"grpc", []network.ClientOption{network.WithProtocol(network.ProtocolGRPC)}},
	} {
		t.Run(protocol.name, func(t *testing.T) {
			client, err := network.NewServiceClient(network.PrivateKeyHexed(crypto.HexPrivateKey(key)), newClient,
				append([]network.ClientOption{network.WithBaseURL(srv.URL)}, protocol.opts...)...)
			require.NoError(t, err)

			_, err = client.CallUnary(context.Background(), connect.NewRequest(wrapperspb.String("m1")))
			require.Equal(t, connect.CodeInternal, connect.CodeOf(err), "unary: %v", err)
			require.ErrorContains(t, err, "lookup failed")

			stream, err := client.CallServerStream(context.Background(), connect.NewRequest(wrapperspb.String("m1")))
			if err == nil {
				for stream.Receive() {
				}
				err = stream.Err()
				_ = stream.Close()
			}
			require.Equal(t, connect.CodeInternal, connect.CodeOf(err), "stream: %v", err)
			require.ErrorContains(t, err, "lookup failed")
		})
	}
}
