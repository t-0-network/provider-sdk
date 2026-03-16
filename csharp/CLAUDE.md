# CLAUDE.md - C# SDK & Starter

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output.

```csharp
// WRONG — re-encoded bytes will differ
var msg = SomeMessage.Parser.ParseFrom(body);
VerifySignature(msg.ToByteArray(), signature);

// CORRECT — use original wire bytes (middleware does this automatically)
VerifySignature(rawBodyBytes, signature);
```

## Build Commands

```bash
cd csharp/sdk/T0.ProviderSdk && dotnet build           # Build SDK
cd csharp/sdk/T0.ProviderSdk.Tests && dotnet test       # Run tests
cd csharp/starter/T0.ProviderStarter && dotnet build     # Build starter CLI
```

## Project Structure

```
csharp/
├── sdk/T0.ProviderSdk/          # Core SDK library (NuGet: T0.ProviderSdk)
│   ├── Crypto/                   # ISigner, Signer, ISignatureVerifier, Keccak256
│   ├── Network/                  # NetworkClient, SigningDelegatingHandler
│   ├── Provider/                 # SignatureVerificationMiddleware
│   ├── Hosting/                  # QuotePublisherService (abstract BackgroundService)
│   ├── Common/                   # Headers, HexUtils
│   ├── Api/                      # Generated protobuf + gRPC code (committed)
│   ├── T0Config.cs               # Typed config with FromEnvironment()
│   └── T0ProviderServer.cs       # Server builder (wraps ASP.NET Core)
├── sdk/T0.ProviderSdk.Tests/     # Unit tests (xUnit)
└── starter/T0.ProviderStarter/   # Project scaffolding CLI (NuGet tool)
```

## Key Classes

- `T0Config.FromEnvironment()` — Loads config from env vars with fail-fast validation
- `T0ProviderServer` — Builder that wraps WebApplication + gRPC + signature middleware
- `Signer` (implements `ISigner`) — secp256k1 ECDSA signing with RFC 6979
- `SignatureVerifier` / `DefaultSignatureVerifier` (implements `ISignatureVerifier`) — Verification
- `Keccak256` — Legacy Keccak-256 hashing (NOT NIST SHA-3)
- `NetworkClient.CreateNetworkServiceClient()` — Auto-signing Payment gRPC client
- `NetworkClient.CreatePaymentIntentNetworkServiceClient()` — Auto-signing PaymentIntent gRPC client
- `SignatureVerificationMiddleware` — ASP.NET Core middleware, verifies incoming requests
- `SigningDelegatingHandler` — HttpClient handler, signs outgoing requests
- `QuotePublisherService` — Abstract BackgroundService for periodic quote publishing

## Architecture Notes

- **Two-phase server build**: `T0ProviderServer` collects service registrations, then `RunAsync()` calls `Build()` + middleware + `MapGrpcService<T>()`
- **Raw bytes signing**: `SignatureVerificationMiddleware` reads body bytes BEFORE gRPC deserialization
- **DelegatingHandler pattern**: `SigningDelegatingHandler` wraps HttpClient to auto-sign outgoing requests
- **Interfaces for testability**: `ISigner` and `ISignatureVerifier` enable mocking without real crypto
- **BackgroundService pattern**: `QuotePublisherService` provides periodic timer with error handling

## Signature Protocol

```
digest  = Keccak256(body_bytes || LE_uint64(timestamp_ms))
headers = { X-Public-Key: "0x...", X-Signature: "0x...", X-Signature-Timestamp: "<ms>" }
```

- Timestamp tolerance: ±60 seconds
- Public keys: uncompressed secp256k1 (65 bytes, 0x04 prefix)
- Signatures: 65 bytes (r[32] + s[32] + v[1]), verification accepts 64 bytes too
- Canonical signatures: s ≤ n/2 enforced

## Dependencies

- **BouncyCastle.Cryptography** (2.6.2) — secp256k1, ECDSA, Keccak-256
- **Google.Protobuf** (3.34.0) — Protobuf runtime
- **Grpc.AspNetCore** (2.76.0) — gRPC server
- **Grpc.Net.Client** (2.76.0) — gRPC client
- **Target**: .NET 10.0

## Go SDK Mapping

| Go | C# |
|----|----|
| `crypto.Sign()` | `Signer.Sign()` |
| `crypto.VerifySignature()` | `SignatureVerifier.Verify()` |
| `network.NewServiceClient()` | `NetworkClient.CreateNetworkServiceClient()` |
| `network.SigningTransport` | `SigningDelegatingHandler` |
| `provider.NewHttpHandler()` | `T0ProviderServer` |
| `provider.StartServer()` | `T0ProviderServer.RunAsync()` |

## Starter CLI

```bash
dotnet run --project starter/T0.ProviderStarter -- my-provider
```

Generates a complete provider project with `.env` (auto-generated keypair), `PaymentHandler`, `QuotePublisher`, `Dockerfile`, and `appsettings.json`.

Template strings live in `TemplateFiles.cs` with `{{PROJECT_NAME}}` and `{{PROJECT_NAME_PASCAL}}` placeholders.

## Cross-Language Test Vectors

Tests in `CrossTestVectors.cs` validate crypto against shared `cross_test/test_vectors.json`:
- Keccak-256 hashing
- Key derivation (private → public)
- Request hash computation
- Sign/verify round-trips (64-byte and 65-byte signatures)

## Documentation

Docs live in [`docs/csharp/`](../docs/csharp/):
- [`ARCHITECTURE.md`](../docs/csharp/ARCHITECTURE.md) — Architecture and design decisions
- [`QUICKSTART.md`](../docs/csharp/QUICKSTART.md) — Getting started guide