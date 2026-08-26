package provider

import (
	"context"
	"log/slog"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"connectrpc.com/validate"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"

	"github.com/t-0-network/provider-sdk/go/sdkversion"
)

// newValidationInterceptor creates a ConnectRPC interceptor that validates
// provider responses against buf.validate proto annotations. Invalid responses
// are reported as errors with connect.CodeInternal — the wire shape is
// produced by connectrpc.com/validate and must remain unchanged.
//
// The returned interceptor adds one structured slog.Error line per validation
// failure (with rpc_method, response_type, violations, sdk_version fields) so
// providers see the failure even when their handler skipped the Validate
// helper. The original error is re-returned verbatim so callers downstream
// observe the same CodeInternal status and message.
func newValidationInterceptor(logger *slog.Logger) connect.Interceptor {
	inner := validate.NewInterceptor(validate.WithValidateResponses())
	return &loggingValidationInterceptor{
		inner:  inner,
		logger: logger,
	}
}

type loggingValidationInterceptor struct {
	inner  connect.Interceptor
	logger *slog.Logger
}

func (i *loggingValidationInterceptor) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	wrapped := i.inner.WrapUnary(next)
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		resp, err := wrapped(ctx, req)
		if err != nil {
			i.logIfResponseValidationFailure(req.Spec(), resp, err)
		}
		return resp, err
	}
}

func (i *loggingValidationInterceptor) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return i.inner.WrapStreamingClient(next)
}

func (i *loggingValidationInterceptor) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return i.inner.WrapStreamingHandler(next)
}

// logIfResponseValidationFailure emits a single error-level line when err is
// the response-validation failure produced by connectrpc.com/validate. Request
// validation failures (CodeInvalidArgument) and any other handler errors are
// ignored so we don't spam the log on unrelated failure paths.
func (i *loggingValidationInterceptor) logIfResponseValidationFailure(
	spec connect.Spec,
	resp connect.AnyResponse,
	err error,
) {
	if connect.CodeOf(err) != connect.CodeInternal {
		return
	}
	ve := asValidationError(err)
	if ve == nil {
		return
	}

	logger := i.logger
	if logger == nil {
		logger = slog.Default()
	}
	logger.Error(
		"response validation failed",
		slog.String("rpc_method", spec.Procedure),
		slog.String("response_type", responseTypeName(spec, resp)),
		slog.Any("violations", formatViolations(ve)),
		slog.String("sdk_version", sdkversion.Version),
	)
}

// responseTypeName returns the proto FQN of the response message. It prefers
// the actual response payload when present (success / partial-response paths),
// and falls back to the procedure's MethodDescriptor when the inner validate
// interceptor returned a nil response on failure. Returns "unknown" only when
// neither source carries proto metadata — which would indicate a non-protobuf
// custom transport.
func responseTypeName(spec connect.Spec, resp connect.AnyResponse) string {
	if resp != nil {
		if msg, ok := resp.Any().(proto.Message); ok {
			return string(msg.ProtoReflect().Descriptor().FullName())
		}
	}
	if md, ok := spec.Schema.(protoreflect.MethodDescriptor); ok {
		return string(md.Output().FullName())
	}
	return "unknown"
}

// formatViolations renders the violations list as a slice of short strings so
// slog handlers can serialise it as either a JSON array or a textual list
// without holding references to protobuf reflection objects.
func formatViolations(ve *protovalidate.ValidationError) []string {
	if ve == nil {
		return nil
	}
	out := make([]string, 0, len(ve.Violations))
	for _, v := range ve.Violations {
		out = append(out, v.String())
	}
	return out
}
