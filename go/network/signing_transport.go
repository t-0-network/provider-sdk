package network

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

// SigningTransportOption configures a SigningTransport.
type SigningTransportOption func(*SigningTransport)

// WithTransport overrides the underlying RoundTripper.
// Default: http.DefaultTransport.
func WithTransport(rt http.RoundTripper) SigningTransportOption {
	return func(st *SigningTransport) { st.transport = rt }
}

// WithCallTimeouts bounds each call the transport carries, from sending the request (for a client
// stream: from waiting for its first message) to reading the end of the response. unary applies to
// unary calls, stream to client-streaming and server-streaming calls; zero means no timeout.
// Default: no timeouts.
func WithCallTimeouts(unary, stream time.Duration) SigningTransportOption {
	return func(st *SigningTransport) { st.unaryTimeout, st.streamTimeout = unary, stream }
}

func NewSigningTransport(signFn crypto.SignFn, timeNow func() time.Time, opts ...SigningTransportOption) *SigningTransport {
	st := &SigningTransport{
		transport: http.DefaultTransport,
		sign:      signFn,
		timeNow:   timeNow,
	}
	for _, o := range opts {
		o(st)
	}
	if st.transport == nil {
		st.transport = http.DefaultTransport
	}
	return st
}

// SigningTransport is an HTTP transport that signs requests with a given signing function and adds
// the signature, public key and timestamp headers before forwarding the request to the underlying
// transport.
//
// What it signs depends on the request's content type:
//
//   - Enveloped requests (Connect streaming, application/connect+*, and gRPC, application/grpc
//     and application/grpc+*): only the first envelope, exactly as sent — flags, 4-byte length and
//     payload. The transport reads that envelope, signs it, and sends the request at once; later
//     messages are streamed as they come and are not covered by the signature. gRPC unary requests
//     are a single envelope, so for them this is the whole body.
//   - Everything else (Connect unary, application/proto and application/json): the whole body.
//
// For a client-streaming call the request is sent only when the first message is available, so
// send a message (or close the stream) before waiting for a response. Bidirectional streams are
// not supported.
type SigningTransport struct {
	transport     http.RoundTripper
	sign          crypto.SignFn
	timeNow       func() time.Time
	unaryTimeout  time.Duration
	streamTimeout time.Duration
}

func (t *SigningTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	timeout := t.unaryTimeout
	if isStreamingCall(req) {
		timeout = t.streamTimeout
	}
	if timeout <= 0 {
		return t.signAndSend(req)
	}

	// The same mechanics as http.Client.Timeout: the deadline covers reading the response body,
	// and closing the body releases it.
	ctx, cancel := context.WithTimeout(req.Context(), timeout)
	resp, err := t.signAndSend(req.WithContext(ctx))
	if err != nil {
		cancel()
		return nil, err
	}
	resp.Body = &cancelOnCloseBody{ReadCloser: resp.Body, cancel: cancel}
	return resp, nil
}

func (t *SigningTransport) signAndSend(req *http.Request) (*http.Response, error) {
	if isEnveloped(req) {
		return t.signFirstEnvelope(req)
	}
	return t.signWholeBody(req)
}

func (t *SigningTransport) signWholeBody(req *http.Request) (*http.Response, error) {
	var body []byte
	if req.Body != nil && req.Body != http.NoBody {
		var err error
		body, err = io.ReadAll(req.Body)
		if err != nil {
			return nil, fmt.Errorf("reading request body: %w", err)
		}
		req.Body.Close()
		req.Body = io.NopCloser(bytes.NewReader(body))
	}

	if err := t.setSignatureHeaders(req.Header, body); err != nil {
		return nil, err
	}

	return t.transport.RoundTrip(req)
}

func (t *SigningTransport) signFirstEnvelope(req *http.Request) (*http.Response, error) {
	envelope, err := readFirstEnvelope(req)
	if err != nil {
		closeRequestBody(req)
		return nil, err
	}

	signed := req.Clone(req.Context())
	if err := t.setSignatureHeaders(signed.Header, envelope); err != nil {
		closeRequestBody(req)
		return nil, err
	}
	// Put the envelope back in front of the rest of the body. ContentLength and GetBody are kept:
	// the body is unchanged, and a retry replays the same bytes under the same signature.
	signed.Body = struct {
		io.Reader
		io.Closer
	}{io.MultiReader(bytes.NewReader(envelope), req.Body), req.Body}

	return t.transport.RoundTrip(signed)
}

// setSignatureHeaders signs signed with the current timestamp and sets the signature headers:
// digest = Keccak256(signed || uint64le(timestamp_ms)).
func (t *SigningTransport) setSignatureHeaders(header http.Header, signed []byte) error {
	timestamp := t.timeNow().UnixMilli()

	timestampBytes := [8]byte{}
	binary.LittleEndian.PutUint64(timestampBytes[:], uint64(timestamp))

	// The full slice expression makes append copy, so the caller's spare capacity is never written.
	digest := crypto.LegacyKeccak256(append(signed[:len(signed):len(signed)], timestampBytes[:]...))

	signature, pubKeyBytes, err := t.sign(digest)
	if err != nil {
		return fmt.Errorf("signing request body: %w", err)
	}

	header.Set(common.PublicKeyHeader, "0x"+hex.EncodeToString(pubKeyBytes))
	header.Set(common.SignatureHeader, "0x"+hex.EncodeToString(signature))
	header.Set(common.SignatureTimestampHeader, strconv.FormatInt(timestamp, 10))
	return nil
}

