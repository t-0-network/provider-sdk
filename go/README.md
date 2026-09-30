# T-0 Provider SDK -- Go

Go SDK for building provider integrations with the T-0 Network. Handles secp256k1 cryptographic signing, signature verification, and provides typed ConnectRPC clients for all T-0 Network APIs.

## Quick Start

Scaffold a new provider project with one command:

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh -s -- --lang=go my-provider
```

All flags and the install-only form: [cli/README.md](../cli/README.md). What `init` creates: [starter template README](starter/template/README.md).

## Installation

```bash
go get github.com/t-0-network/provider-sdk/go
```

## Usage

### Provider Service

Implement the `ProviderServiceHandler` interface to receive callbacks from the T-0 Network (payment updates, payout requests, etc.):

```go
package impl

import (
    "context"
    "connectrpc.com/connect"
    networkproto "github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
    "github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
)

type ProviderServiceImplementation struct{
    networkClient paymentconnect.NetworkServiceClient
}

func (s *ProviderServiceImplementation) PayOut(ctx context.Context, req *connect.Request[networkproto.PayoutRequest],
) (*connect.Response[networkproto.PayoutResponse], error) {
    msg := req.Msg
    finalizePayoutReq := &networkproto.FinalizePayoutRequest{
        PaymentId: msg.GetPaymentId(),
        Result: &networkproto.FinalizePayoutRequest_Success_{
            Success: &networkproto.FinalizePayoutRequest_Success{},
        },
    }

    _, err := s.networkClient.FinalizePayout(ctx, connect.NewRequest(finalizePayoutReq))
    if err != nil {
        return nil, connect.NewError(connect.CodeInternal, err)
    }

    return connect.NewResponse(&networkproto.PayoutResponse{
        Result: &networkproto.PayoutResponse_Accepted_{Accepted: &networkproto.PayoutResponse_Accepted{}},
    }), nil
}

func (s *ProviderServiceImplementation) UpdatePayment(
    ctx context.Context, req *connect.Request[networkproto.UpdatePaymentRequest],
) (*connect.Response[networkproto.UpdatePaymentResponse], error) {
    return connect.NewResponse(&networkproto.UpdatePaymentResponse{}), nil
}

func (s *ProviderServiceImplementation) UpdateLimit(
    ctx context.Context, req *connect.Request[networkproto.UpdateLimitRequest],
) (*connect.Response[networkproto.UpdateLimitResponse], error) {
    return connect.NewResponse(&networkproto.UpdateLimitResponse{}), nil
}

func (s *ProviderServiceImplementation) AppendLedgerEntries(
    ctx context.Context, req *connect.Request[networkproto.AppendLedgerEntriesRequest],
) (*connect.Response[networkproto.AppendLedgerEntriesResponse], error) {
    return connect.NewResponse(&networkproto.AppendLedgerEntriesResponse{}), nil
}

func (s *ProviderServiceImplementation) ApprovePaymentQuotes(
    ctx context.Context, req *connect.Request[networkproto.ApprovePaymentQuoteRequest],
) (*connect.Response[networkproto.ApprovePaymentQuoteResponse], error) {
    return connect.NewResponse(&networkproto.ApprovePaymentQuoteResponse{}), nil
}
```

Initialize the provider handler and start the server:

```go
networkPublicKey := "0x049bb924..."
var handler paymentconnect.ProviderServiceHandler = &ProviderServiceImplementation{}
providerServiceHandler, err := provider.NewHttpHandler(
    provider.NetworkPublicKeyHexed(networkPublicKey),
    provider.Handler(paymentconnect.NewProviderServiceHandler, handler),
)
if err != nil {
    log.Fatalf("Failed to create provider service handler: %v", err)
}

// Start server (HTTP/2 cleartext enabled automatically via h2c)
shutdownFunc, err := provider.StartServer(
    providerServiceHandler,
    provider.WithAddr(":8080"),
)
```

Or create an HTTP server instance without starting it, for use with your own server setup:

```go
server := provider.NewServer(providerServiceHandler, provider.WithAddr(":8080"))
```

**Server options:** `WithAddr`, `WithReadTimeout`, `WithWriteTimeout`, `WithReadHeaderTimeout`, `WithShutdownTimeout`, `WithTLSConfig`, `WithHTTP2Config`.

**Handler options:** `WithVerifySignatureFn`, `WithConnectHandlerOptions`, `WithMaxBodySize` (default: 1 MB).

### Network Client

Use `NewServiceClient` to call T-0 Network APIs. The client handles request signing automatically:

```go
import (
    "context"
    "log"

    "connectrpc.com/connect"
    networkproto "github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
    "github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
    "github.com/t-0-network/provider-sdk/go/network"
)

privateKey := network.PrivateKeyHexed("0x7795db2f...")

networkClient, err := network.NewServiceClient(privateKey, paymentconnect.NewNetworkServiceClient)
if err != nil {
    log.Fatalf("Failed to create network service client: %v", err)
}

// Publish quotes
_, err = networkClient.UpdateQuote(ctx, connect.NewRequest(&networkproto.UpdateQuoteRequest{ /* ... */ }))

// Get a quote
_, err = networkClient.GetQuote(ctx, connect.NewRequest(&networkproto.GetQuoteRequest{ /* ... */ }))

// Create payment
_, err = networkClient.CreatePayment(ctx, connect.NewRequest(&networkproto.CreatePaymentRequest{ /* ... */ }))
```

**Client options:** `WithBaseURL` (default: `https://api.t-0.network`), `WithTimeout` (unary calls, default: 15s), `WithStreamTimeout` (streaming calls, default: 5 min), `WithWireFormat` (`WireFormatBinary` default, `WireFormatJSON`), `WithProtocol` (`ProtocolConnect` default, `ProtocolGRPC`), `WithSignatureFunction`.

#### Streaming and timeouts

Client-streaming and server-streaming calls are signed over their first request message. The request goes out as soon as that message is sent, and later messages are not buffered. Bidirectional-streaming calls fail with `CodeUnimplemented` and send nothing.

A unary call gets a 15 second deadline and a stream gets 5 minutes, unless the call's context has a deadline of its own, which then applies instead, shorter or longer.

```go
// A client-streaming method, called through connect.NewClient; a generated client works the same way.
upload := func(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) *connect.Client[wrapperspb.StringValue, wrapperspb.StringValue] {
    return connect.NewClient[wrapperspb.StringValue, wrapperspb.StringValue](httpClient, baseURL+"/example.v1.UploadService/Upload", opts...)
}
uploadClient, err := network.NewServiceClient(privateKey, upload,
    network.WithStreamTimeout(30*time.Minute), // every stream of this client may run up to 30 minutes
)
if err != nil {
    log.Fatalf("Failed to create upload client: %v", err)
}

stream := uploadClient.CallClientStream(ctx)
if err := stream.Send(wrapperspb.String("first chunk")); err != nil { /* ... */ } // signs and sends the request
if err := stream.Send(wrapperspb.String("next chunk")); err != nil { /* ... */ }
resp, err := stream.CloseAndReceive()
```

Details: [`docs/STREAMING.md`](../docs/STREAMING.md).

## Examples

The [starter template](starter/template/) is a complete provider: the server and handlers in `internal/handler/`, network client calls in `internal/` and `cmd/main.go`.

## Development

```bash
go build ./...       # Build
go test ./...        # Run tests
go fmt ./...         # Format code
go vet ./...         # Static analysis
```
