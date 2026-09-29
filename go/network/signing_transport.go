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
// send a message (or close the stream) before waiting for a response. A stream closed before its
// first message is signed over empty bytes and sent; the network rejects it. Bidirectional streams
// are not supported.
type SigningTransport struct {
	transport http.RoundTripper
	sign      crypto.SignFn
	timeNow   func() time.Time
}

func (t *SigningTransport) RoundTrip(req *http.Request) (*http.Response, error) {
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
	if len(envelope) > 0 {
		signed.Body = struct {
			io.Reader
			io.Closer
		}{io.MultiReader(bytes.NewReader(envelope), req.Body), req.Body}
	}

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
// the first message; the request's context ends the wait. A body that ends before its first
// message, as a client stream closed without Send does, has no envelope: nil is returned.
func readFirstEnvelope(req *http.Request) ([]byte, error) {
	if req.Body == nil || req.Body == http.NoBody {
		return nil, nil
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
			return nil, nil // no first message
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

// firstMessageError reports a body that ends inside its first envelope with a Connect code on
// purpose: connect-go reports any other RoundTrip error as unavailable, which looks retryable. It
// must not wrap io.EOF either — connect-go replaces a RoundTrip error that wraps io.EOF with
// io.ErrUnexpectedEOF, and the code is lost.
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

// callTimeouts gives each call the default timeout of its type as a context deadline: unary calls
// the unary timeout, client- and server-streaming calls the stream timeout; zero means none.
// connect-go enforces the deadline and sends it to the server (Connect-Timeout-Ms, grpc-timeout).
// It is an interceptor, not part of SigningTransport, because only connect-go knows a call's
// stream type: gRPC unary and gRPC server-streaming requests look alike on the wire.
type callTimeouts struct {
	unary, stream time.Duration
}

func (c callTimeouts) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	if c.unary <= 0 {
		return next
	}
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		ctx, cancel := context.WithTimeout(ctx, c.unary)
		defer cancel()
		return next(ctx, req)
	}
}

func (c callTimeouts) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	if c.stream <= 0 {
		return next
	}
	return func(ctx context.Context, spec connect.Spec) connect.StreamingClientConn {
		ctx, cancel := context.WithTimeout(ctx, c.stream)
		return &cancelOnCloseConn{StreamingClientConn: next(ctx, spec), cancel: cancel}
	}
}

func (callTimeouts) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return next
}

// cancelOnCloseConn releases a stream's deadline when the stream is closed.
type cancelOnCloseConn struct {
	connect.StreamingClientConn
	cancel context.CancelFunc
}

func (c *cancelOnCloseConn) CloseResponse() error {
	defer c.cancel()
	return c.StreamingClientConn.CloseResponse()
}
