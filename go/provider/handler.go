package provider

import (
	"log/slog"
	"net/http"
	"strings"

	"connectrpc.com/connect"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/system/systemconnect"
)

type BuildHandler func(defaultOptions providerHandlerOptions) (path string, handler http.Handler)

// T-ZERO Network Public Key, required for signature verification.
type NetworkPublicKeyHexed string

// HttpHandlerOption configures server-wide options (logger, etc.) shared by
// every registered service. Use it with NewHttpHandlerWithOptions.
type HttpHandlerOption func(*providerHandlerOptions)

// WithLogger overrides the *slog.Logger used by the response-validation
// interceptor when it catches a failure. The default is slog.Default(); pass a
// custom logger here to route SDK diagnostics into your own pipeline.
func WithLogger(logger *slog.Logger) HttpHandlerOption {
	return func(o *providerHandlerOptions) {
		if logger != nil {
			o.logger = logger
		}
	}
}

// NewHttpHandler returns a ready-to-use *http.ServeMux with the provider
// service handlers registered. SystemService is registered automatically
// alongside the customer services.
//
// Parameters:
//   - networkPublicKey: hex-encoded T-0 Network public key used for signature
//     verification (empty disables verification).
//   - buildHandlers: zero or more handlers built via provider.Handler(...).
//
// Returns the mux + any setup error (typically a malformed public key).
//
// Equivalent to NewHttpHandlerWithOptions(networkPublicKey, nil, buildHandlers...).
func NewHttpHandler(
	networkPublicKey NetworkPublicKeyHexed,
	buildHandlers ...BuildHandler,
) (http.Handler, error) {
	return NewHttpHandlerWithOptions(networkPublicKey, nil, buildHandlers...)
}

// NewHttpHandlerWithOptions is the option-aware sibling of NewHttpHandler. Use
// it when you need to override server-wide settings such as the slog.Logger
// used by the response-validation interceptor.
//
//	handler, err := provider.NewHttpHandlerWithOptions(
//	    networkPublicKey,
//	    []provider.HttpHandlerOption{provider.WithLogger(slog.Default())},
//	    provider.Handler(paymentconnect.NewProviderServiceHandler, impl),
//	)
func NewHttpHandlerWithOptions(
	networkPublicKey NetworkPublicKeyHexed,
	opts []HttpHandlerOption,
	buildHandlers ...BuildHandler,
) (http.Handler, error) {
	var verifySignatureFn VerifySignature = nil
	if networkPublicKey != "" {
		var err error
		verifySignatureFn, err = newVerifySignature(string(networkPublicKey))
		if err != nil {
			return nil, err
		}
	}

	// Apply caller-supplied overrides to a scratch struct, then thread the
	// final logger into newDefaultHandlerOptions so the validation interceptor
	// is constructed with it. This keeps the interceptor construction in one
	// place even though logger arrives via a server-level option.
	scratch := providerHandlerOptions{}
	for _, o := range opts {
		o(&scratch)
	}
	defaultOptions, err := newDefaultHandlerOptions(verifySignatureFn, scratch.logger)
	if err != nil {
		return nil, err
	}

	mux := http.NewServeMux()
	registered := make([]string, 0, len(buildHandlers)+1)
	for _, b := range buildHandlers {
		path, providerServiceHandler := b(defaultOptions)
		mux.Handle(path, providerServiceHandler)
		registered = append(registered, strings.Trim(path, "/"))
	}
	registered = append(registered, systemconnect.SystemServiceName)

	systemBuild := Handler(systemconnect.NewSystemServiceHandler, systemconnect.SystemServiceHandler(newSystemServiceImpl(registered)))
	systemPath, systemHandler := systemBuild(defaultOptions)
	mux.Handle(systemPath, systemHandler)

	return mux, nil
}

func Handler[T any](handler func(svc T, option ...connect.HandlerOption) (string, http.Handler), p T, options ...HandlerOption) BuildHandler {
	return func(defaultOptions providerHandlerOptions) (string, http.Handler) {
		for _, o := range options {
			o(&defaultOptions)
		}
		path, h := handler(p, defaultOptions.connectHandlerOptions...)
		h = newSignatureVerifierMiddleware(defaultOptions.verifySignatureFn, defaultOptions.verifySignatureMaxBodySize)(h)
		return path, h
	}
}
