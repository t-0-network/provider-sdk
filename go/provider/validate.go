// Package provider exposes helpers used by provider services. Validate runs the
// same protovalidate rules that the response-validation interceptor enforces,
// but inside the handler's own call frame so the developer can see and react to
// failures before they reach the wire.
package provider

import (
	"errors"
	"fmt"
	"sync"

	"buf.build/go/protovalidate"
	"connectrpc.com/connect"
	"google.golang.org/protobuf/proto"
)

// validator is shared by every Validate call. protovalidate.New caches compiled
// rule programs per message descriptor, so re-using one instance avoids
// recompiling on each call. Construction is lazy because protovalidate.New can
// fail at process startup and we don't want to penalize callers who never use
// the helper.
var validator = sync.OnceValue(func() protovalidate.Validator {
	// protovalidate.GlobalValidator falls back to a lazily-initialised default
	// instance. Returning it here keeps the helper and the connect validate
	// interceptor pointed at the same cache.
	return protovalidate.GlobalValidator
})

// Validate runs protovalidate on msg and returns it unchanged on success.
// On failure it returns the zero value of T and an error whose message is
// prefixed "response validation failed: " so it surfaces consistently with the
// interceptor safety net.
//
// Usage:
//
//	resp, err := provider.Validate(&payment.PayOutResponse{ /* ... */ })
//	if err != nil {
//	    return nil, err
//	}
//	return connect.NewResponse(resp), nil
//
// Letting the returned error propagate keeps the wire response bit-identical to
// what the interceptor would have produced. Catching it lets the handler
// convert the failure into a domain-level error (for example, the Failed arm
// of a oneof result) before responding.
func Validate[T proto.Message](msg T) (T, error) {
	if err := validator().Validate(msg); err != nil {
		var zero T
		return zero, connect.NewError(connect.CodeInternal,
			fmt.Errorf("response validation failed: %w", err))
	}
	return msg, nil
}

// asValidationError extracts a *protovalidate.ValidationError from err if one
// is wrapped inside, returning nil otherwise. Exposed unexported so the
// interceptor and tests can share the same extraction logic.
func asValidationError(err error) *protovalidate.ValidationError {
	var ve *protovalidate.ValidationError
	if errors.As(err, &ve) {
		return ve
	}
	return nil
}
