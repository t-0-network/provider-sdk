package provider

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"connectrpc.com/validate"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// newValidationInterceptor creates a ConnectRPC interceptor that validates
// requests and responses against buf.validate proto annotations. An invalid
// request is refused by connectrpc.com/validate with connect.CodeInvalidArgument.
// An invalid response, of a unary call or any message of a stream, becomes
// connect.CodeInternal "response validation failed: <field>: <message>[; …]",
// the error Validate returns.
//
// The interceptor adds one structured slog.Error line per response validation
// failure (with rpc_method, response_type, violations, sdk_version fields) so
// providers see the failure even when their handler skipped the Validate
// helper. sdkVersion is the version the server reports, WithSDKVersion's when
// set.
func newValidationInterceptor(logger *slog.Logger, sdkVersion string) connect.Interceptor {
	return &loggingValidationInterceptor{
		requests:   validate.NewInterceptor(),
		logger:     logger,
		sdkVersion: sdkVersion,
	}
}

type loggingValidationInterceptor struct {
	requests   connect.Interceptor
	logger     *slog.Logger
	sdkVersion string
}

func (i *loggingValidationInterceptor) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	wrapped := i.requests.WrapUnary(next)
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		resp, err := wrapped(ctx, req)
		if err != nil {
			// A handler that returned Validate's error.
			if ve := asValidationError(err); ve != nil && connect.CodeOf(err) == connect.CodeInternal {
				i.logFailure(req.Spec(), resp, ve)
			}
			return resp, err
		}
		if err := i.validateResponse(req.Spec(), resp.Any()); err != nil {
			return nil, err
		}
		return resp, nil
	}
}

func (i *loggingValidationInterceptor) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return i.requests.WrapStreamingClient(next)
}

func (i *loggingValidationInterceptor) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return i.requests.WrapStreamingHandler(func(ctx context.Context, conn connect.StreamingHandlerConn) error {
		return next(ctx, &validatingHandlerConn{StreamingHandlerConn: conn, interceptor: i})
	})
}

// validatingHandlerConn validates each message a stream handler sends.
type validatingHandlerConn struct {
	connect.StreamingHandlerConn
	interceptor *loggingValidationInterceptor
}

func (c *validatingHandlerConn) Send(msg any) error {
	if err := c.interceptor.validateResponse(c.Spec(), msg); err != nil {
		return err
	}
	return c.StreamingHandlerConn.Send(msg)
}

// validateResponse validates msg and, when it is invalid, logs the failure and
// returns the error Validate returns.
func (i *loggingValidationInterceptor) validateResponse(spec connect.Spec, msg any) error {
	protoMsg, ok := msg.(proto.Message)
	if !ok || protoMsg == nil {
		return nil
	}
	err := validator().Validate(protoMsg)
	if err == nil {
		return nil
	}
	var ve *protovalidate.ValidationError
	if !errors.As(err, &ve) {
		// A rule that could not be evaluated.
		return connect.NewError(connect.CodeInternal, fmt.Errorf(contract.ResponseValidationError, err))
	}
	i.logFailure(spec, protoMsg, ve)
	return newResponseValidationError(ve)
}

// logFailure emits a single error-level line for a response that failed
// protovalidate. resp is the response (a connect.AnyResponse or the message
// itself), nil when there is none.
func (i *loggingValidationInterceptor) logFailure(spec connect.Spec, resp any, ve *protovalidate.ValidationError) {
	logger := i.logger
	if logger == nil {
		logger = slog.Default()
	}
	logger.Error(
		"response validation failed",
		slog.String("rpc_method", spec.Procedure),
		slog.String("response_type", responseTypeName(spec, resp)),
		slog.Any("violations", formatViolations(ve)),
		slog.String("sdk_version", i.sdkVersion),
	)
}

// newResponseValidationError is the error of a response that fails
// protovalidate: connect.CodeInternal "response validation failed: <field>:
// <message>[; …]", with the violations as a buf.validate.Violations detail.
func newResponseValidationError(ve *protovalidate.ValidationError) *connect.Error {
	connectErr := connect.NewError(connect.CodeInternal, &responseValidationError{ve: ve})
	if detail, err := connect.NewErrorDetail(ve.ToProto()); err == nil {
		connectErr.AddDetail(detail)
	}
	return connectErr
}

type responseValidationError struct {
	ve *protovalidate.ValidationError
}

func (e *responseValidationError) Error() string {
	details := make([]string, 0, len(e.ve.Violations))
	for _, v := range e.ve.Violations {
		details = append(details, protovalidate.FieldPathString(v.Proto.GetField())+": "+v.Proto.GetMessage())
	}
	return fmt.Sprintf(contract.ResponseInvalid, strings.Join(details, "; "))
}

func (e *responseValidationError) Unwrap() error { return e.ve }

// responseTypeName returns the proto FQN of the response message. It prefers
// the actual response payload when present (success / partial-response paths),
// and falls back to the procedure's MethodDescriptor when there is no response
// (a handler that returned Validate's error). Returns "unknown" only when
// neither source carries proto metadata — which would indicate a non-protobuf
// custom transport.
func responseTypeName(spec connect.Spec, resp any) string {
	if r, ok := resp.(connect.AnyResponse); ok && r != nil {
		resp = r.Any()
	}
	if msg, ok := resp.(proto.Message); ok && msg != nil {
		return string(msg.ProtoReflect().Descriptor().FullName())
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
