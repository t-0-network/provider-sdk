package provider

import (
	"context"
	"fmt"
	"net/http"

	"connectrpc.com/connect"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
	"github.com/t-0-network/provider-sdk/go/internal/pubkey"
	"github.com/t-0-network/provider-sdk/go/signature"
)

type middleware func(http.Handler) http.Handler

// SignatureError is why a request's signature was not accepted, and the code its call fails with.
//
// Deprecated: Not used by the SDK; will be removed in a future release.
type SignatureError struct {
	ConnectCode connect.Code
	Message     string
}

// SignedPart is the part of a request body its signature was verified over: signature.SignedPart.
type SignedPart = signature.SignedPart

// The parts of a request body a signature covers: those of the signature package.
const (
	SignedBody     = signature.SignedBody
	SignedEnvelope = signature.SignedEnvelope
	SignedPayload  = signature.SignedPayload
)

// SignatureVerification reports how the request in ctx was verified: the part of its body the
// signature covers. Outside a handler built by Handler, the error is CodeInternal with
// ErrNoSignatureResult. signature.FromContext returns the whole verdict.
func SignatureVerification(ctx context.Context) (SignedPart, error) {
	v, ok := signature.FromContext(ctx)
	if !ok {
		return "", connect.NewError(connect.CodeInternal, ErrNoSignatureResult)
	}
	return v.Part, nil
}

// newSignatureVerifierMiddleware verifies the signature of each request with
// signature.NewMiddleware, accepting the network public key only.
func newSignatureVerifierMiddleware(verifier *signatureVerifier, maxBodySize int64) middleware {
	return signature.NewMiddleware(verifier.resolve, maxBodySize)
}

// signatureVerifier accepts requests signed with the network public key.
type signatureVerifier struct {
	networkPublicKey *secp256k1.PublicKey
}

func newSignatureVerifier(networkPublicKeyHexed string) (*signatureVerifier, error) {
	networkPublicKey, err := pubkey.ParseHex(networkPublicKeyHexed)
	if err != nil {
		return nil, fmt.Errorf(contract.NetworkPublicKeyInvalid, err)
	}
	return &signatureVerifier{networkPublicKey: networkPublicKey}, nil
}

// resolve is the signature.KeyResolver of a provider server: it accepts publicKey if it is the
// network public key, compared as a point.
func (v *signatureVerifier) resolve(ctx context.Context, publicKey string) (context.Context, error) {
	signerPublicKey, err := pubkey.ParseHex(publicKey)
	if err != nil || !signerPublicKey.IsEqual(v.networkPublicKey) {
		return nil, ErrUnknownPublicKey
	}
	return ctx, nil
}

// VerifySignature accepts a public key, a message, and a signature, hashes the
// message, and verifies the signature against the public key.
// WithVerifySignatureFn, which took it, has no effect.
//
// Deprecated: Not used by the SDK; will be removed in a future release.
type VerifySignature func(publicKey, message, signature []byte) error
