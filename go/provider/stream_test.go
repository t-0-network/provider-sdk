package provider

import (
	"bytes"
	"context"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/wrapperspb"

	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
)

// test.v1.StreamTest (cross_test/stream_test.proto), built by hand on google.protobuf.StringValue.
const (
	streamTestPrefix       = "/test.v1.StreamTest/"
	streamTestClientStream = streamTestPrefix + "ClientStream"
	streamTestServerStream = streamTestPrefix + "ServerStream"
)

// streamTest replies with the signed part and what it received, and reports every message it
// receives and every call it starts.
type streamTest struct {
	calls    atomic.Int32
	received chan string
}

func newStreamTestHandler(svc *streamTest, opts ...connect.HandlerOption) (string, http.Handler) {
	mux := http.NewServeMux()
	mux.Handle(streamTestClientStream, connect.NewClientStreamHandler(streamTestClientStream,
		func(ctx context.Context, stream *connect.ClientStream[wrapperspb.StringValue]) (*connect.Response[wrapperspb.StringValue], error) {
			svc.calls.Add(1)
			var got []string
			for stream.Receive() {
				got = append(got, stream.Msg().GetValue())
				svc.received <- stream.Msg().GetValue()
			}
			if err := stream.Err(); err != nil {
				return nil, err
			}
			return connect.NewResponse(wrapperspb.String(signedPartOf(ctx) + ":" + strings.Join(got, ","))), nil
		}, opts...))
	mux.Handle(streamTestServerStream, connect.NewServerStreamHandler(streamTestServerStream,
		func(ctx context.Context, req *connect.Request[wrapperspb.StringValue], stream *connect.ServerStream[wrapperspb.StringValue]) error {
			svc.calls.Add(1)
			for range 3 {
				if err := stream.Send(wrapperspb.String(signedPartOf(ctx) + ":" + req.Msg.GetValue())); err != nil {
					return err
				}
			}
			return nil
		}, opts...))
	return streamTestPrefix, mux
}

func signedPartOf(ctx context.Context) string {
	part, err := SignatureVerification(ctx)
	if err != nil {
		return "unverified"
	}
	return string(part)
}

type streamTestClient struct {
	clientStream *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue]
	serverStream *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue]
}

func newStreamTestClient(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) *streamTestClient {
	return &streamTestClient{
		clientStream: connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+streamTestClientStream, opts...),
		serverStream: connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+streamTestServerStream, opts...),
	}
}

type streamServer struct {
	url        string
	svc        *streamTest
	networkKey *secp256k1.PrivateKey
}

// serveStreamTest serves test.v1.StreamTest behind NewHttpHandler, Connect over HTTP/1.1 and gRPC
// over HTTP/2 without TLS on the same port.
func serveStreamTest(t *testing.T, opts ...HandlerOption) *streamServer {
	t.Helper()
	networkKey, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	svc := &streamTest{received: make(chan string, 1024)}
	mux, err := NewHttpHandler(NetworkPublicKeyHexed(crypto.HexPublicKey(networkKey.PubKey())),
		Handler(newStreamTestHandler, svc, opts...))
	require.NoError(t, err)
	srv := httptest.NewUnstartedServer(mux)
	srv.Config.Protocols = new(http.Protocols)
	srv.Config.Protocols.SetHTTP1(true)
	srv.Config.Protocols.SetUnencryptedHTTP2(true)
	srv.Start()
	t.Cleanup(srv.Close)
	return &streamServer{url: srv.URL, svc: svc, networkKey: networkKey}
}

var streamProtocols = []struct {
	name string
	opts []network.ClientOption
}{
	{"connect", nil},
	{"connect-json", []network.ClientOption{network.WithWireFormat(network.WireFormatJSON)}},
	{"grpc", []network.ClientOption{network.WithProtocol(network.ProtocolGRPC)}},
}

func (s *streamServer) client(t *testing.T, key *secp256k1.PrivateKey, opts []network.ClientOption) *streamTestClient {
	t.Helper()
	client, err := network.NewServiceClient(network.PrivateKeyHexed(hex.EncodeToString(key.Serialize())), newStreamTestClient,
		append([]network.ClientOption{network.WithBaseURL(s.url)}, opts...)...)
	require.NoError(t, err)
	return client
}

// staleClient signs with a clock two minutes behind.
func (s *streamServer) staleClient(protocol string) *streamTestClient {
	transport := http.DefaultTransport
	var opts []connect.ClientOption
	switch protocol {
	case "connect-json":
		opts = append(opts, connect.WithProtoJSON())
	case "grpc":
		h2cTransport := &http.Transport{Protocols: new(http.Protocols)}
		h2cTransport.Protocols.SetUnencryptedHTTP2(true)
		transport = h2cTransport
		opts = append(opts, connect.WithGRPC())
	}
	twoMinutesAgo := func() time.Time { return time.Now().Add(-2 * time.Minute) }
	httpClient := &http.Client{
		Transport: network.NewSigningTransport(crypto.NewSigner(s.networkKey), twoMinutesAgo, network.WithTransport(transport)),
	}
	return newStreamTestClient(httpClient, s.url, opts...)
}

