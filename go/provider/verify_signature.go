package provider

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
	"github.com/t-0-network/provider-sdk/go/internal/envelope"
	"github.com/t-0-network/provider-sdk/go/internal/pubkey"
)

type middleware func(http.Handler) http.Handler

// SignatureError is why a request's signature was not accepted, and the code its call fails with.
type SignatureError struct {
	ConnectCode connect.Code
	Message     string
}

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

// verdict is what the middleware found: the signed part, or why the request is rejected.
type verdict struct {
	part SignedPart
	err  *SignatureError
}

type verdictContextKey struct{}

// SignatureVerification reports how the request in ctx was verified: the part of its body the
// signature covers, or the error its call fails with. Outside a handler built by Handler, the
// error is CodeInternal with ErrNoSignatureResult.
func SignatureVerification(ctx context.Context) (SignedPart, error) {
	v, ok := ctx.Value(verdictContextKey{}).(verdict)
	if !ok {
		return "", connect.NewError(connect.CodeInternal, ErrNoSignatureResult)
	}
	if v.err != nil {
		return "", connect.NewError(v.err.ConnectCode, errors.New(v.err.Message))
	}
	return v.part, nil
}

func signatureError(code connect.Code, err error) *SignatureError {
	return &SignatureError{ConnectCode: code, Message: err.Error()}
}

// newSignatureVerifierMiddleware verifies the signature of each request and passes the request on
// with the verdict in its context; signatureErrorInterceptor fails a rejected call before its
// handler runs. Of the body, it reads only what the signature covers: the whole body, or the first
// envelope of an enveloped one, so a stream reaches its handler as it arrives.
func newSignatureVerifierMiddleware(verifier *signatureVerifier, maxBodySize int64) middleware {
	return func(handler http.Handler) http.Handler {
		return http.HandlerFunc(func(writer http.ResponseWriter, req *http.Request) {
			body := req.Body
			limited := http.MaxBytesReader(writer, body, maxBodySize)
			v, read := verifier.verify(req, limited, maxBodySize)

			// connect-go reads a rejected request's body too, so the limit stays on it. Past the
			// first envelope an accepted stream is not limited as a whole; connect.WithReadMaxBytes
			// limits each message.
			rest := io.Reader(limited)
			if v.err == nil {
				rest = body
			}
			req = req.WithContext(context.WithValue(req.Context(), verdictContextKey{}, v))
			req.Body = struct {
				io.Reader
				io.Closer
			}{io.MultiReader(bytes.NewReader(read), rest), body}
			handler.ServeHTTP(writer, req)
		})
	}
}

// signatureVerifier verifies requests against the network public key.
type signatureVerifier struct {
	networkPublicKey *secp256k1.PublicKey
}

func newVerifySignature(networkPublicKeyHexed string) (*signatureVerifier, error) {
	networkPublicKey, err := pubkey.ParseHex(networkPublicKeyHexed)
	if err != nil {
		return nil, fmt.Errorf("invalid network public key: %w", err)
	}
	return &signatureVerifier{networkPublicKey: networkPublicKey}, nil
}

// verify checks the signature headers, then reads from body the part the signature covers and
// verifies the signature over it. It returns the verdict and every byte it read.
func (v *signatureVerifier) verify(req *http.Request, body io.Reader, maxBodySize int64) (verdict, []byte) {
	signature, timestampBytes, rejection := v.checkHeaders(req.Header)
	if rejection != nil {
		return verdict{err: rejection}, nil
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
		return verdict{err: rejection}, read
	case v.verifies(read, timestampBytes, signature):
		return verdict{part: part}, read
	case envelope.IsGRPC(envelope.MediaType(contentType)) && read[0] == 0 && v.verifies(read[5:], timestampBytes, signature):
		// A signer above the gRPC framer covers the uncompressed message alone (the Java SDK).
		return verdict{part: SignedPayload}, read
	default:
		return verdict{err: signatureError(connect.CodeUnauthenticated, ErrSignatureVerificationFailed)}, read
	}
}

// checkHeaders checks everything the signature headers alone decide, before any of the body is
// read: that the three are present and well-formed, the timestamp window, that the public key is
// the network key, and the signature length. It returns the signature and the timestamp bytes.
func (v *signatureVerifier) checkHeaders(headers http.Header) ([]byte, [8]byte, *SignatureError) {
	var noTimestamp [8]byte

	publicKey, err := parsePublicKeyHeader(headers)
	if err != nil {
		return nil, noTimestamp, signatureError(connect.CodeInvalidArgument, err)
	}
	signature, err := parseRequiredHexedHeader(common.SignatureHeader, headers)
	if err != nil {
		return nil, noTimestamp, signatureError(connect.CodeInvalidArgument, err)
	}
	timestamp, timestampBytes, err := parseTimestamp(headers)
	if err != nil {
		return nil, noTimestamp, signatureError(connect.CodeInvalidArgument, err)
	}
	if !timesWithinDelta(timestamp, time.Now(), time.Minute) {
		return nil, noTimestamp, signatureError(connect.CodeInvalidArgument, errors.New("timestamp is outside the allowed time window"))
	}

	signerPublicKey, err := pubkey.ParseHex(publicKey)
	if err != nil {
		return nil, noTimestamp, signatureError(connect.CodeUnauthenticated, fmt.Errorf("invalid public key: %w", err))
	}
	if !signerPublicKey.IsEqual(v.networkPublicKey) {
		return nil, noTimestamp, signatureError(connect.CodeUnauthenticated, ErrUnknownPublicKey)
	}
	if len(signature) < 64 || len(signature) > 65 {
		return nil, noTimestamp, signatureError(connect.CodeUnauthenticated, ErrInvalidSignature)
	}

	return signature, timestampBytes, nil
}

