package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"net/url"
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
// It passes opts to both connect handlers, as a factory for provider.Handler must. A rejected
// request never reaches it: the SDK answers it, and logRejections logs it. See cross_test/README.md.
func newStreamTestHandler(svc streamTest, opts ...connect.HandlerOption) (string, http.Handler) {
	mux := http.NewServeMux()
	mux.Handle(streamTestClientStream, connect.NewClientStreamHandler(streamTestClientStream, svc.clientStream, opts...))
	mux.Handle(streamTestServerStream, connect.NewServerStreamHandler(streamTestServerStream, svc.serverStream, opts...))

	return streamTestPrefix, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// The streaming cross tests wait for these log lines to see a request go out with its first
		// message, and check that nothing was sent: keep their wording. The SDK answers a rejected
		// request itself, so this runs for verified requests only; logRejections logs the rest.
		if ran, ok := r.Context().Value(handlerRanKey{}).(*bool); ok {
			*ran = true
		}
		switch part, _ := provider.SignatureVerification(r.Context()); part {
		case provider.SignedBody: // a unary content type, which connect-go refuses with 415
			log.Printf("%s verified over the body", r.URL.Path)
		default:
			log.Printf("%s verified over the first %s", r.URL.Path, part)
		}
		mux.ServeHTTP(w, r)
	})
}

type handlerRanKey struct{}

// logRejections logs "<path> rejected: <reason>" for every request under the StreamTest prefix that
// the SDK rejected. The SDK writes the rejection itself, so the stream handler never runs for it;
// the reason is read back from the error the SDK wrote. Every path under the prefix is logged, so a
// request to a procedure that does not exist marks the log too.
func logRejections(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, streamTestPrefix) {
			next.ServeHTTP(w, r)
			return
		}
		ran := false
		rec := &errorRecorder{ResponseWriter: w}
		next.ServeHTTP(rec, r.WithContext(context.WithValue(r.Context(), handlerRanKey{}, &ran)))
		if !ran {
			log.Printf("%s rejected: %s", r.URL.Path, rec.message())
		}
	})
}

// errorRecorder passes a response through and keeps the start of its body, so the error message
// can be read back.
type errorRecorder struct {
	http.ResponseWriter
	body []byte
}

func (e *errorRecorder) Write(p []byte) (int, error) {
	if room := 64*1024 - len(e.body); room > 0 {
		e.body = append(e.body, p[:min(room, len(p))]...)
	}
	return e.ResponseWriter.Write(p)
}

func (e *errorRecorder) Flush() {
	if f, ok := e.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

func (e *errorRecorder) Unwrap() http.ResponseWriter { return e.ResponseWriter }

// message is the error message of a gRPC (grpc-message), Connect streaming (end-stream message) or
// Connect unary (JSON body) error response.
func (e *errorRecorder) message() string {
	if m := e.Header().Get("Grpc-Message"); m != "" {
		if decoded, err := url.PathUnescape(m); err == nil {
			return decoded
		}
		return m
	}
	body := e.body
	if len(body) >= 5 && body[0]&0x02 != 0 {
		var endStream struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		if json.Unmarshal(body[5:], &endStream) == nil {
			return endStream.Error.Message
		}
	}
	var unary struct {
		Message string `json:"message"`
	}
	if json.Unmarshal(body, &unary) == nil {
		return unary.Message
	}
	return string(body)
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
