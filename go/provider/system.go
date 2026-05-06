package provider

import (
	"context"
	"time"

	"connectrpc.com/connect"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/system"
	"github.com/t-0-network/provider-sdk/go/sdkversion"
)

type systemServiceImpl struct {
	services []string
}

func newSystemServiceImpl(services []string) *systemServiceImpl {
	return &systemServiceImpl{services: services}
}

func (s *systemServiceImpl) Health(_ context.Context, _ *connect.Request[system.HealthRequest]) (*connect.Response[system.HealthResponse], error) {
	return connect.NewResponse(&system.HealthResponse{
		Services:     s.services,
		CurrentTime:  timestamppb.New(time.Now()),
		SdkVersion:   sdkversion.Version,
		SdkEcosystem: system.SdkEcosystem_SDK_ECOSYSTEM_GO,
	}), nil
}
