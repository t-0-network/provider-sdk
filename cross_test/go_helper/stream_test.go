package main

import (
	"bytes"
	"context"
	"errors"
	"github.com/t-0-network/provider-sdk/go/provider"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/network"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

// The key pair of cross_test/test_vectors.json, which the other SDKs' cross tests use too.
const (
	clientPrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
	clientPublicKey  = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
)

// serveHelper serves what `go_helper serve` serves.
func serveHelper(t *testing.T) string {
	t.Helper()
	handler, err := newServeHandler(clientPublicKey)
	if err != nil {
		t.Fatal(err)
	}
	// The handler of the SDK's own server: h2c, so Connect and gRPC share the port.
	srv := httptest.NewServer(provider.NewServer(handler).Handler)
	t.Cleanup(srv.Close)
	return srv.URL
}

// helperLog collects what the helper logs, as the cross tests read it from its stderr, and the
// PASS and FAIL lines of an in-process probe. Its writers log from many goroutines, one line per
// Write.
type helperLog struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (l *helperLog) Write(p []byte) (int, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.buf.Write(p)
}

func (l *helperLog) contains(s string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	return strings.Contains(l.buf.String(), s)
}

func (l *helperLog) lines() []string {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.buf.Len() == 0 {
		return nil
	}
	return strings.Split(strings.TrimSuffix(l.buf.String(), "\n"), "\n")
}

func captureLog(t *testing.T) *helperLog {
	t.Helper()
	l := &helperLog{}
	log.SetOutput(l)
	t.Cleanup(func() { log.SetOutput(os.Stderr) })
	return l
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

var helperProtocols = []struct {
	name string
	opts []network.ClientOption
}{
	{"connect", nil},
	{"connect-json", []network.ClientOption{network.WithWireFormat(network.WireFormatJSON)}},
	{"grpc", []network.ClientOption{network.WithProtocol(network.ProtocolGRPC)}},
}

func newHelperClient(t *testing.T, url, privateKey string, opts []network.ClientOption) *streamTestClient {
	t.Helper()
	client, err := network.NewServiceClient(network.PrivateKeyHexed(privateKey), newStreamTestClient,
		append([]network.ClientOption{network.WithBaseURL(url)}, opts...)...)
	if err != nil {
		t.Fatal(err)
	}
	return client
}

// The helper's wiring, which the other SDKs' streaming cross tests rely on: the SDK's verifier in
// front of test.v1.StreamTest, the signed part in front of every reply, and the log lines. The
// verifier itself is tested in the SDK (go/provider).
func TestGoClientAgainstHelper(t *testing.T) {
	logs := captureLog(t)
	for _, p := range helperProtocols {
		t.Run(p.name, func(t *testing.T) {
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()

			t.Run("client stream", func(t *testing.T) {
				stream := newHelperClient(t, serveHelper(t), clientPrivateKey, p.opts).clientStream.CallClientStream(ctx)
				for _, m := range []string{"m1", "m2", "m3"} {
					if err := stream.Send(wrapperspb.String(m)); err != nil {
						t.Fatal(err)
					}
				}
				resp, err := stream.CloseAndReceive()
				if err != nil {
					t.Fatal(err)
				}
				// Go signs below the gRPC framer: the first envelope for gRPC too.
				if got := resp.Msg.GetValue(); got != "envelope:m1,m2,m3" {
					t.Fatalf("got %q, want envelope:m1,m2,m3", got)
				}
				if line := streamTestClientStream + " verified over the first envelope"; !logs.contains(line) {
					t.Fatalf("%q not logged", line)
				}
			})

			t.Run("server stream", func(t *testing.T) {
				stream, err := newHelperClient(t, serveHelper(t), clientPrivateKey, p.opts).serverStream.CallServerStream(ctx, connect.NewRequest(wrapperspb.String("hello")))
				if err != nil {
					t.Fatal(err)
				}
				defer stream.Close()
				var got []string
				for stream.Receive() {
					got = append(got, stream.Msg().GetValue())
				}
				if err := stream.Err(); err != nil {
					t.Fatal(err)
				}
				if strings.Join(got, ",") != "envelope:hello,envelope:hello,envelope:hello" {
					t.Fatalf("got %q, want envelope:hello three times", got)
				}
			})

			t.Run("empty client stream is rejected", func(t *testing.T) {
				stream := newHelperClient(t, serveHelper(t), clientPrivateKey, p.opts).clientStream.CallClientStream(ctx)
				_, err := stream.CloseAndReceive()
				var connectErr *connect.Error
				if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeUnauthenticated ||
					!strings.Contains(connectErr.Message(), "no first message") {
					t.Fatalf("got %v, want unauthenticated with the reason no first message", err)
				}
				if line := streamTestClientStream + " rejected: no first message"; !logs.contains(line) {
					t.Fatalf("%q not logged", line)
				}
			})
		})
	}

	// The Python cross test marks the log with unsigned requests to procedures that do not exist.
	t.Run("mark", func(t *testing.T) {
		resp, err := http.Post(serveHelper(t)+streamTestPrefix+"Mark1", "", nil)
		if err != nil {
			t.Fatal(err)
		}
		_ = resp.Body.Close()
		if line := streamTestPrefix + "Mark1 rejected: missing required header"; !logs.contains(line) {
			t.Fatalf("%q not logged", line)
		}
	})
}
