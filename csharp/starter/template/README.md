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
│   ├── QuotePublisherService.cs # Periodic timer that calls QuotePublisher
│   ├── GetQuote.cs              # Quote sample; runs after the server is listening
│   ├── SubmitPayment.cs         # Payment submission utility
│   └── CompleteManualAmlCheck.cs # Manual AML check completion utility
├── Program.cs                   # Entry point
├── Config.cs                    # Reads the environment variables below
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

`t0-init` creates `.env` from `.env.example` and writes a new private key into it. The other values in that file are for the sandbox. For production, the T-0 team gives you the network public key and the endpoint.

The process environment wins over `.env`. An empty value counts as unset. A missing `.env` is not a failure: the process prints one notice on stderr and continues with the environment.

| Variable | In `.env` after `t0-init` | If unset or empty | Description |
|----------|---------------------------|-------------------|-------------|
| `PROVIDER_PRIVATE_KEY` | A new key generated for this project | The provider does not start | Your secp256k1 private key (64-char hex). Surrounding whitespace is ignored. |
| `NETWORK_PUBLIC_KEY` | The sandbox network key | The provider does not start | T-0 Network public key for signature verification. Surrounding whitespace is ignored. |
| `TZERO_ENDPOINT` | `https://api-sandbox.t-0.network` | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | `8080` | `8080` | Provider server port, an integer from 1 to 65535 |
| `QUOTE_PUBLISHING_INTERVAL` | not set | `5000` | Quote publishing interval in milliseconds |

ASP.NET Core also reads configuration from the environment. `Kestrel__Endpoints__*` replaces the address `PORT` selects, rather than listening beside it. The server speaks gRPC over cleartext HTTP/2 only.

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
| `PROVIDER_PRIVATE_KEY is not set` | Run from the directory that holds `.env`, or set `PROVIDER_PRIVATE_KEY` in the environment. An existing `.env` already has the generated key; its private half cannot be recovered from the public key. |
| Signature verification failures | Ensure system clock is synchronized (NTP). Tolerance is +/- 60 seconds. |
| gRPC connection refused | Verify `TZERO_ENDPOINT` is correct and reachable. |
| Port already in use | Change `PORT` in `.env` or stop the conflicting process. |

## SDK Reference

For direct SDK usage (without the starter), see the [C# SDK documentation](https://github.com/t-0-network/provider-sdk/tree/master/csharp/sdk).
