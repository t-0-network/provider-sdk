package network

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
	"os"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

// test.v1.StreamTest, the service every SDK's streaming tests use (see cross_test/stream_test.proto).
const (
	procClientStream = "/test.v1.StreamTest/ClientStream"
	procServerStream = "/test.v1.StreamTest/ServerStream"
	procUnary        = "/test.v1.StreamTest/Unary"
)

type streamTestClient struct {
	clientStream *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue]
	serverStream *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue]
	unary        *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue]
}

// newStreamTestClient has the shape of a generated client constructor, so it can be passed to
// NewServiceClient.
func newStreamTestClient(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) *streamTestClient {
	return &streamTestClient{
		clientStream: connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+procClientStream, opts...),
		serverStream: connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+procServerStream, opts...),
		unary:        connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+procUnary, opts...),
	}
}

// verified is one request the test server accepted: the bytes the signature covered, and how they
// relate to the body.
type verified struct {
	procedure string
	framing   string // "envelope", "payload" (gRPC without the prefix) or "body" (unary)
	signed    []byte
}

// streamTestServer is a TLS + HTTP/2 server for test.v1.StreamTest. Like the network, it checks a
// streaming request's signature against the first envelope only, before the handler reads the rest.
type streamTestServer struct {
	url       string
	transport http.RoundTripper
	publicKey []byte

	// received gets every message the ClientStream handler reads, as it reads it.
	received chan string
	// serverStreamGap and unaryDelay slow the handlers down for the timeout tests.
	serverStreamGap time.Duration
	unaryDelay      time.Duration

	mu       sync.Mutex
	accepted []verified
	rejected []string
}

func newStreamTestServer(t *testing.T, publicKey []byte) *streamTestServer {
	t.Helper()
	s := &streamTestServer{publicKey: publicKey, received: make(chan string, 64)}

	mux := http.NewServeMux()
	mux.Handle(procClientStream, connect.NewClientStreamHandler(procClientStream,
		func(ctx context.Context, stream *connect.ClientStream[wrapperspb.StringValue]) (*connect.Response[wrapperspb.StringValue], error) {
			var got []string
			for stream.Receive() {
				got = append(got, stream.Msg().GetValue())
				s.received <- stream.Msg().GetValue()
			}
			if err := stream.Err(); err != nil {
				return nil, err
			}
			return connect.NewResponse(wrapperspb.String(strings.Join(got, ","))), nil
		}))
	mux.Handle(procServerStream, connect.NewServerStreamHandler(procServerStream,
		func(ctx context.Context, req *connect.Request[wrapperspb.StringValue], stream *connect.ServerStream[wrapperspb.StringValue]) error {
			for i := 0; i < 3; i++ {
				if i > 0 && s.serverStreamGap > 0 {
					select {
					case <-time.After(s.serverStreamGap):
					case <-ctx.Done():
						return ctx.Err()
					}
				}
				if err := stream.Send(wrapperspb.String(req.Msg.GetValue())); err != nil {
					return err
				}
			}
			return nil
		}))
	mux.Handle(procUnary, connect.NewUnaryHandler(procUnary,
		func(ctx context.Context, req *connect.Request[wrapperspb.StringValue]) (*connect.Response[wrapperspb.StringValue], error) {
			if s.unaryDelay > 0 {
				select {
				case <-time.After(s.unaryDelay):
				case <-ctx.Done():
					return nil, ctx.Err()
				}
			}
			return connect.NewResponse(wrapperspb.String(req.Msg.GetValue())), nil
		}))

	srv := httptest.NewUnstartedServer(s.verify(mux))
	srv.EnableHTTP2 = true
	srv.StartTLS()
	t.Cleanup(srv.Close)

	s.url = srv.URL
	s.transport = srv.Client().Transport
	return s
}

