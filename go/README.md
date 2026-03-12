# T-0 Provider SDK -- Go

Go SDK for building provider integrations with the T-0 Network. Handles secp256k1 cryptographic signing, signature verification, and provides typed ConnectRPC clients for all T-0 Network APIs.

## Quick Start

Bootstrap a new provider project:

```bash
go run github.com/t-0-network/provider-sdk/go/starter@latest my-provider
```

See [starter README](starter/README.md) for details on the generated project.

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
    confirmPayoutReq := &networkproto.ConfirmPayoutRequest{
        PaymentId: msg.GetPaymentId(),
        PayoutId:  msg.GetPayoutId(),
        Result: &networkproto.ConfirmPayoutRequest_Success_{
            Success: &networkproto.ConfirmPayoutRequest_Success{},
        },
    }

    _, err := s.networkClient.ConfirmPayout(ctx, connect.NewRequest(confirmPayoutReq))
    if err != nil {
        return nil, connect.NewError(connect.CodeInternal, err)
    }

    return connect.NewResponse(&networkproto.PayoutResponse{}), nil
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
```

Initialize the provider handler and start the server:

```go
networkPublicKey := "0x049bb924..."
var handler providerconnect.ProviderServiceHandler = &ProviderServiceImplementation{}
providerServiceHandler, err := provider.NewProviderHandler(
    provider.NetworkPublicKeyHexed(networkPublicKey),
    provider.Handler(providerconnect.NewProviderServiceHandler, handler),
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
_, err = networkClient.UpdateQuote(ctx, connect.NewRequest(&networkproto.UpdateQuoteRequest{...}))

// Get a quote
_, err = networkClient.GetPayoutQuote(ctx, connect.NewRequest(&networkproto.GetPayoutQuoteRequest{...}))

// Create payment
_, err = networkClient.CreatePayment(ctx, connect.NewRequest(&networkproto.CreatePaymentRequest{...}))
```

**Client options:** `WithBaseURL` (default: `https://api.t-0.network`), `WithTimeout` (default: 15s), `WithSignatureFunction`, `WithConnectOptions`.

## Examples

- [Payout Provider Flow](examples/payout_provider_flow_test.go)
- [Provider Service](examples/provider_service_test.go)
- [Network Client](examples/network_client_test.go)
- [Payment Intent Pay-in](examples/payment_intent/pay_in_flow_test.go)

## Development

```bash
go build ./...       # Build
go test ./...        # Run tests
go fmt ./...         # Format code
go vet ./...         # Static analysis
```