func sendAll(ctx context.Context, client *streamTestClient, values ...string) (string, error) {
	stream := client.clientStream.CallClientStream(ctx)
	for _, v := range values {
		if err := stream.Send(wrapperspb.String(v)); err != nil {
			break // the error is the response's
		}
	}
	resp, err := stream.CloseAndReceive()
	if err != nil {
		return "", err
	}
	return resp.Msg.GetValue(), nil
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

func requireRefused(t *testing.T, err error, code connect.Code, reason string) {
	t.Helper()
	require.Equal(t, code, connect.CodeOf(err), "error: %v", err)
	require.ErrorContains(t, err, reason)
}

// The Go network client against streaming handlers built by Handler: each stream is verified over
// its first envelope (Go signs below the gRPC framer), its handler gets the messages as they come,
// and a rejected stream never starts its handler.
func TestStreamingHandlers(t *testing.T) {
	for _, p := range streamProtocols {
		t.Run(p.name, func(t *testing.T) {
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()

			t.Run("client stream", func(t *testing.T) {
				s := serveStreamTest(t)
				got, err := sendAll(ctx, s.client(t, s.networkKey, p.opts), "m1", "m2", "m3")
				require.NoError(t, err)
				require.Equal(t, "envelope:m1,m2,m3", got)
			})

			t.Run("server stream", func(t *testing.T) {
				s := serveStreamTest(t)
				got, err := receiveAll(ctx, s.client(t, s.networkKey, p.opts), "hello")
				require.NoError(t, err)
				require.Equal(t, []string{"envelope:hello", "envelope:hello", "envelope:hello"}, got)
			})

			// Message 2 is sent only once the handler has received message 1: a server that read
			// the stream to its end before the handler would stall here.
			t.Run("not buffered", func(t *testing.T) {
				s := serveStreamTest(t)
				stream := s.client(t, s.networkKey, p.opts).clientStream.CallClientStream(ctx)
				require.NoError(t, stream.Send(wrapperspb.String("m1")))
				select {
				case got := <-s.svc.received:
					require.Equal(t, "m1", got)
				case <-ctx.Done():
					t.Fatal("the handler did not receive message 1 before message 2 was sent")
				}
				require.NoError(t, stream.Send(wrapperspb.String("m2")))
				resp, err := stream.CloseAndReceive()
				require.NoError(t, err)
				require.Equal(t, "envelope:m1,m2", resp.Msg.GetValue())
			})

			t.Run("stream over the limit as a whole", func(t *testing.T) {
				s := serveStreamTest(t, WithMaxBodySize(1024))
				chunk := strings.Repeat("x", 600)
				got, err := sendAll(ctx, s.client(t, s.networkKey, p.opts), chunk, chunk, chunk, chunk)
				require.NoError(t, err)
				require.Equal(t, "envelope:"+strings.Join([]string{chunk, chunk, chunk, chunk}, ","), got)
			})

			t.Run("first message over the limit", func(t *testing.T) {
				s := serveStreamTest(t, WithMaxBodySize(1024))
				_, err := sendAll(ctx, s.client(t, s.networkKey, p.opts), strings.Repeat("x", 2048))
				requireRefused(t, err, connect.CodeResourceExhausted, "max payload size of 1024 bytes exceeded")
				require.Zero(t, s.svc.calls.Load(), "the handler ran")
			})

			t.Run("later message over the limit", func(t *testing.T) {
				s := serveStreamTest(t, WithMaxBodySize(1024))
				_, err := sendAll(ctx, s.client(t, s.networkKey, p.opts), "m1", strings.Repeat("x", 2048))
				require.Equal(t, connect.CodeResourceExhausted, connect.CodeOf(err), "error: %v", err)
			})

			t.Run("other key", func(t *testing.T) {
				s := serveStreamTest(t)
				otherKey, err := secp256k1.GeneratePrivateKey()
				require.NoError(t, err)
				client := s.client(t, otherKey, p.opts)

				_, err = sendAll(ctx, client, "m1")
				requireRefused(t, err, connect.CodeUnauthenticated, "request signed with unknown public key")
				_, err = receiveAll(ctx, client, "hello")
				requireRefused(t, err, connect.CodeUnauthenticated, "request signed with unknown public key")
				require.Zero(t, s.svc.calls.Load(), "a handler ran")
			})

			t.Run("stale timestamp", func(t *testing.T) {
				s := serveStreamTest(t)
				client := s.staleClient(p.name)

				_, err := sendAll(ctx, client, "m1")
				requireRefused(t, err, connect.CodeInvalidArgument, "timestamp is outside the allowed time window")
				_, err = receiveAll(ctx, client, "hello")
				requireRefused(t, err, connect.CodeInvalidArgument, "timestamp is outside the allowed time window")
				require.Zero(t, s.svc.calls.Load(), "a handler ran")
			})

			t.Run("empty client stream", func(t *testing.T) {
				s := serveStreamTest(t)
				_, err := sendAll(ctx, s.client(t, s.networkKey, p.opts))
				requireRefused(t, err, connect.CodeUnauthenticated, "no first message")
				require.Zero(t, s.svc.calls.Load(), "the handler ran")
			})
		})
	}
}

// A streaming procedure called with a unary content type is refused by connect-go before any
// interceptor or handler, even when the whole body is signed.
func TestStreamingHandlers_UnaryContentType(t *testing.T) {
	s := serveStreamTest(t)
	payload, err := proto.Marshal(wrapperspb.String("m1"))
	require.NoError(t, err)

	for _, path := range []string{streamTestClientStream, streamTestServerStream} {
		req, err := http.NewRequest(http.MethodPost, s.url+path, bytes.NewReader(payload))
		require.NoError(t, err)
		req.Header = signedHeaders(t, s.networkKey, payload, time.Now())
		req.Header.Set("Content-Type", "application/proto")
		resp, err := http.DefaultClient.Do(req)
		require.NoError(t, err)
		_ = resp.Body.Close()
		require.Equal(t, http.StatusUnsupportedMediaType, resp.StatusCode, path)
	}
	require.Zero(t, s.svc.calls.Load(), "a handler ran")
}