func (s *streamTestServer) verify(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		v, err := s.checkSignature(r)
		s.mu.Lock()
		if err != nil {
			s.rejected = append(s.rejected, err.Error())
		} else {
			s.accepted = append(s.accepted, v)
		}
		s.mu.Unlock()
		if err != nil {
			http.Error(w, err.Error(), http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *streamTestServer) checkSignature(r *http.Request) (verified, error) {
	if r.ProtoMajor != 2 {
		return verified{}, fmt.Errorf("want HTTP/2, got %s", r.Proto)
	}
	publicKey, err := hex.DecodeString(strings.TrimPrefix(r.Header.Get(common.PublicKeyHeader), "0x"))
	if err != nil || !bytes.Equal(publicKey, s.publicKey) {
		return verified{}, errors.New("unexpected public key")
	}
	signature, err := hex.DecodeString(strings.TrimPrefix(r.Header.Get(common.SignatureHeader), "0x"))
	if err != nil {
		return verified{}, errors.New("bad signature header")
	}
	timestamp, err := strconv.ParseInt(r.Header.Get(common.SignatureTimestampHeader), 10, 64)
	if err != nil {
		return verified{}, errors.New("bad timestamp header")
	}
	if d := time.Since(time.UnixMilli(timestamp)); d > time.Minute || d < -time.Minute {
		return verified{}, errors.New("timestamp outside the window")
	}

	contentType := r.Header.Get("Content-Type")
	isGRPC := contentType == "application/grpc" || strings.HasPrefix(contentType, "application/grpc+")
	candidates := map[string][]byte{}
	var order []string
	if isGRPC || strings.HasPrefix(contentType, "application/connect+") {
		var prefix [5]byte
		if _, err := io.ReadFull(r.Body, prefix[:]); err != nil {
			return verified{}, fmt.Errorf("reading first envelope prefix: %w", err)
		}
		payload := make([]byte, binary.BigEndian.Uint32(prefix[1:]))
		if _, err := io.ReadFull(r.Body, payload); err != nil {
			return verified{}, fmt.Errorf("reading first envelope payload: %w", err)
		}
		envelope := append(prefix[:], payload...)
		r.Body = struct {
			io.Reader
			io.Closer
		}{io.MultiReader(bytes.NewReader(envelope), r.Body), r.Body}
		candidates["envelope"], order = envelope, append(order, "envelope")
		if isGRPC {
			candidates["payload"], order = payload, append(order, "payload")
		}
	} else {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			return verified{}, err
		}
		r.Body = io.NopCloser(bytes.NewReader(body))
		candidates["body"], order = body, append(order, "body")
	}

	pubKey, err := crypto.GetPublicKeyFromBytes(publicKey)
	if err != nil {
		return verified{}, err
	}
	for _, framing := range order {
		signed := candidates[framing]
		if crypto.VerifySignature(pubKey, digestOf(signed, timestamp), signature) {
			return verified{procedure: r.URL.Path, framing: framing, signed: signed}, nil
		}
	}
	return verified{}, errors.New("signature does not verify")
}

func (s *streamTestServer) results() ([]verified, []string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]verified(nil), s.accepted...), append([]string(nil), s.rejected...)
}

func digestOf(signed []byte, timestampMs int64) []byte {
	var ts [8]byte
	binary.LittleEndian.PutUint64(ts[:], uint64(timestampMs))
	return crypto.LegacyKeccak256(append(append([]byte{}, signed...), ts[:]...))
}

type testKey struct {
	sign      crypto.SignFn
	publicKey []byte
}

func newTestKey(t *testing.T) testKey {
	t.Helper()
	pk, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	return testKey{sign: crypto.NewSigner(pk), publicKey: crypto.GetPublicKeyBytes(pk.PubKey())}
}

func newStreamClient(t *testing.T, srv *streamTestServer, key testKey, opts ...ClientOption) *streamTestClient {
	t.Helper()
	base := []ClientOption{
		WithSignatureFunction(key.sign),
		WithBaseURL(srv.url),
		WithHTTPTransport(srv.transport),
	}
	client, err := NewServiceClient("", newStreamTestClient, append(base, opts...)...)
	require.NoError(t, err)
	return client
}

