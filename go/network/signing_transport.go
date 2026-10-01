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
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/internal/envelope"
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
// envelope and sent as soon as it is read; other requests over the whole body. A GET request is
// refused with CodeUnimplemented, because its message would travel in the URL, which the
// signature does not cover. See docs/STREAMING.md.
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
	if envelope.IsEnveloped(req.Header.Get("Content-Type")) {
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
		}{abortWhenDone{ctx: req.Context(), r: io.MultiReader(bytes.NewReader(envelope), req.Body)}, req.Body}
	}

	return t.transport.RoundTrip(signed)
}

// abortWhenDone ends a stream's body in the call's context error rather than io.EOF once that
// context is done. A cancelled stream's body is still closed normally afterwards, and a body that
// ended normally would let the server take a partial stream for a complete one.
type abortWhenDone struct {
	ctx context.Context
	r   io.Reader
}

func (a abortWhenDone) Read(p []byte) (int, error) {
	n, err := a.r.Read(p)
	if err == io.EOF {
		if ctxErr := a.ctx.Err(); ctxErr != nil {
			return n, ctxErr
		}
	}
	return n, err
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

func readEnvelope(r io.Reader) ([]byte, error) {
	var prefix [5]byte
	if _, err := io.ReadFull(r, prefix[:]); err != nil {
		if errors.Is(err, io.EOF) {
			return nil, nil // no first message
		}
		return nil, firstMessageError(err)
	}

	var envelope bytes.Buffer
	envelope.Write(prefix[:])
	if _, err := io.CopyN(&envelope, r, int64(binary.BigEndian.Uint32(prefix[1:]))); err != nil {
		return nil, firstMessageError(err)
	}
	return envelope.Bytes(), nil
}

// firstMessageError reports a first message cut short as the caller's error (CodeInvalidArgument),
// not as a failure worth retrying. It must not wrap io.EOF, or the code is lost.
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
