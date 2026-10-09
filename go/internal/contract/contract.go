// Package contract holds the messages every SDK raises itself, each as one named constant:
// `messages` in cross_test/test_vectors.json, which the SDK's contract test compares them with. A
// message with a placeholder is a format string: %s or %d for a value, %w for the error underneath.
// The shared values are public constants of the packages that use them.
package contract

// Keys and signers.
const (
	PrivateKeyEmpty         = "private key must not be null or empty"
	PrivateKeyMalformed     = "private key must be 32 bytes (64 hex characters)"
	PrivateKeyOutOfRange    = "private key must be in range [1, n-1]"
	PublicKeyNotHex         = "must be hex, with an optional 0x or 0X prefix"
	PublicKeyNotAPoint      = "not a point on secp256k1"
	NetworkPublicKeyNotSet  = "network public key is not set"
	NetworkPublicKeyInvalid = "invalid network public key: %w" // the PublicKeyNotHex or PublicKeyNotAPoint error
	SignerNull              = "signer must not be null"
	KeyAndSigner            = "a private key and a signer must not both be given"
	DigestLength            = "digest must be 32 bytes"
	SignerSignatureInvalid  = "signature must be 64 or 65 bytes"
	SignerPublicKeyInvalid  = "public key must be 65 bytes, uncompressed"
)

// Clients.
const (
	BaseURLNotSet          = "base URL is not set"
	BaseURLNotValid        = "base URL is not valid"
	TimeoutNotValid        = "timeout must be a positive duration of at most 2147483647 ms"
	StreamTimeoutNotValid  = "stream timeout must be a positive duration of at most 2147483647 ms"
	SigningFailed          = "signing the request failed: %w" // the signer's error, or a failed check of its output
	GetNotSupported        = "GET requests are not supported"
	BidiNotSupported       = "bidirectional streams are not supported"
	FirstMessageIncomplete = "streaming request ends inside its first message"
	FirstMessageReadFailed = "reading first request message: %w" // the read error
	KycDownloadShape       = "download stream must be one metadata message followed by chunks"
)

// Server setup.
const (
	ServiceNull          = "service must not be null"
	PortNotValid         = "port must be between 0 and 65535"
	ShutdownContextDone  = "shutdown context already done: %w" // the context's error
	ServerShutdownFailed = "http server shutdown: %w"          // the error of http.Server.Shutdown
)

// Server rejections and errors.
const (
	MissingHeader               = "missing required header: %s" // the header's name
	InvalidHeaderEncoding       = "invalid header encoding: %s" // the header's name
	TimestampNotDecimal         = "invalid timestamp header: not a decimal number"
	TimestampOutOfRange         = "invalid timestamp header: value out of range"
	TimestampOutsideWindow      = "timestamp is outside the allowed time window"
	UnknownPublicKey            = "request signed with unknown public key"
	SignatureVerificationFailed = "signature verification failed"
	BodyTooLarge                = "max payload size of %d bytes exceeded" // the limit in bytes
	NoSignatureResult           = "no signature result in context"
	NoFirstMessage              = "no first message: %w"           // the read error
	TruncatedFirstMessage       = "truncated first message: %w"    // the read error
	UnknownService              = "unknown service '%s'"           // the service asked for
	ResponseInvalid             = "response validation failed: %s" // "<field path>: <message>", joined by "; "
	ResponseValidationError     = "response validation error: %w"  // protovalidate could not evaluate a rule
	RequestBodyReadFailed       = "reading request body: %w"       // the read error
)
