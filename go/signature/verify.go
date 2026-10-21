// Package signature verifies the signature of a request a server receives: the X-Public-Key,
// X-Signature and X-Signature-Timestamp headers against the part of the body the caller signed
// (docs/CROSS_SDK_RULES.md). provider.Handler verifies the network's calls with it, against the
// network public key; a server that takes calls signed by other keys resolves each key with its
// own KeyResolver.
package signature

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
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
	"github.com/t-0-network/provider-sdk/go/internal/envelope"
	"github.com/t-0-network/provider-sdk/go/internal/pubkey"
)

// TimestampWindow is how far X-Signature-Timestamp may be from the server's clock, either way.
// It is fixed: no option changes it.
const TimestampWindow = time.Minute

// DefaultMaxBodySize is the body limit of NewMiddleware unless WithMaxBodySize sets another.
const DefaultMaxBodySize = 10 * 1024 * 1024 // 10 MiB

// Option configures NewMiddleware.
type Option func(*options)

type options struct {
	maxBodySize int64
}

// WithMaxBodySize sets the largest body of a unary call, gRPC prefix included, and the largest
// first envelope of an enveloped one, DefaultMaxBodySize by default. A larger one is
// ResourceExhausted. If size is <= 0, the default is used.
func WithMaxBodySize(size int64) Option {
	return func(o *options) {
		if size > 0 {
			o.maxBodySize = size
		}
	}
}

// The errors a request is rejected with. Their texts are the same in every SDK
// (cross_test/test_vectors.json, server_cases).
var (
	ErrTimestampNotDecimal         = errors.New(contract.TimestampNotDecimal)
	ErrTimestampOutOfRange         = errors.New(contract.TimestampOutOfRange)
	ErrTimestampOutsideWindow      = errors.New(contract.TimestampOutsideWindow)
	ErrUnknownPublicKey            = errors.New(contract.UnknownPublicKey)
	ErrSignatureVerificationFailed = errors.New(contract.SignatureVerificationFailed)
)

// SignedPart is the part of a request body its signature was verified over.
type SignedPart string

const (
	// SignedBody is the whole body: a unary call that is not gRPC.
	SignedBody SignedPart = "body"
	// SignedEnvelope is the first envelope, its 5-byte prefix included: a Connect stream, or a
	// gRPC call signed below the framer.
	SignedEnvelope SignedPart = "envelope"
	// SignedPayload is the first envelope without its 5-byte prefix: a gRPC call signed above the
	// framer (the Java SDK).
	SignedPayload SignedPart = "payload"
)

// KeyResolver decides whether a request signed with publicKey, its X-Public-Key value, is
// accepted. The middleware calls it after the signature headers are present and well-formed, the
// timestamp is inside the window and publicKey is a key (rule V2), and before it reads any of the
// body. To accept the key it returns the context the request goes on with, which may carry the
// caller's identity. A *connect.Error it returns, wrapped or not, is the answer to the request;
// any other error is Unauthenticated ErrUnknownPublicKey. So return a plain error for a key you
// do not accept, which gives the caller the shared message, and a *connect.Error only for a
// failure such as a failed key lookup.
type KeyResolver func(ctx context.Context, publicKey string) (context.Context, error)

// Verdict is how the middleware verified an accepted request.
type Verdict struct {
	// Part is the part of the body the signature covers.
	Part SignedPart
	// Timestamp is the X-Signature-Timestamp of the request.
	Timestamp time.Time
	// Body is what the middleware read of the body: the whole body for SignedBody, the first
	// envelope with its 5-byte prefix for SignedEnvelope and SignedPayload. The signature covers
	// all of it, except for SignedPayload, where it covers Body[5:].
	Body []byte
}

type verdictContextKey struct{}

// FromContext returns the verdict of the request in ctx, or false if the middleware did not
// accept it.
func FromContext(ctx context.Context) (Verdict, bool) {
	v, ok := ctx.Value(verdictContextKey{}).(Verdict)
	return v, ok
}

