package provider

import (
	"errors"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
	"github.com/t-0-network/provider-sdk/go/signature"
)

// The errors a provider server rejects a request with. Their texts are the same in every SDK
// (cross_test/test_vectors.json, server_cases).
var (
	ErrMissingRequiredHeader       = errors.New("missing required header")
	ErrInvalidHeaderEncoding       = errors.New("invalid header encoding")
	ErrTimestampNotDecimal         = signature.ErrTimestampNotDecimal
	ErrTimestampOutOfRange         = signature.ErrTimestampOutOfRange
	ErrTimestampOutsideWindow      = signature.ErrTimestampOutsideWindow
	ErrUnknownPublicKey            = signature.ErrUnknownPublicKey
	ErrSignatureVerificationFailed = signature.ErrSignatureVerificationFailed
	ErrNoSignatureResult           = errors.New(contract.NoSignatureResult)
	ErrNetworkPublicKeyIsRequired  = errors.New(contract.NetworkPublicKeyNotSet)

	// ErrInvalidSignature is not returned by the SDK: a signature that is not 64 or 65 bytes
	// fails with ErrSignatureVerificationFailed, as in every SDK.
	//
	// Deprecated: Not used by the SDK; will be removed in a future release.
	ErrInvalidSignature = errors.New("invalid signature")
)
