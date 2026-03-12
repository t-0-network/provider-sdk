# T-0 Provider Starter -- Go

CLI tool to scaffold a Go provider project for the T-0 Network.

## Quick Start

```bash
go run github.com/t-0-network/provider-sdk/go/starter@latest my-provider
```

This creates a ready-to-run project with a secp256k1 keypair, environment config, provider service stubs, and a Dockerfile.

## Generated Project Structure

```
my-provider/
├── cmd/
│   └── main.go              # Entry point
├── internal/
│   ├── handler/
│   │   ├── provider.go      # Provider service implementation
│   │   └── payment.go       # Payment handler implementation
│   ├── get_quote.go         # Quote retrieval logic
│   ├── publish_quotes.go    # Quote publishing logic
│   └── service.go           # Service utilities
├── .env                     # Environment variables (with generated keys)
├── .env.example             # Example environment file
├── .gitignore               # Git ignore rules
├── Dockerfile               # Docker configuration
├── go.mod                   # Go module definition
└── go.sum                   # Go dependencies checksums
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `internal/handler/payment.go` | Implement your payment processing logic. Look for `TODO` comments. |
| `internal/publish_quotes.go` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | No | `8080` | Server port |
| `QUOTE_PUBLISHING_INTERVAL` | No | -- | Quote publishing frequency in milliseconds |

## Getting Started

### Phase 1: Quoting

1. Review the generated keys in `.env` -- share your public key (shown as a comment) with the T-0 team to register your provider.
2. Edit `internal/publish_quotes.go` to implement your quote publishing logic.
3. Start the development server: `go run ./cmd/main.go`
4. Verify quotes are received by the network.

### Phase 2: Payments

1. Implement `UpdatePayment` handler in `internal/handler/payment.go`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `PayOut` handler in `internal/handler/payment.go`.
4. Coordinate with the T-0 team to test end-to-end payment flows.

## Available Commands

```bash
go run ./cmd/main.go         # Run in development mode
go build -o provider ./cmd/  # Build for production
go test ./...                # Run tests
go fmt ./...                 # Format code
go vet ./...                 # Static analysis
```

## Deployment

```bash
docker build -t my-provider:latest .
docker run -p 8080:8080 --env-file .env my-provider:latest
```

## SDK Reference

For direct SDK usage (without the starter), see the [Go SDK documentation](../README.md).

## Troubleshooting

**"Directory already exists"** -- Choose a different project name.

**"Go not found"** -- Install Go from [golang.org](https://golang.org/dl/) and ensure it's in your PATH.

**Module download fails** -- Check your internet connection and Go module proxy settings: `go env GOPROXY`.