// NewMiddleware verifies the signature of each request, with the public keys resolve accepts. It
// answers a rejected request itself, in the request's protocol, before the RPC library reads or
// decodes anything, so the caller gets the rejection's code and message whatever the body holds.
// An accepted request goes on with the context resolve returned and the verdict in it. Of the
// body, it reads only what the signature covers: the whole body, or the first envelope of an
// enveloped one, so a stream reaches its handler as it arrives. The body limit is
// DefaultMaxBodySize unless WithMaxBodySize sets another.
//
// A request whose method is not POST is refused first, before its headers are checked: a call's
// message must be in its signed body. It gets Unimplemented, as a Connect unary error.
func NewMiddleware(resolve KeyResolver, opts ...Option) func(http.Handler) http.Handler {
	o := options{maxBodySize: DefaultMaxBodySize}
	for _, opt := range opts {
		opt(&o)
	}
	maxBodySize := o.maxBodySize
	errorWriter := connect.NewErrorWriter()
	return func(handler http.Handler) http.Handler {
		return http.HandlerFunc(func(writer http.ResponseWriter, req *http.Request) {
			if req.Method != http.MethodPost {
				// connect.ErrorWriter writes a Connect unary error for any request that is not POST.
				_ = errorWriter.Write(writer, req, connect.NewError(connect.CodeUnimplemented, errors.New(contract.GetNotSupported)))
				return
			}
			body := req.Body
			limited := http.MaxBytesReader(writer, body, maxBodySize)
			ctx, v, rejection := verify(req, resolve, limited, maxBodySize)
			if rejection != nil {
				_ = errorWriter.Write(writer, req, rejection)
				return
			}

			// Past the first envelope an accepted stream is not limited as a whole;
			// connect.WithReadMaxBytes limits each message.
			rest := io.Reader(body)
			req = req.WithContext(context.WithValue(ctx, verdictContextKey{}, v))
			req.Body = struct {
				io.Reader
				io.Closer
			}{io.MultiReader(bytes.NewReader(v.Body), rest), body}
			handler.ServeHTTP(writer, req)
		})
	}
}

// checkedHeaders is what checkHeaders found in the signature headers of a request.
type checkedHeaders struct {
	publicKey      *secp256k1.PublicKey
	signature      []byte
	timestamp      time.Time
	timestampBytes [8]byte
}

// verify checks the signature headers, then reads from body the part the signature covers and
// verifies the signature over it. It returns the context resolve returned and the verdict, or the
// error the request is rejected with.
func verify(req *http.Request, resolve KeyResolver, body io.Reader, maxBodySize int64) (context.Context, Verdict, *connect.Error) {
	ctx, headers, rejection := checkHeaders(req, resolve)
	if rejection != nil {
		return nil, Verdict{}, rejection
	}

	// Of an enveloped body only the first envelope is signed. A gRPC unary body is a single
	// envelope, so the rule covers it as well.
	contentType := req.Header.Get("Content-Type")
	part, read := SignedBody, []byte(nil)
	if envelope.IsEnveloped(contentType) {
		part = SignedEnvelope
		read, rejection = readFirstEnvelope(body, maxBodySize)
	} else {
		read, rejection = readBodyWithCap(req.ContentLength, body, maxBodySize)
	}

	switch {
	case rejection != nil:
		return nil, Verdict{}, rejection
	case headers.verifies(read):
		return ctx, Verdict{Part: part, Timestamp: headers.timestamp, Body: read}, nil
	case envelope.IsGRPC(contentType) && read[0] == 0 && headers.verifies(read[5:]):
		// A signer above the gRPC framer covers the uncompressed message alone (the Java SDK).
		return ctx, Verdict{Part: SignedPayload, Timestamp: headers.timestamp, Body: read}, nil
	default:
		return nil, Verdict{}, connect.NewError(connect.CodeUnauthenticated, ErrSignatureVerificationFailed)
	}
}

// checkHeaders checks everything the signature headers alone decide, before any of the body is
// read: that the three are present and well-formed, the timestamp window, that resolve accepts
// the public key, and the signature length. It returns the context resolve returned.
func checkHeaders(req *http.Request, resolve KeyResolver) (context.Context, checkedHeaders, *connect.Error) {
	reject := func(code connect.Code, err error) (context.Context, checkedHeaders, *connect.Error) {
		return nil, checkedHeaders{}, connect.NewError(code, err)
	}

	publicKeyHeader, err := parsePublicKeyHeader(req.Header)
	if err != nil {
		return reject(connect.CodeInvalidArgument, err)
	}
	signature, err := parseRequiredHexedHeader(common.SignatureHeader, req.Header)
	if err != nil {
		return reject(connect.CodeInvalidArgument, err)
	}
	timestamp, timestampBytes, err := parseTimestamp(req.Header)
	if err != nil {
		return reject(connect.CodeInvalidArgument, err)
	}
	if !timesWithinDelta(timestamp, time.Now(), TimestampWindow) {
		return reject(connect.CodeInvalidArgument, ErrTimestampOutsideWindow)
	}

	// A value that is not a key is not a key resolve could accept either.
	publicKey, err := pubkey.ParseHex(publicKeyHeader)
	if err != nil {
		return reject(connect.CodeUnauthenticated, ErrUnknownPublicKey)
	}
	ctx, err := resolve(req.Context(), publicKeyHeader)
	if err != nil {
		if connectErr, ok := errors.AsType[*connect.Error](err); ok {
			return nil, checkedHeaders{}, connectErr
		}
		return reject(connect.CodeUnauthenticated, ErrUnknownPublicKey)
	}
	if len(signature) < 64 || len(signature) > 65 {
		return reject(connect.CodeUnauthenticated, ErrSignatureVerificationFailed)
	}

	return ctx, checkedHeaders{publicKey: publicKey, signature: signature, timestamp: timestamp, timestampBytes: timestampBytes}, nil
}