var streamProtocols = []struct {
	name       string
	opts       []connect.ClientOption
	compressed bool
}{
	{name: "connect"},
	{name: "connect-gzip", opts: []connect.ClientOption{connect.WithSendGzip()}, compressed: true},
	{name: "grpc", opts: []connect.ClientOption{connect.WithGRPC()}},
	{name: "grpc-gzip", opts: []connect.ClientOption{connect.WithGRPC(), connect.WithSendGzip()}, compressed: true},
}

// requireFirstEnvelopeSigned checks that the server accepted exactly one request, on the envelope
// path: the Go client signs the first envelope, prefix included, for Connect and gRPC alike.
func requireFirstEnvelopeSigned(t *testing.T, srv *streamTestServer, procedure string, compressed bool) {
	t.Helper()
	accepted, rejected := srv.results()
	require.Empty(t, rejected)
	require.Len(t, accepted, 1)
	require.Equal(t, procedure, accepted[0].procedure)
	require.Equal(t, "envelope", accepted[0].framing)
	require.Equal(t, compressed, accepted[0].signed[0]&1 == 1, "compressed flag of the signed envelope")
}

func testContext(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	t.Cleanup(cancel)
	return ctx
}

func TestStream_ClientStreamSignsFirstEnvelope(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			stream := client.clientStream.CallClientStream(testContext(t))
			for _, m := range []string{"m1", "m2", "m3"} {
				require.NoError(t, stream.Send(wrapperspb.String(m)))
			}
			resp, err := stream.CloseAndReceive()
			require.NoError(t, err)
			require.Equal(t, "m1,m2,m3", resp.Msg.GetValue(), "the server receives every message unchanged")

			requireFirstEnvelopeSigned(t, srv, procClientStream, p.compressed)
		})
	}
}

func TestStream_ServerStreamSignsFirstEnvelope(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			stream, err := client.serverStream.CallServerStream(testContext(t), connect.NewRequest(wrapperspb.String("hello")))
			require.NoError(t, err)
			var got []string
			for stream.Receive() {
				got = append(got, stream.Msg().GetValue())
			}
			require.NoError(t, stream.Err())
			require.NoError(t, stream.Close())
			require.Equal(t, []string{"hello", "hello", "hello"}, got)

			requireFirstEnvelopeSigned(t, srv, procServerStream, p.compressed)
		})
	}
}

// The request must reach the server as soon as the first message is signed. A transport that
// buffers the body sends nothing until CloseAndReceive, and this test times out waiting.
func TestStream_ClientStreamIsNotBuffered(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			stream := client.clientStream.CallClientStream(testContext(t))
			require.NoError(t, stream.Send(wrapperspb.String("m1")))
			require.NoError(t, stream.Send(wrapperspb.String("m2")))
			for _, want := range []string{"m1", "m2"} {
				select {
				case got := <-srv.received:
					require.Equal(t, want, got)
				case <-time.After(3 * time.Second):
					t.Fatalf("the server did not get %s before the stream ended: the request body is buffered", want)
				}
			}
			require.NoError(t, stream.Send(wrapperspb.String("m3")))
			resp, err := stream.CloseAndReceive()
			require.NoError(t, err)
			require.Equal(t, "m1,m2,m3", resp.Msg.GetValue())
		})
	}
}

// A first message larger than one HTTP/2 DATA frame (16 KiB).
func TestStream_LargeFirstMessage(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			large := strings.Repeat("0123456789abcdef", 4096) // 64 KiB
			stream := client.clientStream.CallClientStream(testContext(t))
			require.NoError(t, stream.Send(wrapperspb.String(large)))
			require.NoError(t, stream.Send(wrapperspb.String("tail")))
			resp, err := stream.CloseAndReceive()
			require.NoError(t, err)
			require.Equal(t, large+",tail", resp.Msg.GetValue())

			requireFirstEnvelopeSigned(t, srv, procClientStream, p.compressed)
		})
	}
}

// With no first message there is nothing to sign. The client refuses the call with
// invalid_argument instead of sending it.
func TestStream_EmptyClientStreamIsInvalidArgument(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			stream := client.clientStream.CallClientStream(testContext(t))
			_, err := stream.CloseAndReceive()
			require.Error(t, err)
			require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err), "error: %v", err)

			accepted, rejected := srv.results()
			require.Empty(t, accepted, "the request must not be sent")
			require.Empty(t, rejected, "the request must not be sent")
		})
	}
}

