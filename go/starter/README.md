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
│   └── main.go                                # Entry point
├── internal/
│   ├── handler/
│   │   ├── payment.go                         # Phase 2: ProviderService handler
│   │   ├── payment_intent_pay_in.go           # Phase 3A: PayInProviderService handler
│   │   └── payment_intent_beneficiary.go      # Phase 3B: BeneficiaryService handler
│   ├── get_quote.go                           # Phase 1: quote retrieval
│   ├── publish_quotes.go                      # Phase 1: payout quote publishing
│   ├── publish_payment_intent_quotes.go       # Phase 3A: pay-in quote publishing
│   ├── get_payment_intent_quote.go            # Phase 3B: indicative quote retrieval
│   ├── create_payment_intent.go               # Phase 3B: create a payment intent
│   └── confirm_funds_received.go              # Phase 3A: confirm funds received
├── .env                                       # Environment variables (with generated keys)
├── .env.example                               # Example environment file
├── .gitignore                                 # Git ignore rules
├── Dockerfile                                 # Docker configuration
├── go.mod                                     # Go module definition
└── go.sum                                     # Go dependencies checksums
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

### Phase 3: Payment Intent Flow

The payment intent flow is independent of Phase 2. It is an asynchronous pay-in flow where an end-user pays a pay-in provider in fiat (bank transfer, mobile money, etc.) and a beneficiary provider receives settlement on the crypto side. Quotes are indicative until funds are received, settlement happens periodically, and a confirmation code links the end-user's payment back to a specific payment intent.

Implement **one** of the two sub-phases below depending on your role. If you participate on both sides, implement both.

**Phase 3A -- Pay-In Provider role** (skip if you're a beneficiary):

1. **Step 3A.1** Replace the sample pay-in quote publishing in `internal/publish_payment_intent_quotes.go` with your own.
2. **Step 3A.2** Implement `GetPaymentDetails` in `internal/handler/payment_intent_pay_in.go` -- return bank account / mobile money details plus a payment reference the end-user will include in their transfer.
3. **Step 3A.3** When you detect the end-user's fiat payment, call `ConfirmFundsReceived` (see `internal/confirm_funds_received.go`).

**Phase 3B -- Beneficiary Provider role** (skip if you're pay-in):

1. **Step 3B.1** Verify indicative quotes are returned (`internal/get_payment_intent_quote.go`).
2. **Step 3B.2** Create payment intents for your end-users via `CreatePaymentIntent` (see `internal/create_payment_intent.go`).
3. **Step 3B.3** Implement `PaymentIntentUpdate` in `internal/handler/payment_intent_beneficiary.go` to receive notifications when funds are received.

If you only play one role, delete the files for the other role and remove the corresponding handler registration in `cmd/main.go`.

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
