# my-provider

T-0 Network provider implementation generated from the official C# starter.

## Quick Start

```bash
dotnet run
```

Share the provider public key (printed by the initializer; also on the comment line under `# Your provider's public key` in `.env`) with the T-0 team so t-0 can verify the requests you sign.

## Generated Project Structure

```
my-provider/
├── Services/
│   ├── PaymentHandler.cs        # ProviderService implementation (modify this)
│   ├── QuotePublisher.cs        # Quote publishing logic (modify this)
│   ├── GetQuote.cs              # Quote fetching utility
│   ├── SubmitPayment.cs         # Payment submission utility
│   └── CompleteManualAmlCheck.cs # Manual AML check completion utility
├── Program.cs                   # Entry point
├── my-provider.csproj           # Build configuration
├── appsettings.json             # ASP.NET Core configuration
├── .env                         # Your configuration (git-ignored)
├── .env.example                 # Example environment file
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

1. Open `.env` and copy the public key on the comment line under `# Your provider's public key`. Share it with the T-0 team to register your provider.
2. Replace sample quote publishing logic in `Services/QuotePublisher.cs`.
3. Start the application: `dotnet run`
4. Verify quotes are received by checking application logs.

### Phase 2: Payments

1. Implement `UpdatePayment` handler in `Services/PaymentHandler.cs`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `PayOut` handler in `Services/PaymentHandler.cs`.
4. Test payment submission using the included `SubmitPayment` utility.
5. Coordinate with the T-0 team to test end-to-end payment flows.
6. Optional: if your `PayOut` handler responds with a manual AML check, report the outcome using the included `CompleteManualAmlCheck` utility.

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
| `PROVIDER_PRIVATE_KEY is required` | `.env` is generated with a fresh key next to the `.csproj`; run from that directory. To generate a new key, run `t0-init keygen` and set `PROVIDER_PRIVATE_KEY` to the private key it prints. |
| Signature verification failures | Ensure system clock is synchronized (NTP). Tolerance is +/- 60 seconds. |
| gRPC connection refused | Verify `TZERO_ENDPOINT` is correct and reachable. |
| Port already in use | Change `PORT` in `.env` or stop the conflicting process. |

## SDK Reference

For direct SDK usage (without the starter), see the [C# SDK documentation](https://github.com/t-0-network/provider-sdk/tree/master/csharp/sdk).
