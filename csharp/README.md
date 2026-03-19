# T-0 Provider SDK -- C#

C# SDK and starter CLI for building T-0 Network payment provider integrations. The SDK provides a gRPC-based framework with automatic secp256k1 cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **.NET** 10.0 or later

## Quick Start

```bash
dotnet tool install -g T0.ProviderStarter
t0-provider-starter my-provider
cd my-provider
dotnet run
```

Or run directly from source:

```bash
dotnet run --project starter/T0.ProviderStarter -- my-provider
```

This creates a ready-to-run project with a secp256k1 keypair, environment config, provider service stubs, and a Dockerfile.

## Generated Project Structure

```
your-project/
├── Services/
│   ├── PaymentHandler.cs        # ProviderService implementation (modify this)
│   ├── QuotePublisher.cs        # Quote publishing logic (modify this)
│   ├── GetQuote.cs              # Quote fetching utility
│   └── SubmitPayment.cs         # Payment submission utility
├── Program.cs                   # Entry point
├── your-project.csproj          # Build configuration
├── appsettings.json             # ASP.NET Core configuration
├── .env                         # Your configuration (git-ignored)
└── Dockerfile                   # Docker deployment
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `Services/PaymentHandler.cs` | Implement your payment processing logic. Look for `TODO` comments. |
| `Services/QuotePublisher.cs` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (64-char hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | No | `8080` | Provider server port |
| `QUOTE_PUBLISHING_INTERVAL` | No | `5000` | Quote publishing interval in milliseconds |

## Getting Started

### Phase 1: Quoting

1. Initialize your project using the quick start above.
2. Share your public key with the T-0 team (displayed on first run).
3. Replace sample quote publishing logic in `Services/QuotePublisher.cs`.
4. Start the application: `dotnet run`
5. Verify quotes are received by checking application logs.

### Phase 2: Payments

1. Implement `UpdatePayment` handler in `Services/PaymentHandler.cs`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `PayOut` handler in `Services/PaymentHandler.cs`.
4. Test payment submission using the included `SubmitPayment` utility.
5. Coordinate with the T-0 team to test end-to-end payment flows.

## Installation

To use the SDK directly without the starter CLI, add the NuGet package:

```bash
dotnet add package T0.ProviderSdk
```

## Available Commands

```bash
dotnet run                 # Run the application
dotnet build               # Build the project
dotnet test                # Run tests
```

## Deployment

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `PROVIDER_PRIVATE_KEY is required` | Copy `.env.example` to `.env` and fill in your private key |
| Signature verification failures | Ensure system clock is synchronized (NTP). Tolerance is +/- 60 seconds |
| gRPC connection refused | Verify `TZERO_ENDPOINT` is correct and reachable |
| Port already in use | Change `PORT` in `.env` or stop the conflicting process |
