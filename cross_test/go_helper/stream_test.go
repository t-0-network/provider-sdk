package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"connectrpc.com/connect"
	sdkcommon "github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

// The key pair of cross_test/test_vectors.json, which the other SDKs' cross tests use too.
const (
	clientPrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
	clientPublicKey  = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
	otherPrivateKey  = "0x0000000000000000000000000000000000000000000000000000000000000001"
)

func payloadOf(t *testing.T, value string) []byte {
	t.Helper()
	b, err := proto.Marshal(wrapperspb.String(value))
	if err != nil {
		t.Fatal(err)
	}
	return b
}

func envelopeOf(payload []byte) []byte {
	prefix := make([]byte, 5)
	binary.BigEndian.PutUint32(prefix[1:], uint32(len(payload)))
	return append(prefix, payload...)
}

func concat(parts ...[]byte) []byte {
	return bytes.Join(parts, nil)
}

// signatureHeaders signs bytes the way every SDK does: Keccak256(bytes || uint64le(ts_ms)).
func signatureHeaders(t *testing.T, privateKey string, signed []byte, ts time.Time) http.Header {
	t.Helper()
	sign, err := crypto.NewSignerFromHex(privateKey)
	if err != nil {
		t.Fatal(err)
	}
	var tsBytes [8]byte
	binary.LittleEndian.PutUint64(tsBytes[:], uint64(ts.UnixMilli()))
	signature, publicKey, err := sign(crypto.LegacyKeccak256(concat(signed, tsBytes[:])))
	if err != nil {
		t.Fatal(err)
	}
	h := http.Header{}
	h.Set(sdkcommon.PublicKeyHeader, "0x"+hex.EncodeToString(publicKey))
	h.Set(sdkcommon.SignatureHeader, "0x"+hex.EncodeToString(signature))
	h.Set(sdkcommon.SignatureTimestampHeader, strconv.FormatInt(ts.UnixMilli(), 10))
	return h
}

// TestVerifyFirstEnvelope pins the verifier every SDK's streaming cross tests run against: it
// accepts a signature over the first envelope (and, for gRPC only, over its payload without the
// prefix), rejects everything else, and hands the handler the whole body.
func TestVerifyFirstEnvelope(t *testing.T) {
	publicKey, err := crypto.GetPublicKeyFromHex(clientPublicKey)
	if err != nil {
		t.Fatal(err)
	}
	trustedKey := crypto.GetPublicKeyBytes(publicKey)

	p1, p2 := payloadOf(t, "m1"), payloadOf(t, "m2")
	env1, env2 := envelopeOf(p1), envelopeOf(p2)
	body := concat(env1, env2)
	// The verifier reads envelopes, whatever the codec: Connect JSON streams are signed alike.
	jsonEnv1 := envelopeOf([]byte(`"m1"`))
	jsonBody := concat(jsonEnv1, envelopeOf([]byte(`"m2"`)))
	now := time.Now()

	cases := []struct {
		name        string
		contentType string
		body        []byte
		headers     http.Header
		framing     string // accepted over this, or rejected when empty
		reason      string // part of the rejection
	}{
		{"connect, first envelope", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, env1, now), "envelope", ""},
		{"connect+json, first envelope", "application/connect+json", jsonBody, signatureHeaders(t, clientPrivateKey, jsonEnv1, now), "envelope", ""},
		{"connect+json, whole body", "application/connect+json", jsonBody, signatureHeaders(t, clientPrivateKey, jsonBody, now), "", "signature does not verify over the first message"},
		{"grpc, first envelope", "application/grpc", body, signatureHeaders(t, clientPrivateKey, env1, now), "envelope", ""},
		{"grpc+proto, first payload", "application/grpc+proto", body, signatureHeaders(t, clientPrivateKey, p1, now), "payload", ""},
		{"connect, first payload", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, p1, now), "", "signature does not verify over the first message"},
		{"whole body", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, body, now), "", "signature does not verify over the first message"},
		{"grpc, whole body", "application/grpc+proto", body, signatureHeaders(t, clientPrivateKey, body, now), "", "signature does not verify over the first message"},
		{"second envelope", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, env2, now), "", "signature does not verify over the first message"},
		{"unsigned", "application/connect+proto", body, http.Header{}, "", "unknown public key"},
		{"other key", "application/connect+proto", body, signatureHeaders(t, otherPrivateKey, env1, now), "", "unknown public key"},
		{"stale timestamp", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, env1, now.Add(-2*time.Minute)), "", "timestamp is outside the allowed time window"},
		{"future timestamp", "application/connect+proto", body, signatureHeaders(t, clientPrivateKey, env1, now.Add(2*time.Minute)), "", "timestamp is outside the allowed time window"},
		{"no first message", "application/connect+proto", nil, signatureHeaders(t, clientPrivateKey, nil, now), "", "no first message"},
		{"truncated first message", "application/connect+proto", env1[:len(env1)-1], signatureHeaders(t, clientPrivateKey, env1, now), "", "truncated first message"},
		{"unary content type", "application/proto", p1, signatureHeaders(t, clientPrivateKey, p1, now), "", "is not a streaming content type"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest(http.MethodPost, streamTestClientStream, bytes.NewReader(tc.body))
			for name, values := range tc.headers {
				r.Header[name] = values
			}
			r.Header.Set("Content-Type", tc.contentType)

			framing, err := verifyFirstEnvelope(r, trustedKey)
			if tc.framing == "" {
				if err == nil || !strings.Contains(err.Error(), tc.reason) {
					t.Fatalf("want a rejection with %q, got framing %q, err %v", tc.reason, framing, err)
				}
				return
			}
			if err != nil {
				t.Fatalf("want it verified over the first %s, got %v", tc.framing, err)
			}
			if framing != tc.framing {
				t.Fatalf("verified over the first %s, want %s", framing, tc.framing)
			}
			// The handler still reads the whole body, first envelope included.
			rest, err := io.ReadAll(r.Body)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(rest, tc.body) {
				t.Fatalf("the handler reads %x, want the body %x", rest, tc.body)
			}
		})
	}
}

