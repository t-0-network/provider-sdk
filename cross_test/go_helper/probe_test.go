package main

import (
	"bytes"
	"context"
	"net"
	"strconv"
	"testing"
	"time"

	"buf.build/go/protovalidate"
	"github.com/t-0-network/provider-sdk/go/provider"
	"google.golang.org/protobuf/proto"
)

// The Go column of the shared server behavior: every case of server_cases in
// cross_test/test_vectors.json, over Connect and over gRPC, against the SDK's own server.
func TestProbe_GoServer(t *testing.T) {
	v, err := loadVectors("../test_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	handler, err := newServeHandler("0x" + v.Keys.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	port := freePort(t)
	shutdown, err := provider.StartServer(handler, provider.WithAddr("127.0.0.1:"+strconv.Itoa(port)))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = shutdown(ctx)
	})

	var out bytes.Buffer
	failures := runProbe(&out, v, "http://127.0.0.1:"+strconv.Itoa(port), "go", []string{"connect", "grpc"})
	t.Log("\n" + out.String())
	if failures > 0 {
		t.Fatalf("%d case(s) failed", failures)
	}
}

func freePort(t *testing.T) int {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port
}

// The requests of the `call` cases are valid, so a server that validates requests reaches the
// handler whose response the case is about.
func TestProbe_CallRequestsValid(t *testing.T) {
	for _, msg := range []proto.Message{approvePaymentQuotesRequest, payOutRequest} {
		if err := protovalidate.Validate(msg); err != nil {
			t.Errorf("%s: %v", msg.ProtoReflect().Descriptor().FullName(), err)
		}
	}
}
