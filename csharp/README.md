# T-0 Provider SDK -- C#

C# SDK for building T-0 Network payment provider integrations. The SDK provides a gRPC-based framework with automatic secp256k1 cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **.NET** 10.0 or later

## Quick Start

Scaffold a new provider project with one command:

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh -s -- --lang=csharp my-provider
```

All flags and the install-only form: [cli/README.md](../cli/README.md). What `init` creates: [starter template README](starter/template/README.md).

## Installation

To use the SDK directly, add the NuGet package:

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
| `PROVIDER_PRIVATE_KEY is required` | `.env` is generated with a fresh key next to the `.csproj`; run from that directory. To generate a new key, run `t0-init keygen` and set `PROVIDER_PRIVATE_KEY` to the private key it prints (see [`cli/README.md`](../cli/README.md)) |
| Signature verification failures | Ensure system clock is synchronized (NTP). Tolerance is +/- 60 seconds |
| gRPC connection refused | Verify `TZERO_ENDPOINT` is correct and reachable |
| Port already in use | Change `PORT` in `.env` or stop the conflicting process |
