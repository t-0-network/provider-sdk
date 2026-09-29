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

// SigningTransport is an http.RoundTripper that signs each request and sets the signature, public
// key and timestamp headers. Connect-streaming and gRPC requests are signed over their first
// envelope and sent as soon as it is read; other requests over the whole body.
// See docs/STREAMING.md.
type SigningTransport struct {
	transport http.RoundTripper
	sign      crypto.SignFn
	timeNow   func() time.Time
}

func (t *SigningTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	// A GET request carries its message in the URL, which the signature does not cover.
	if req.Method == http.MethodGet || req.Method == "" {
		closeRequestBody(req)
		return nil, connect.NewError(connect.CodeUnimplemented, errors.New("GET requests are not supported"))
	}
	if isEnveloped(req) {
		return t.signFirstEnvelope(req)
	}
	return t.signWholeBody(req)
}

func (t *SigningTransport) signWholeBody(req *http.Request) (*http.Response, error) {
	hasBody := req.Body != nil && req.Body != http.NoBody
	var body []byte
	if hasBody {
		var err error
		body, err = io.ReadAll(req.Body)
		closeRequestBody(req)
		if err != nil {
			return nil, fmt.Errorf("reading request body: %w", err)
		}
	}

	// The caller's request is not modified, as the http.RoundTripper contract requires.
	signed := req.Clone(req.Context())
	if hasBody {
		signed.Body = io.NopCloser(bytes.NewReader(body))
		signed.ContentLength = int64(len(body))
		signed.GetBody = func() (io.ReadCloser, error) { return io.NopCloser(bytes.NewReader(body)), nil }
	}
	if err := t.setSignatureHeaders(signed.Header, body); err != nil {
		return nil, err
	}

	return t.transport.RoundTrip(signed)
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
	// The forwarded bytes are unchanged, so ContentLength and GetBody stay valid.
	if len(envelope) > 0 {
		signed.Body = struct {
			io.Reader
			io.Closer
		}{io.MultiReader(bytes.NewReader(envelope), req.Body), req.Body}
	}

	return t.transport.RoundTrip(signed)
}

// setSignatureHeaders signs Keccak256(signed || uint64le(now_ms)) and sets the signature headers.
func (t *SigningTransport) setSignatureHeaders(header http.Header, signed []byte) error {
	timestamp := t.timeNow().UnixMilli()

	timestampBytes := [8]byte{}
	binary.LittleEndian.PutUint64(timestampBytes[:], uint64(timestamp))

	// Full slice expression: append must copy, never write into signed's spare capacity.
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

// readFirstEnvelope reads the first envelope and nothing after it; nil if the body ends before one.
// A client stream's body is a pipe, so this blocks until the first Send; the context ends the wait.
func readFirstEnvelope(req *http.Request) ([]byte, error) {
	if req.Body == nil || req.Body == http.NoBody {
		return nil, nil
	}

	stop := context.AfterFunc(req.Context(), func() { _ = req.Body.Close() })
	envelope, err := readEnvelope(req.Body)
	if !stop() {
		// The AfterFunc ran: the context ended and the body is closed.
		return nil, req.Context().Err()
	}
	return envelope, err
}

// maxPrealloc caps what the length prefix may allocate up front; larger messages grow as they arrive.
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

// firstMessageError gives a truncated first envelope CodeInvalidArgument, not wrapping io.EOF:
// connect-go reports an uncoded RoundTrip error as unavailable (retryable), and replaces one that
// wraps io.EOF, losing its code.
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
	return mt == "application/grpc" || strings.HasPrefix(mt, "application/grpc+")
}

// isEnveloped reports whether the body is a sequence of envelopes, of which only the first is signed.
func isEnveloped(req *http.Request) bool {
	mt := mediaType(req)
	return strings.HasPrefix(mt, "application/connect+") || isGRPCMediaType(mt)
}

// callTimeouts gives each call whose context has no deadline the timeout of its stream type. It is
// an interceptor because the transport cannot tell gRPC unary from gRPC server-streaming requests.
type callTimeouts struct {
	unary, stream time.Duration
}

func (c callTimeouts) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		if _, ok := ctx.Deadline(); ok {
			return next(ctx, req)
		}
		ctx, cancel := context.WithTimeout(ctx, c.unary)
		defer cancel()
		return next(ctx, req)
	}
}

func (c callTimeouts) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return func(ctx context.Context, spec connect.Spec) connect.StreamingClientConn {
		if _, ok := ctx.Deadline(); ok {
			return next(ctx, spec)
		}
		ctx, cancel := context.WithTimeout(ctx, c.stream)
		return &cancelOnCloseConn{StreamingClientConn: next(ctx, spec), cancel: cancel}
	}
}

func (callTimeouts) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return next
}

// rejectBidi fails bidirectional-streaming calls with CodeUnimplemented before anything is sent:
// the network does not accept them.
type rejectBidi struct{}

func (rejectBidi) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	return next
}

func (rejectBidi) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return func(ctx context.Context, spec connect.Spec) connect.StreamingClientConn {
		if spec.StreamType == connect.StreamTypeBidi {
			return &unsupportedConn{spec: spec, header: http.Header{}}
		}
		return next(ctx, spec)
	}
}

func (rejectBidi) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return next
}

// unsupportedConn is a stream that is never opened: every send and receive fails.
type unsupportedConn struct {
	spec   connect.Spec
	header http.Header
}

func errBidiUnsupported() error {
	return connect.NewError(connect.CodeUnimplemented, errors.New("bidirectional streams are not supported"))
}

func (c *unsupportedConn) Spec() connect.Spec           { return c.spec }
func (c *unsupportedConn) Peer() connect.Peer           { return connect.Peer{} }
func (c *unsupportedConn) Send(any) error               { return errBidiUnsupported() }
func (c *unsupportedConn) RequestHeader() http.Header   { return c.header }
func (c *unsupportedConn) CloseRequest() error          { return nil }
func (c *unsupportedConn) Receive(any) error            { return errBidiUnsupported() }
func (c *unsupportedConn) ResponseHeader() http.Header  { return http.Header{} }
func (c *unsupportedConn) ResponseTrailer() http.Header { return http.Header{} }
func (c *unsupportedConn) CloseResponse() error         { return nil }

// cancelOnCloseConn releases a stream's deadline when the stream is closed.
type cancelOnCloseConn struct {
	connect.StreamingClientConn
	cancel context.CancelFunc
}

func (c *cancelOnCloseConn) CloseResponse() error {
	defer c.cancel()
	return c.StreamingClientConn.CloseResponse()
}