// syncBuffer collects the helper's log, which its handlers write from other goroutines.
type syncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (b *syncBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

func (b *syncBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// serveHelper serves what `go_helper serve` serves and captures its log.
func serveHelper(t *testing.T) (string, *syncBuffer) {
	t.Helper()
	logs := &syncBuffer{}
	log.SetOutput(logs)
	t.Cleanup(func() { log.SetOutput(os.Stderr) })

	handler, err := newServeHandler(clientPublicKey)
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	return srv.URL, logs
}

// The Go SDK client against the helper's verifier, over the protocols `call-client-stream` and
// `call-server-stream` use, Connect over HTTP/1.1 and gRPC over h2c, and over Connect JSON.
var helperProtocols = []struct {
	name string
	opts []network.ClientOption
}{
	{"connect", nil},
	{"connect-json", []network.ClientOption{network.WithConnectOptions(connect.WithProtoJSON())}},
	{"grpc", []network.ClientOption{
		network.WithConnectOptions(connect.WithGRPC()),
		network.WithHTTPTransport(newH2CTransport()),
	}},
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

func TestGoClientAgainstHelper(t *testing.T) {
	for _, p := range helperProtocols {
		t.Run(p.name, func(t *testing.T) {
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()

			t.Run("client stream", func(t *testing.T) {
				url, logs := serveHelper(t)
				stream := newHelperClient(t, url, clientPrivateKey, p.opts).clientStream.CallClientStream(ctx)
				for _, m := range []string{"m1", "m2", "m3"} {
					if err := stream.Send(wrapperspb.String(m)); err != nil {
						t.Fatal(err)
					}
				}
				resp, err := stream.CloseAndReceive()
				if err != nil {
					t.Fatal(err)
				}
				if got := resp.Msg.GetValue(); got != "m1,m2,m3" {
					t.Fatalf("got %q, want m1,m2,m3", got)
				}
				// The Go SDK signs below the framer, over the first envelope, for gRPC too.
				if want := streamTestClientStream + " verified over the first envelope"; !strings.Contains(logs.String(), want) {
					t.Fatalf("log has no %q:\n%s", want, logs)
				}
			})

			t.Run("server stream", func(t *testing.T) {
				url, logs := serveHelper(t)
				stream, err := newHelperClient(t, url, clientPrivateKey, p.opts).serverStream.CallServerStream(ctx, connect.NewRequest(wrapperspb.String("hello")))
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
				if strings.Join(got, ",") != "hello,hello,hello" {
					t.Fatalf("got %q, want hello three times", got)
				}
				if want := streamTestServerStream + " verified over the first envelope"; !strings.Contains(logs.String(), want) {
					t.Fatalf("log has no %q:\n%s", want, logs)
				}
			})

			t.Run("empty client stream is rejected", func(t *testing.T) {
				url, logs := serveHelper(t)
				stream := newHelperClient(t, url, clientPrivateKey, p.opts).clientStream.CallClientStream(ctx)
				_, err := stream.CloseAndReceive()
				if code := connect.CodeOf(err); code != connect.CodeUnauthenticated {
					t.Fatalf("got %v (%v), want unauthenticated", code, err)
				}
				if want := streamTestClientStream + " rejected: no first message"; !strings.Contains(logs.String(), want) {
					t.Fatalf("log has no %q:\n%s", want, logs)
				}
			})

			t.Run("other key is rejected", func(t *testing.T) {
				url, _ := serveHelper(t)
				stream := newHelperClient(t, url, otherPrivateKey, p.opts).clientStream.CallClientStream(ctx)
				_ = stream.Send(wrapperspb.String("m1"))
				_, err := stream.CloseAndReceive()
				var connectErr *connect.Error
				if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeUnauthenticated {
					t.Fatalf("got %v, want unauthenticated", err)
				}
			})
		})
	}
}
