package provider

import (
	"log/slog"

	"connectrpc.com/connect"
)

const (
	defaultMaxBodySize = 10 * 1024 * 1024 // 10 MiB
)

type providerHandlerOptions struct {
	verifySignatureFn          verifyFunc
	verifySignatureMaxBodySize int64
	connectHandlerOptions      []connect.HandlerOption
	logger                     *slog.Logger
	sdkVersion                 string
}

func newDefaultHandlerOptions(verifySignatureFn verifyFunc, logger *slog.Logger) (providerHandlerOptions, error) {
	if logger == nil {
		logger = slog.Default()
	}
	return providerHandlerOptions{
		verifySignatureMaxBodySize: defaultMaxBodySize,
		connectHandlerOptions: []connect.HandlerOption{
			connect.WithInterceptors(signatureErrorInterceptor(), newValidationInterceptor(logger)),
		},
		verifySignatureFn: verifySignatureFn,
		logger:            logger,
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

// WithMaxBodySize sets the maximum allowed request body size; a larger body is
// rejected with ResourceExhausted. If size is <= 0, the default size will be used.
func WithMaxBodySize(size int64) HandlerOption {
	return func(h *providerHandlerOptions) {
		if size > 0 {
			h.verifySignatureMaxBodySize = size
		}
	}
}
