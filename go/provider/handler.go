package provider

import (
	"log/slog"
	"net/http"
	"strings"

	"connectrpc.com/connect"

	"connectrpc.com/grpchealth"

	"github.com/t-0-network/provider-sdk/go/sdkversion"
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

// WithSDKVersion overrides the SDK version reported in health-check response
// headers. Wrapping SDKs use this to stamp their own version instead of the
// provider-sdk's built-in version.
func WithSDKVersion(version string) HttpHandlerOption {
	return func(o *providerHandlerOptions) {
		if strings.TrimSpace(version) != "" {
			o.sdkVersion = version
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

	sdkVer := scratch.sdkVersion
	if sdkVer == "" {
		sdkVer = sdkversion.Version
	}

	mux := http.NewServeMux()
	registered := make([]string, 0, len(buildHandlers)+1)
	for _, b := range buildHandlers {
		path, providerServiceHandler := b(defaultOptions)
		mux.Handle(path, providerServiceHandler)
		registered = append(registered, strings.Trim(path, "/"))
	}
	// The Network has to establish that an endpoint is reachable before it sends
	// anything there, and grpc.health.v1.Health is the only service this transport
	// can mount to say so: it belongs to no business protocol, so serving it makes
	// no claim about what the server is — which matters, because this transport
	// also builds servers that are not providers'. Same middleware as everything
	// else, so the probe is signed like any other call.
	registered = append(registered, grpchealth.HealthV1ServiceName)

	healthBuild := Handler(
		grpchealth.NewHandler,
		grpchealth.Checker(newHealthChecker(registered)),
		func(o *providerHandlerOptions) {
			o.connectHandlerOptions = append(o.connectHandlerOptions, withSDKIdentity(sdkVer))
		},
	)
	healthPath, healthHandler := healthBuild(defaultOptions)
	mux.Handle(healthPath, healthHandler)

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
