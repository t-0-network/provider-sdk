package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"strings"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/provider"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

// test.v1.StreamTest (cross_test/stream_test.proto), built by hand on google.protobuf.StringValue.
const (
	streamTestPrefix       = "/test.v1.StreamTest/"
	streamTestClientStream = streamTestPrefix + "ClientStream"
	streamTestServerStream = streamTestPrefix + "ServerStream"
	serverStreamReplies    = 3
)

type streamTest struct{}

// newStreamTestHandler serves test.v1.StreamTest. Built with provider.Handler, it is behind the
// SDK's signature verification, which checks a stream over its first envelope as the network does.
// It passes opts to both connect handlers, and the opts carry the interceptor that fails a rejected
// stream before its handler runs. The http.HandlerFunc it returns runs for a rejected request too,
// so it can log the verdict. See cross_test/README.md.
func newStreamTestHandler(svc streamTest, opts ...connect.HandlerOption) (string, http.Handler) {
	mux := http.NewServeMux()
	mux.Handle(streamTestClientStream, connect.NewClientStreamHandler(streamTestClientStream, svc.clientStream, opts...))
	mux.Handle(streamTestServerStream, connect.NewServerStreamHandler(streamTestServerStream, svc.serverStream, opts...))

	return streamTestPrefix, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// The streaming cross tests wait for these log lines to see a request go out with its first
		// message, and check that nothing was sent: keep their wording. Every path under the prefix
		// is logged, so a request to a procedure that does not exist marks the log too.
		switch part, err := provider.SignatureVerification(r.Context()); {
		case err != nil:
			log.Printf("%s rejected: %s", r.URL.Path, connectMessage(err))
		case part == provider.SignedBody: // a unary content type, which connect-go refuses with 415
			log.Printf("%s verified over the body", r.URL.Path)
		default:
			log.Printf("%s verified over the first %s", r.URL.Path, part)
		}
		mux.ServeHTTP(w, r)
	})
}

// clientStream replies "<part>:" and the received values joined by ",".
func (streamTest) clientStream(ctx context.Context, stream *connect.ClientStream[wrapperspb.StringValue]) (*connect.Response[wrapperspb.StringValue], error) {
	var got []string
	for stream.Receive() {
		got = append(got, stream.Msg().GetValue())
	}
	if err := stream.Err(); err != nil {
		return nil, err
	}
	log.Printf("ClientStream received %d messages", len(got))
	return connect.NewResponse(wrapperspb.String(signedPart(ctx) + ":" + strings.Join(got, ","))), nil
}

// serverStream replies "<part>:" and the request value, three times.
func (streamTest) serverStream(ctx context.Context, req *connect.Request[wrapperspb.StringValue], stream *connect.ServerStream[wrapperspb.StringValue]) error {
	for range serverStreamReplies {
		if err := stream.Send(wrapperspb.String(signedPart(ctx) + ":" + req.Msg.GetValue())); err != nil {
			return err
		}
	}
	return nil
}

// signedPart is what the signature was verified over: "envelope", or "payload" for gRPC signed
// above the framer (Java). The handlers run only for a verified request.
func signedPart(ctx context.Context) string {
	part, _ := provider.SignatureVerification(ctx)
	return string(part)
}

func connectMessage(err error) string {
	if connectErr, ok := errors.AsType[*connect.Error](err); ok {
		return connectErr.Message()
	}
	return err.Error()
}