// verifies reports whether signature verifies over Keccak256(signed || timestampBytes) with the
// network public key. A 65th byte, the recovery id, is ignored.
func (v *signatureVerifier) verifies(signed []byte, timestampBytes [8]byte, signature []byte) bool {
	// Full slice expression: append must copy, never write into the spare capacity of signed.
	digest := crypto.LegacyKeccak256(append(signed[:len(signed):len(signed)], timestampBytes[:]...))
	return crypto.VerifySignature(v.networkPublicKey, digest, signature[:64])
}

func parseRequiredHexedHeader(headerName string, headers http.Header) ([]byte, error) {
	encodedHeader := headers.Get(headerName)
	if encodedHeader == "" {
		return nil, fmt.Errorf("%w: %s", ErrMissingRequiredHeader, headerName)
	}

	hexStr := strings.TrimPrefix(encodedHeader, "0x")
	decodedHeader, err := hex.DecodeString(hexStr)
	if err != nil {
		return nil, fmt.Errorf("%w: %s", ErrInvalidHeaderEncoding, headerName)
	}

	return decodedHeader, nil
}

// parsePublicKeyHeader returns the X-Public-Key header. Whether it is the
// network public key is checked by checkHeaders, which rejects anything else,
// malformed or not, as Unauthenticated.
func parsePublicKeyHeader(headers http.Header) (string, error) {
	encodedHeader := headers.Get(common.PublicKeyHeader)
	if encodedHeader == "" {
		return "", fmt.Errorf("%w: %s", ErrMissingRequiredHeader, common.PublicKeyHeader)
	}

	return encodedHeader, nil
}

// parseTimestamp extracts the timestamp from the request headers, and returns
// the parsed time and its byte representation in little-endian format.
func parseTimestamp(headers http.Header) (time.Time, [8]byte, error) {
	tsBytes := [8]byte{}

	timestampValue := headers.Get(common.SignatureTimestampHeader)
	if timestampValue == "" {
		return time.Time{}, tsBytes, fmt.Errorf("%w: %s", ErrMissingRequiredHeader, common.SignatureTimestampHeader)
	}

	// ASCII digits only: strconv.ParseInt would also take a leading + or -.
	if strings.TrimLeft(timestampValue, "0123456789") != "" {
		return time.Time{}, tsBytes, errors.New("invalid timestamp header: not a decimal number")
	}
	timestamp, err := strconv.ParseInt(timestampValue, 10, 64)
	if err != nil {
		return time.Time{}, tsBytes, fmt.Errorf("invalid timestamp header: %s", err.Error())
	}

	binary.LittleEndian.PutUint64(tsBytes[:], uint64(timestamp))
	return time.UnixMilli(timestamp), tsBytes, nil
}

// readBodyWithCap reads the whole body from body, which http.MaxBytesReader limits to maxBodySize
// bytes. It returns what it read, error or not.
func readBodyWithCap(contentLength int64, body io.Reader, maxBodySize int64) ([]byte, *SignatureError) {
	// The Content-Length header is optional, and not trusted: it only saves reading a body that is
	// too large.
	if contentLength > maxBodySize {
		return nil, tooLarge(maxBodySize)
	}
	whole, err := io.ReadAll(body)
	if err != nil {
		return whole, bodyReadError("reading request body", err, maxBodySize)
	}
	return whole, nil
}

// readFirstEnvelope reads the first envelope, flags (1) || uint32be(length) || payload, and
// nothing after it. Its length is checked against maxBodySize before the payload is read. It
// returns what it read, error or not.
func readFirstEnvelope(body io.Reader, maxBodySize int64) ([]byte, *SignatureError) {
	var prefix [5]byte
	if n, err := io.ReadFull(body, prefix[:]); err != nil {
		return prefix[:n], bodyReadError("no first message", err, maxBodySize)
	}
	length := int64(binary.BigEndian.Uint32(prefix[1:]))
	if 5+length > maxBodySize {
		return prefix[:], tooLarge(maxBodySize)
	}
	first := make([]byte, 5+length)
	copy(first, prefix[:])
	if n, err := io.ReadFull(body, first[5:]); err != nil {
		return first[:5+n], bodyReadError("truncated first message", err, maxBodySize)
	}
	return first, nil
}

func tooLarge(maxBodySize int64) *SignatureError {
	return signatureError(connect.CodeResourceExhausted, fmt.Errorf("max payload size of %d bytes exceeded", maxBodySize))
}

// bodyReadError is ResourceExhausted for a body over the limit. Any other failure leaves nothing
// for the signature to cover, so it is Unauthenticated.
func bodyReadError(reason string, err error, maxBodySize int64) *SignatureError {
	if _, ok := errors.AsType[*http.MaxBytesError](err); ok {
		return tooLarge(maxBodySize)
	}
	return signatureError(connect.CodeUnauthenticated, fmt.Errorf("%s: %w", reason, err))
}

// VerifySignature accepts a public key, a message, and a signature, hashes the
// message, and verifies the signature against the public key.
//
// Deprecated: not used by the SDK. WithVerifySignatureFn, which took it, has no
// effect.
type VerifySignature func(publicKey, message, signature []byte) error

func timesWithinDelta(t1, t2 time.Time, delta time.Duration) bool {
	diff := t1.Sub(t2)
	if diff < 0 {
		diff = -diff
	}

	return diff <= delta
}