// A generated client uses one http.Client for all its methods: unary calls on it keep whole-body
// signing, which for gRPC is the single envelope.
func TestStream_UnaryOnSameClient(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...))

			resp, err := client.unary.CallUnary(testContext(t), connect.NewRequest(wrapperspb.String("ping")))
			require.NoError(t, err)
			require.Equal(t, "ping", resp.Msg.GetValue())

			accepted, rejected := srv.results()
			require.Empty(t, rejected)
			require.Len(t, accepted, 1)
			if strings.HasPrefix(p.name, "grpc") {
				require.Equal(t, "envelope", accepted[0].framing)
			} else {
				require.Equal(t, "body", accepted[0].framing)
			}
		})
	}
}

// errReadCloser fails every read with err and records Close.
type errReadCloser struct {
	err    error
	closed bool
}

func (b *errReadCloser) Read([]byte) (int, error) { return 0, b.err }
func (b *errReadCloser) Close() error             { b.closed = true; return nil }

func newStreamRequest(t *testing.T, ctx context.Context, body io.Reader) *http.Request {
	t.Helper()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "http://localhost"+procClientStream, body)
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/connect+proto")
	return req
}

func unexpectedRoundTrip(t *testing.T) http.RoundTripper {
	return roundTripperFunc(func(*http.Request) (*http.Response, error) {
		t.Error("the request must not be sent")
		return nil, errors.New("unexpected")
	})
}

func TestSigningTransport_FirstEnvelopeReadErrorKeepsCode(t *testing.T) {
	body := &errReadCloser{err: connect.NewError(connect.CodePermissionDenied, errors.New("caller refused"))}
	st := NewSigningTransport(newTestKey(t).sign, time.Now, WithTransport(unexpectedRoundTrip(t)))

	_, err := st.RoundTrip(newStreamRequest(t, context.Background(), body))
	require.Error(t, err)
	require.Equal(t, connect.CodePermissionDenied, connect.CodeOf(err))
	require.True(t, body.closed, "the body must be closed when RoundTrip fails")
}

func TestSigningTransport_TruncatedFirstEnvelope(t *testing.T) {
	for name, body := range map[string][]byte{
		"empty":           nil,
		"partial prefix":  {0, 0, 0},
		"partial payload": {0, 0, 0, 0, 9, 0x0a, 0x07},
		"missing payload": {0, 0, 0, 0, 9},
		"http.NoBody":     nil,
		"nil body":        nil,
	} {
		t.Run(name, func(t *testing.T) {
			var reader io.Reader = bytes.NewReader(body)
			if name == "http.NoBody" {
				reader = http.NoBody
			}
			st := NewSigningTransport(newTestKey(t).sign, time.Now, WithTransport(unexpectedRoundTrip(t)))
			req := newStreamRequest(t, context.Background(), reader)
			if name == "nil body" {
				req.Body = nil
			}

			_, err := st.RoundTrip(req)
			require.Error(t, err)
			require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err), "error: %v", err)
			require.False(t, errors.Is(err, io.EOF), "connect-go rewrites errors that wrap io.EOF")
		})
	}
}

func TestSigningTransport_FirstEnvelopeDoesNotModifyRequest(t *testing.T) {
	var sent *http.Request
	recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		sent = r
		return &http.Response{StatusCode: http.StatusOK, Body: http.NoBody}, nil
	})
	st := NewSigningTransport(newTestKey(t).sign, time.Now, WithTransport(recorder))

	first := []byte{0, 0, 0, 0, 2, 0x0a, 0x00}
	rest := []byte{0, 0, 0, 0, 2, 0x0a, 0x00}
	body := io.NopCloser(bytes.NewReader(append(append([]byte{}, first...), rest...)))
	req := newStreamRequest(t, context.Background(), body)
	req.Body = body
	req.ContentLength = -1

	resp, err := st.RoundTrip(req)
	require.NoError(t, err)
	resp.Body.Close()

	require.Empty(t, req.Header.Get(common.SignatureHeader), "the caller's headers must not change")
	require.True(t, req.Body == body, "the caller's body must not change")
	require.NotSame(t, req, sent)
	require.NotEmpty(t, sent.Header.Get(common.SignatureHeader))
	require.Equal(t, int64(-1), sent.ContentLength)
	forwarded, err := io.ReadAll(sent.Body)
	require.NoError(t, err)
	require.Equal(t, append(first, rest...), forwarded, "the body is forwarded unchanged")
}

