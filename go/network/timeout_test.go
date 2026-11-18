package network

import (
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/require"
	commonv1 "github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/reflect/protoregistry"
	"google.golang.org/protobuf/types/descriptorpb"
	"google.golang.org/protobuf/types/known/durationpb"
	_ "google.golang.org/protobuf/types/known/emptypb"
)

// A method's timeout option replaces the client's timeout, shorter or longer; a method without
// one, or with one that is not positive, gets the client's timeout.
func TestTimeoutOf(t *testing.T) {
	const fallback = 45 * time.Second
	paymentServices := payment.File_tzero_v1_payment_provider_proto.Services()
	networkServices := payment.File_tzero_v1_payment_network_proto.Services()
	tests := []struct {
		name   string
		schema any
		want   time.Duration
	}{
		{"ApprovePaymentQuotes, 29 s", paymentServices.ByName("ProviderService").Methods().ByName("ApprovePaymentQuotes"), 29 * time.Second},
		{"network UpdateQuote, 30 s", networkServices.ByName("NetworkService").Methods().ByName("UpdateQuote"), 30 * time.Second},
		{"no schema", nil, fallback},
		{"no option", methodWithTimeout(t, "none", nil), fallback},
		{"option of zero", methodWithTimeout(t, "zero", &durationpb.Duration{}), fallback},
		{"negative option", methodWithTimeout(t, "negative", &durationpb.Duration{Seconds: -1}), fallback},
		{"option of 90 s", methodWithTimeout(t, "longer", &durationpb.Duration{Seconds: 90}), 90 * time.Second},
		{"option with nanos", methodWithTimeout(t, "nanos", &durationpb.Duration{Seconds: 1, Nanos: 500_000_000}), 1500 * time.Millisecond},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			require.Equal(t, tt.want, timeoutOf(connect.Spec{Schema: tt.schema}, fallback))
		})
	}
}

// methodWithTimeout is method M of a service built for the test, with timeout as its timeout
// option, or no option when timeout is nil.
func methodWithTimeout(t *testing.T, name string, timeout *durationpb.Duration) protoreflect.MethodDescriptor {
	t.Helper()
	options := &descriptorpb.MethodOptions{}
	if timeout != nil {
		proto.SetExtension(options, commonv1.E_Timeout, timeout)
	}
	file, err := protodesc.NewFile(&descriptorpb.FileDescriptorProto{
		Name:       new("timeout_" + name + ".proto"),
		Package:    new("timeouttest." + name),
		Syntax:     new("proto3"),
		Dependency: []string{"google/protobuf/empty.proto"},
		Service: []*descriptorpb.ServiceDescriptorProto{{
			Name: new("S"),
			Method: []*descriptorpb.MethodDescriptorProto{{
				Name:       new("M"),
				InputType:  new(".google.protobuf.Empty"),
				OutputType: new(".google.protobuf.Empty"),
				Options:    options,
			}},
		}},
	}, protoregistry.GlobalFiles)
	require.NoError(t, err)
	return file.Services().ByName("S").Methods().ByName("M")
}
