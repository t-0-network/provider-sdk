# CLAUDE.md - C# SDK

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
└── starter/template/             # Starter template (scaffolded by the unified CLI)
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
- `DefaultDeadlineInterceptor` — gRPC client interceptor, default deadline per call type from `NetworkClientOptions`
- `QuotePublisherService` — Abstract BackgroundService for periodic quote publishing

## Architecture Notes

- **Two-phase server build**: `T0ProviderServer` collects service registrations, then `RunAsync()` calls `Build()` + middleware + `MapGrpcService<T>()`
- **Raw bytes signing**: `SignatureVerificationMiddleware` reads body bytes BEFORE gRPC deserialization
- **DelegatingHandler pattern**: `SigningDelegatingHandler` wraps HttpClient to auto-sign outgoing requests
- **First-frame signing for gRPC**: the handler signs only the first request frame as sent and pipes the rest unbuffered (`FirstFrameThenPipeContent`); a client stream goes out once its first message is written
- **Deadlines, not HttpClient.Timeout**: `DefaultDeadlineInterceptor` (installed by the `Create*ServiceClient` helpers) sets per-call deadlines and refuses bidirectional streams; raw channels must be wrapped
- Streaming, signing and timeout rules: [`docs/STREAMING.md`](../docs/STREAMING.md)
- **Interfaces for testability**: `ISigner` and `ISignatureVerifier` enable mocking without real crypto
- **BackgroundService pattern**: `QuotePublisherService` provides periodic timer with error handling

## Signature Protocol

```
digest  = Keccak256(body_bytes || LE_uint64(timestamp_ms))
headers = { X-Public-Key: "0x...", X-Signature: "0x...", X-Signature-Timestamp: "<ms>" }
```

- `body_bytes`: for gRPC requests the first frame only, prefix included (the whole body for unary and server streaming); otherwise the whole body
- Timestamp tolerance: ±60 seconds
- Public keys: uncompressed secp256k1 (65 bytes, 0x04 prefix)
- Signatures: 65 bytes (r[32] + s[32] + v[1]), verification accepts 64 bytes too
- Canonical signatures: s ≤ n/2 enforced

## Dependencies

- **BouncyCastle.Cryptography** — secp256k1, ECDSA, Keccak-256
- **Google.Protobuf** — Protobuf runtime
- **Grpc.AspNetCore** — gRPC server
- **Grpc.Net.Client** — gRPC client
- **Target**: .NET 10.0

Versions: `sdk/T0.ProviderSdk/T0.ProviderSdk.csproj`.

## Go SDK Mapping

| Go | C# |
|----|----|
| `crypto.Sign()` | `Signer.Sign()` |
| `crypto.VerifySignature()` | `SignatureVerifier.Verify()` |
| `network.NewServiceClient()` | `NetworkClient.CreateNetworkServiceClient()` |
| `network.SigningTransport` | `SigningDelegatingHandler` |
| `provider.NewHttpHandler()` | `T0ProviderServer` |
| `provider.StartServer()` | `T0ProviderServer.RunAsync()` |

## Starter Template

Template files live in `starter/template/` as a buildable standalone project using `my-provider` as the project name and `MyProvider` as the namespace. The unified CLI (`cli/`) scaffolds new projects from this template, replacing `my-provider` with the project name and `MyProvider` with the PascalCase name.

## Cross-Language Testing

**Test vectors:** `CrossTestVectors.cs` checks the C# crypto and `SigningDelegatingHandler` against the shared `cross_test/test_vectors.json`.

**Server-to-server:** `CrossTest/CrossServerTests.cs` exercises health check round-trips (both directions), Go→C# PayOut, and C#→Go client and server streaming between C# and Go using the shared helper at `cross_test/go_helper/`. Build it first:

```bash
cd ../cross_test/go_helper && go build -o go_helper . && cd ../../csharp
dotnet test --filter "CrossServerTests"
```

CI builds the Go helper automatically. Tests fail (not skip) in CI if the helper is missing.

## Documentation

Docs live in [`docs/csharp/`](../docs/csharp/):
- [`ARCHITECTURE.md`](../docs/csharp/ARCHITECTURE.md) — Architecture and design decisions
- [`QUICKSTART.md`](../docs/csharp/QUICKSTART.md) — Getting started guide
- [`STREAMING.md`](../docs/STREAMING.md) (shared by all SDKs) — Signing, streaming calls and timeouts