// A client stream waits for its first message; the call's context ends the wait.
func TestSigningTransport_ContextEndsWaitForFirstMessage(t *testing.T) {
	pr, pw := io.Pipe()
	ctx, cancel := context.WithCancel(context.Background())
	st := NewSigningTransport(newTestKey(t).sign, time.Now, WithTransport(unexpectedRoundTrip(t)))

	done := make(chan error, 1)
	go func() {
		_, err := st.RoundTrip(newStreamRequest(t, ctx, pr))
		done <- err
	}()
	cancel()

	select {
	case err := <-done:
		require.ErrorIs(t, err, context.Canceled)
	case <-time.After(5 * time.Second):
		t.Fatal("RoundTrip kept waiting after the context was cancelled")
	}
	_, err := pw.Write([]byte{0})
	require.ErrorIs(t, err, io.ErrClosedPipe, "the body must be closed")
}

func TestSigningTransport_TimestampTakenAfterFirstMessage(t *testing.T) {
	pr, pw := io.Pipe()
	var sent atomic.Bool
	clock := func() time.Time {
		require.True(t, sent.Load(), "signed before the first message arrived")
		return time.Now()
	}
	recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: http.StatusOK, Body: http.NoBody}, nil
	})
	st := NewSigningTransport(newTestKey(t).sign, clock, WithTransport(recorder))

	done := make(chan error, 1)
	go func() {
		resp, err := st.RoundTrip(newStreamRequest(t, context.Background(), pr))
		if err == nil {
			resp.Body.Close()
		}
		done <- err
	}()
	time.Sleep(50 * time.Millisecond)
	sent.Store(true)
	_, err := pw.Write([]byte{0, 0, 0, 0, 0})
	require.NoError(t, err)
	require.NoError(t, <-done)
}

func TestStream_Timeouts(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			key := newTestKey(t)
			srv := newStreamTestServer(t, key.publicKey)
			srv.unaryDelay = 500 * time.Millisecond
			srv.serverStreamGap = 150 * time.Millisecond // three messages: 300 ms

			t.Run("unary timeout applies to unary calls", func(t *testing.T) {
				client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...), WithTimeout(100*time.Millisecond))
				_, err := client.unary.CallUnary(testContext(t), connect.NewRequest(wrapperspb.String("ping")))
				require.Equal(t, connect.CodeDeadlineExceeded, connect.CodeOf(err), "error: %v", err)
			})

			t.Run("streams have no timeout by default", func(t *testing.T) {
				client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...), WithTimeout(100*time.Millisecond))
				got, err := receiveAll(testContext(t), client, "hello")
				require.NoError(t, err)
				require.Len(t, got, 3)
			})

			t.Run("stream timeout applies to streams", func(t *testing.T) {
				client := newStreamClient(t, srv, key, WithConnectOptions(p.opts...), WithStreamTimeout(100*time.Millisecond))
				_, err := receiveAll(testContext(t), client, "hello")
				require.Equal(t, connect.CodeDeadlineExceeded, connect.CodeOf(err), "error: %v", err)
			})
		})
	}
}

func receiveAll(ctx context.Context, client *streamTestClient, value string) ([]string, error) {
	stream, err := client.serverStream.CallServerStream(ctx, connect.NewRequest(wrapperspb.String(value)))
	if err != nil {
		return nil, err
	}
	defer stream.Close()
	var got []string
	for stream.Receive() {
		got = append(got, stream.Msg().GetValue())
	}
	return got, stream.Err()
}

