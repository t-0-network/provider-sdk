package provider

import (
	"net/http"
	"strings"

	"connectrpc.com/connect"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/system/systemconnect"
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