// readFirstEnvelope reads exactly one envelope from the request body and nothing after it. For a
// client stream the body is a pipe that connect-go fills as the caller sends, so this waits for
// the first message; the request's context ends the wait.
func readFirstEnvelope(req *http.Request) ([]byte, error) {
	if req.Body == nil || req.Body == http.NoBody {
		return nil, errNoFirstMessage()
	}

	stop := context.AfterFunc(req.Context(), func() { _ = req.Body.Close() })
	envelope, err := readEnvelope(req.Body)
	if !stop() {
		// The context ended while we waited, and the body is closed.
		return nil, req.Context().Err()
	}
	return envelope, err
}

// maxPrealloc caps the buffer allocated up front from the length in the prefix. A larger message
// grows the buffer as it arrives.
const maxPrealloc = 64 << 10

func readEnvelope(r io.Reader) ([]byte, error) {
	var prefix [5]byte
	if _, err := io.ReadFull(r, prefix[:]); err != nil {
		if errors.Is(err, io.EOF) {
			return nil, errNoFirstMessage()
		}
		return nil, firstMessageError(err)
	}

	size := int64(binary.BigEndian.Uint32(prefix[1:]))
	envelope := bytes.NewBuffer(make([]byte, 0, 5+min(size, maxPrealloc)))
	envelope.Write(prefix[:])
	if _, err := io.CopyN(envelope, r, size); err != nil {
		return nil, firstMessageError(err)
	}
	return envelope.Bytes(), nil
}

// errNoFirstMessage is returned for a stream closed before its first message, as a client
// stream is when the caller calls CloseAndReceive without Send. There is nothing to sign.
//
// These errors carry a Connect code on purpose: connect-go reports any other RoundTrip error as
// unavailable, which looks retryable. They must not wrap io.EOF either — connect-go replaces a
// RoundTrip error that wraps io.EOF with io.ErrUnexpectedEOF, and the code is lost.
func errNoFirstMessage() error {
	return connect.NewError(connect.CodeInvalidArgument,
		errors.New("streaming request has no first message to sign"))
}

func firstMessageError(err error) error {
	if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
		return connect.NewError(connect.CodeInvalidArgument,
			errors.New("streaming request ends inside its first message"))
	}
	// Keep the code of an error that already has one.
	return fmt.Errorf("reading first request message: %w", err)
}

func closeRequestBody(req *http.Request) {
	if req.Body != nil {
		_ = req.Body.Close()
	}
}

type cancelOnCloseBody struct {
	io.ReadCloser
	cancel context.CancelFunc
}

func (b *cancelOnCloseBody) Read(p []byte) (int, error) {
	n, err := b.ReadCloser.Read(p)
	if errors.Is(err, io.EOF) {
		// Trailers are populated by now.
		b.cancel()
	}
	return n, err
}

func (b *cancelOnCloseBody) Close() error {
	err := b.ReadCloser.Close()
	b.cancel()
	return err
}

func mediaType(req *http.Request) string {
	ct := req.Header.Get("Content-Type")
	if i := strings.IndexByte(ct, ';'); i >= 0 {
		ct = ct[:i]
	}
	return strings.ToLower(strings.TrimSpace(ct))
}

func isGRPCMediaType(mt string) bool {
	// Not gRPC-Web: application/grpc-web and friends.
	return mt == "application/grpc" || strings.HasPrefix(mt, "application/grpc+")
}

// isEnveloped reports whether the request body is a sequence of enveloped messages, in which case
// the signature covers only the first envelope.
func isEnveloped(req *http.Request) bool {
	mt := mediaType(req)
	return strings.HasPrefix(mt, "application/connect+") || isGRPCMediaType(mt)
}

// isStreamingCall reports whether req belongs to a client- or server-streaming call, to pick its
// timeout. The client built by NewServiceClient tags every call's context with its stream type,
// because gRPC unary and gRPC server-streaming requests look alike on the wire. Untagged requests
// fall back to the content type: Connect tells unary and streaming apart, and a gRPC request of
// unknown length is a client stream.
func isStreamingCall(req *http.Request) bool {
	if streamType, ok := req.Context().Value(streamTypeKey{}).(connect.StreamType); ok {
		return streamType != connect.StreamTypeUnary
	}
	mt := mediaType(req)
	switch {
	case strings.HasPrefix(mt, "application/connect+"):
		return true
	case isGRPCMediaType(mt):
		return req.ContentLength < 0
	default:
		return false
	}
}

type streamTypeKey struct{}

// streamTypeInterceptor tags each call's context with its stream type, which reaches
// SigningTransport through the request's context.
type streamTypeInterceptor struct{}

func (streamTypeInterceptor) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		return next(context.WithValue(ctx, streamTypeKey{}, connect.StreamTypeUnary), req)
	}
}

func (streamTypeInterceptor) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return func(ctx context.Context, spec connect.Spec) connect.StreamingClientConn {
		return next(context.WithValue(ctx, streamTypeKey{}, spec.StreamType), spec)
	}
}

func (streamTypeInterceptor) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return next
}
