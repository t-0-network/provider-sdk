package provider

import (
	"log/slog"
	"time"

	"connectrpc.com/connect"
)

const (
	// DefaultMaxBodySize is the largest request body a provider server accepts unless
	// WithMaxBodySize sets another: the whole HTTP body of a unary call, gRPC prefix included.
	DefaultMaxBodySize = 10 * 1024 * 1024 // 10 MiB

	// TimestampWindow is how far X-Signature-Timestamp may be from the server's clock, either way.
	// It is fixed: no option changes it.
	TimestampWindow = time.Minute
)

type providerHandlerOptions struct {
	verifier                   *signatureVerifier
	verifySignatureMaxBodySize int64
	connectHandlerOptions      []connect.HandlerOption
	logger                     *slog.Logger
	sdkVersion                 string
}

func newDefaultHandlerOptions(verifier *signatureVerifier, logger *slog.Logger, sdkVersion string) (providerHandlerOptions, error) {
	if logger == nil {
		logger = slog.Default()
	}
	return providerHandlerOptions{
		verifySignatureMaxBodySize: DefaultMaxBodySize,
		connectHandlerOptions: []connect.HandlerOption{
			connect.WithInterceptors(signatureErrorInterceptor{}, newValidationInterceptor(logger, sdkVersion)),
		},
		verifier: verifier,
		logger:   logger,
	}, nil
}

type HandlerOption func(*providerHandlerOptions)

// WithVerifySignatureFn returns an option that leaves the handler as it is: it
// has no effect. Every handler verifies requests against the network public key
// given to NewHttpHandler; there is no way to replace or turn off that check.
//
// Deprecated: Not used by the SDK; will be removed in a future release.
func WithVerifySignatureFn(fn VerifySignature) HandlerOption {
	return func(*providerHandlerOptions) {}
}

func WithConnectHandlerOptions(opts ...connect.HandlerOption) HandlerOption {
	return func(h *providerHandlerOptions) {
		h.connectHandlerOptions = append(h.connectHandlerOptions, opts...)
	}
}

// WithMaxBodySize sets the largest request message, 10 MiB by default: the body
// of a unary call, or each message of a stream (the first one with its 5-byte
// prefix, checked before the signature). A larger one is rejected with
// ResourceExhausted. The limit is also passed to connect.WithReadMaxBytes; a
// caller's own replaces it. If size is <= 0, the default size will be used.
func WithMaxBodySize(size int64) HandlerOption {
	return func(h *providerHandlerOptions) {
		if size > 0 {
			h.verifySignatureMaxBodySize = size
		}
	}
}
