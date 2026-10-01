package provider

import (
	"log/slog"

	"connectrpc.com/connect"
)

const (
	defaultMaxBodySize = 10 * 1024 * 1024 // 10 MiB
)

type providerHandlerOptions struct {
	verifier                   *signatureVerifier
	verifySignatureMaxBodySize int64
	connectHandlerOptions      []connect.HandlerOption
	logger                     *slog.Logger
	sdkVersion                 string
}

func newDefaultHandlerOptions(verifier *signatureVerifier, logger *slog.Logger) (providerHandlerOptions, error) {
	if logger == nil {
		logger = slog.Default()
	}
	return providerHandlerOptions{
		verifySignatureMaxBodySize: defaultMaxBodySize,
		connectHandlerOptions: []connect.HandlerOption{
			connect.WithInterceptors(signatureErrorInterceptor{}, newValidationInterceptor(logger)),
		},
		verifier: verifier,
		logger:   logger,
	}, nil
}

type HandlerOption func(*providerHandlerOptions)

// WithVerifySignatureFn returns an option that leaves the handler as it is.
//
// Deprecated: has no effect. Every handler verifies requests against the
// network public key given to NewHttpHandler; there is no way to replace or
// turn off that check.
func WithVerifySignatureFn(fn VerifySignature) HandlerOption {
	return func(*providerHandlerOptions) {}
}

func WithConnectHandlerOptions(opts ...connect.HandlerOption) HandlerOption {
	return func(h *providerHandlerOptions) {
		h.connectHandlerOptions = append(h.connectHandlerOptions, opts...)
	}
}

// WithMaxBodySize sets the size limit of a request message, 10 MiB by default: the
// body of a unary call, and each message of a stream. A larger one is rejected
// with ResourceExhausted. The first message of a stream, its 5-byte prefix
// included, is checked before the signature is verified; a stream as a whole has
// no limit. The limit is also passed to connect.WithReadMaxBytes, which then
// applies to messages after decompression too; a connect.WithReadMaxBytes given to
// WithConnectHandlerOptions replaces it. If size is <= 0, the default size will be
// used.
func WithMaxBodySize(size int64) HandlerOption {
	return func(h *providerHandlerOptions) {
		if size > 0 {
			h.verifySignatureMaxBodySize = size
		}
	}
}
