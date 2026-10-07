package provider

import (
	"context"
	"fmt"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// Header names carrying the identity of the SDK answering the probe. They ride
// on the health response and nowhere else: grpc.health.v1's CheckResponse has a
// single status field and Check names its service in the request, so the
// contract itself has no room for this — and taxing every callback with headers
// only the probe reads would be worse.
const (
	SDKEcosystemHeader = "T0-Sdk-Ecosystem"
	SDKVersionHeader   = "T0-Sdk-Version"
)

const sdkEcosystem = "go"

// healthChecker reports SERVING for the services registered on this server, and
// NOT_FOUND for anything else. The set is frozen at construction; nothing is
// computed per request.
//
// It implements grpchealth.Checker but not grpchealth.Watcher, so the package's
// handler answers Watch with UNIMPLEMENTED, as in every other SDK.
type healthChecker struct {
	registered map[string]struct{}
}

func newHealthChecker(services []string) *healthChecker {
	registered := make(map[string]struct{}, len(services))
	for _, name := range services {
		registered[name] = struct{}{}
	}
	return &healthChecker{registered: registered}
}

func (h *healthChecker) Check(_ context.Context, req *grpchealth.CheckRequest) (*grpchealth.CheckResponse, error) {
	// An empty service name asks about the process as a whole, which is up if
	// this handler is running at all.
	if req.Service == "" {
		return &grpchealth.CheckResponse{Status: grpchealth.StatusServing}, nil
	}
	if _, ok := h.registered[req.Service]; !ok {
		return nil, connect.NewError(connect.CodeNotFound, fmt.Errorf(contract.UnknownService, req.Service))
	}
	return &grpchealth.CheckResponse{Status: grpchealth.StatusServing}, nil
}

// withSDKIdentity stamps the SDK identity onto every Check reply, NotFound
// included. The headers go through the call's CallInfo, which connect-go sends
// on an error too, over every protocol.
func withSDKIdentity(version string) connect.HandlerOption {
	return connect.WithInterceptors(connect.UnaryInterceptorFunc(
		func(next connect.UnaryFunc) connect.UnaryFunc {
			return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
				if info, ok := connect.CallInfoForHandlerContext(ctx); ok {
					info.ResponseHeader().Set(SDKEcosystemHeader, sdkEcosystem)
					info.ResponseHeader().Set(SDKVersionHeader, version)
				}
				return next(ctx, req)
			}
		},
	))
}
