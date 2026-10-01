package provider

import (
	"context"

	"connectrpc.com/connect"
)

// signatureErrorInterceptor fails a call whose request the signature verification middleware
// rejected, before its handler runs. A streaming handler is not started, so it never receives a
// message, and neither is a server stream's single request message read.
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
