package provider

import (
	"context"

	"connectrpc.com/connect"
)

// signatureErrorInterceptor fails a call that has no accepted signature verdict in its context,
// before its handler runs. The signature verification middleware answers a rejected request
// itself, so this is a call that did not pass through the middleware (ErrNoSignatureResult). A
// streaming handler is not started, so it never receives a message, and neither is a server
// stream's single request message read.
type signatureErrorInterceptor struct{}

func (signatureErrorInterceptor) WrapUnary(next connect.UnaryFunc) connect.UnaryFunc {
	return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
		if _, err := SignatureVerification(ctx); err != nil {
			return nil, err
		}
		return next(ctx, req)
	}
}

func (signatureErrorInterceptor) WrapStreamingClient(next connect.StreamingClientFunc) connect.StreamingClientFunc {
	return next
}

func (signatureErrorInterceptor) WrapStreamingHandler(next connect.StreamingHandlerFunc) connect.StreamingHandlerFunc {
	return func(ctx context.Context, conn connect.StreamingHandlerConn) error {
		if _, err := SignatureVerification(ctx); err != nil {
			return err
		}
		return next(ctx, conn)
	}
}
