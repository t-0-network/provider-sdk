package provider

import (
	"connectrpc.com/connect"
	"connectrpc.com/validate"
)

// newValidationInterceptor creates a ConnectRPC interceptor that validates
// provider responses against buf.validate proto annotations.
// Invalid responses are reported as errors.
func newValidationInterceptor() connect.Interceptor {
	return validate.NewInterceptor(validate.WithValidateResponses())
}