// verifies reports whether the signature verifies over Keccak256(signed || timestampBytes) with
// the public key. A 65th byte, the recovery id, is ignored.
func (h checkedHeaders) verifies(signed []byte) bool {
	digest := crypto.LegacyKeccak256Concat(signed, h.timestampBytes[:])
	return crypto.VerifySignature(h.publicKey, digest, h.signature[:64])
}

func parseRequiredHexedHeader(headerName string, headers http.Header) ([]byte, error) {
	encodedHeader := headers.Get(headerName)
	if encodedHeader == "" {
		return nil, fmt.Errorf(contract.MissingHeader, headerName)
	}

	hexStr := encodedHeader
	if strings.HasPrefix(hexStr, "0x") || strings.HasPrefix(hexStr, "0X") {
		hexStr = hexStr[2:]
	}
	decodedHeader, err := hex.DecodeString(hexStr)
	if err != nil || len(decodedHeader) == 0 {
		return nil, fmt.Errorf(contract.InvalidHeaderEncoding, headerName)
	}

	return decodedHeader, nil
}

// parsePublicKeyHeader returns the X-Public-Key header. Whether it is a key, and one the resolver
// accepts, is checked by checkHeaders, which rejects anything else, malformed or not, as
// Unauthenticated.
func parsePublicKeyHeader(headers http.Header) (string, error) {
	encodedHeader := headers.Get(common.PublicKeyHeader)
	if encodedHeader == "" {
		return "", fmt.Errorf(contract.MissingHeader, common.PublicKeyHeader)
	}

	return encodedHeader, nil
}

// parseTimestamp extracts the timestamp from the request headers, and returns
// the parsed time and its byte representation in little-endian format.
func parseTimestamp(headers http.Header) (time.Time, [8]byte, error) {
	tsBytes := [8]byte{}

	timestampValue := headers.Get(common.SignatureTimestampHeader)
	if timestampValue == "" {
		return time.Time{}, tsBytes, fmt.Errorf(contract.MissingHeader, common.SignatureTimestampHeader)
	}

	// ASCII digits only: strconv.ParseInt would also take a leading + or -.
	if strings.TrimLeft(timestampValue, "0123456789") != "" {
		return time.Time{}, tsBytes, ErrTimestampNotDecimal
	}
	timestamp, err := strconv.ParseInt(timestampValue, 10, 64)
	if err != nil {
		return time.Time{}, tsBytes, ErrTimestampOutOfRange
	}

	binary.LittleEndian.PutUint64(tsBytes[:], uint64(timestamp))
	return time.UnixMilli(timestamp), tsBytes, nil
}

// readBodyWithCap reads the whole body from body, which http.MaxBytesReader limits to maxBodySize
// bytes. It returns what it read, error or not.
func readBodyWithCap(contentLength int64, body io.Reader, maxBodySize int64) ([]byte, *connect.Error) {
	// The Content-Length header is optional, and not trusted: it only saves reading a body that is
	// too large.
	if contentLength > maxBodySize {
		return nil, tooLarge(maxBodySize)
	}
	whole, err := io.ReadAll(body)
	if err != nil {
		return whole, bodyReadError(contract.RequestBodyReadFailed, err, maxBodySize)
	}
	return whole, nil
}

// readFirstEnvelope reads the first envelope, flags (1) || uint32be(length) || payload, and
// nothing after it. Its length is checked against maxBodySize before the payload is read. It
// returns what it read, error or not.
func readFirstEnvelope(body io.Reader, maxBodySize int64) ([]byte, *connect.Error) {
	var prefix [5]byte
	if n, err := io.ReadFull(body, prefix[:]); err != nil {
		return prefix[:n], bodyReadError(contract.NoFirstMessage, err, maxBodySize)
	}
	length := int64(binary.BigEndian.Uint32(prefix[1:]))
	if 5+length > maxBodySize {
		return prefix[:], tooLarge(maxBodySize)
	}
	first := make([]byte, 5+length)
	copy(first, prefix[:])
	if n, err := io.ReadFull(body, first[5:]); err != nil {
		return first[:5+n], bodyReadError(contract.TruncatedFirstMessage, err, maxBodySize)
	}
	return first, nil
}

func tooLarge(maxBodySize int64) *connect.Error {
	return connect.NewError(connect.CodeResourceExhausted, fmt.Errorf(contract.BodyTooLarge, maxBodySize))
}

// bodyReadError is ResourceExhausted for a body over the limit. Any other failure leaves nothing
// for the signature to cover, so it is Unauthenticated, with format filled with err.
func bodyReadError(format string, err error, maxBodySize int64) *connect.Error {
	if _, ok := errors.AsType[*http.MaxBytesError](err); ok {
		return tooLarge(maxBodySize)
	}
	return connect.NewError(connect.CodeUnauthenticated, fmt.Errorf(format, err))
}

func timesWithinDelta(t1, t2 time.Time, delta time.Duration) bool {
	diff := t1.Sub(t2)
	if diff < 0 {
		diff = -diff
	}

	return diff <= delta
}
