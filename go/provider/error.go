package provider

import (
	"errors"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// The errors a provider server rejects a request with. Their texts are the same in every SDK
// (cross_test/test_vectors.json, server_cases).
var (
	ErrMissingRequiredHeader       = errors.New("missing required header")
	ErrInvalidHeaderEncoding       = errors.New("invalid header encoding")
	ErrTimestampNotDecimal         = errors.New(contract.TimestampNotDecimal)
	ErrTimestampOutOfRange         = errors.New(contract.TimestampOutOfRange)
	ErrTimestampOutsideWindow      = errors.New(contract.TimestampOutsideWindow)
	ErrUnknownPublicKey            = errors.New(contract.UnknownPublicKey)
	ErrSignatureVerificationFailed = errors.New(contract.SignatureVerificationFailed)
	ErrNoSignatureResult           = errors.New(contract.NoSignatureResult)
	ErrNetworkPublicKeyIsRequired  = errors.New(contract.NetworkPublicKeyNotSet)

	// ErrInvalidSignature is not returned by the SDK: a signature that is not 64 or 65 bytes
	// fails with ErrSignatureVerificationFailed, as in every SDK.
	//
	// Deprecated: Not used by the SDK; will be removed in a future release.
	ErrInvalidSignature = errors.New("invalid signature")
)