// Requests that reach the transport without the stream type (a SigningTransport used without
// NewServiceClient) fall back to the content type.
func TestIsStreamingCall_Fallback(t *testing.T) {
	for _, tc := range []struct {
		contentType   string
		contentLength int64
		want          bool
	}{
		{"application/proto", 10, false},
		{"application/json", 10, false},
		{"application/connect+proto", 10, true},
		{"application/connect+json", -1, true},
		{"application/grpc", -1, true},
		{"application/grpc+proto", -1, true},
		{"application/grpc+proto", 10, false},
		{"application/grpc-web+proto", -1, false},
		{"", 0, false},
	} {
		t.Run(fmt.Sprintf("%s/%d", tc.contentType, tc.contentLength), func(t *testing.T) {
			req := newStreamRequest(t, context.Background(), http.NoBody)
			req.Header.Set("Content-Type", tc.contentType)
			req.ContentLength = tc.contentLength
			require.Equal(t, tc.want, isStreamingCall(req))
		})
	}
}

func TestIsEnveloped(t *testing.T) {
	for contentType, want := range map[string]bool{
		"application/connect+proto":      true,
		"application/connect+json":       true,
		"application/grpc":               true,
		"application/grpc+proto":         true,
		"Application/GRPC+proto; x=y":    true,
		"application/grpc-web":           false,
		"application/grpc-web+proto":     false,
		"application/grpc-web-text":      false,
		"application/proto":              false,
		"application/json; charset=utf8": false,
		"":                               false,
	} {
		req := newStreamRequest(t, context.Background(), http.NoBody)
		req.Header.Set("Content-Type", contentType)
		require.Equal(t, want, isEnveloped(req), contentType)
	}
}

type streamSigningCase struct {
	Name              string `json:"name"`
	ContentType       string `json:"content_type"`
	BodyHex           string `json:"body_hex"`
	Covers            string `json:"covers"`
	SignedHex         string `json:"signed_hex"`
	TimestampMs       int64  `json:"timestamp_ms"`
	ExpectedSignature string `json:"expected_signature"`
}

// The transport produces the shared vectors' signatures byte for byte, and forwards the body
// unchanged.
func TestSigningTransport_StreamSigningVectors(t *testing.T) {
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err)
	var vectors struct {
		Keys struct {
			PrivateKey string `json:"private_key"`
		} `json:"keys"`
		StreamSigningCases []streamSigningCase `json:"stream_signing_cases"`
	}
	require.NoError(t, json.Unmarshal(data, &vectors))
	require.NotEmpty(t, vectors.StreamSigningCases)

	sign, err := crypto.NewSignerFromHex(vectors.Keys.PrivateKey)
	require.NoError(t, err)

	for _, tc := range vectors.StreamSigningCases {
		t.Run(tc.Name, func(t *testing.T) {
			if tc.Covers != "first_envelope" {
				t.Skip("the Go client signs below the gRPC framer: it never produces " + tc.Covers)
			}
			body, err := hex.DecodeString(tc.BodyHex)
			require.NoError(t, err)

			var sent *http.Request
			var forwarded []byte
			recorder := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
				sent = r
				forwarded, err = io.ReadAll(r.Body)
				require.NoError(t, err)
				return &http.Response{StatusCode: http.StatusOK, Body: http.NoBody}, nil
			})
			clock := func() time.Time { return time.UnixMilli(tc.TimestampMs) }
			st := NewSigningTransport(sign, clock, WithTransport(recorder))

			req := newStreamRequest(t, context.Background(), bytes.NewReader(body))
			req.Header.Set("Content-Type", tc.ContentType)
			resp, err := st.RoundTrip(req)
			if len(body) == 0 {
				require.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err), "Go refuses an empty stream locally")
				return
			}
			require.NoError(t, err)
			resp.Body.Close()

			signature := strings.TrimPrefix(sent.Header.Get(common.SignatureHeader), "0x")
			require.Equal(t, tc.ExpectedSignature, signature[:128])
			require.Equal(t, strconv.FormatInt(tc.TimestampMs, 10), sent.Header.Get(common.SignatureTimestampHeader))
			require.Equal(t, body, forwarded)
		})
	}
}
