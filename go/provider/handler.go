package provider

import (
	"net/http"
	"strings"

	"connectrpc.com/connect"

	"connectrpc.com/grpchealth"
)

type BuildHandler func(defaultOptions providerHandlerOptions) (path string, handler http.Handler)

// T-ZERO Network Public Key, required for signature verification.
type NetworkPublicKeyHexed string

// NewHttpHandler returns a ready-to-use *http.ServeMux with the
// networkconnect.ProviderServiceHandler registered.
//
// It creates a new HTTP mux, registers the provided ProviderServiceHandler on the appropriate path,
// and returns the mux for immediate use in your HTTP server.
//
// Parameters:
//   - service: An implementation of the networkconnect.ProviderServiceHandler interface.
//
// Returns:
//   - *http.ServeMux: An HTTP mux with the provider service handler registered.
func NewHttpHandler(
	networkPublicKey NetworkPublicKeyHexed,
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
	defaultOptions, err := newDefaultHandlerOptions(verifySignatureFn)
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
			o.connectHandlerOptions = append(o.connectHandlerOptions, withSDKIdentity())
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
