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

`Api/` is generated, not committed. Run `scripts/buf-generate.sh` from the repository root before building; the Git hooks run it after every pull ([CONTRIBUTING.md, "Generated code"](../CONTRIBUTING.md#generated-code)).

## Project Structure

```
csharp/
├── sdk/T0.ProviderSdk/          # Core SDK library (NuGet: T0.ProviderSdk)
│   ├── Crypto/                   # SignFn, Signer, ISigner (obsolete), ISignatureVerifier, Keccak256
│   ├── Network/                  # NetworkClient, SigningDelegatingHandler
│   ├── Provider/                 # SignatureVerificationMiddleware
│   ├── Common/                   # Headers, HexUtils
│   ├── Api/                      # Generated protobuf + gRPC code (not committed; scripts/buf-generate.sh)
│   ├── T0Config.cs               # Typed config the server is built from
│   └── T0ProviderServer.cs       # Server builder (wraps ASP.NET Core)
├── sdk/T0.ProviderSdk.Tests/     # Unit tests (xUnit)
└── starter/template/             # Starter template (scaffolded by the unified CLI); reads the
                                  # environment (Config.cs) and publishes quotes (QuotePublisherService)
```

## Key Classes

- `T0Config` — Keys, endpoint and port the server is built from; the SDK reads no environment variables
- `T0ProviderServer` — Builder that wraps WebApplication + gRPC + signature middleware
- `Signer` — secp256k1 ECDSA signing with RFC 6979; converts implicitly to `SignFn`
- `SignFn` — the signer a client takes: digest → (signature, 65-byte uncompressed public key); a custom signer is one
- `ISigner` — v1.2's signer interface, `[Obsolete]`: `Signer` implements it, the server registers it in DI, and each client factory and the `SigningDelegatingHandler` constructor has an obsolete overload taking it (`[OverloadResolutionPriority(-1)]`, so a `Signer` picks the `SignFn` one) that signs through a `SignFn` made from it
- `SignatureVerifier` / `DefaultSignatureVerifier` (implements `ISignatureVerifier`) — Verification
- `Keccak256` — Legacy Keccak-256 hashing (NOT NIST SHA-3)
- `NetworkClient.CreateNetworkServiceClient()` — Auto-signing Payment gRPC client
- `NetworkClient.CreatePaymentIntentNetworkServiceClient()` — Auto-signing PaymentIntent gRPC client
- `SignatureVerificationMiddleware` — ASP.NET Core middleware, verifies incoming requests
- `ValidationInterceptor` — validates every response the server sends (rule V12)
- `Validate.Check(message)` — the same validation inside a handler (Go `provider.Validate`, Node and Python `validate`, Java `Validate.check`): returns the message when it is valid, else throws the interceptor's `RpcException`, Internal "response validation failed: …" or "response validation error: …"
- `SigningDelegatingHandler` — HttpClient handler, signs outgoing requests
- `NetworkClient.Create(options, signer, invoker => new XClient(invoker))` — Auto-signing client for any generated gRPC client
- `NetworkClientOptions` — `BaseUrl`, `Timeout` (unary, 15 s), `StreamTimeout` (streams, 5 min); each timeout is at most `MaxTimeout` (2147483647 ms)

## Architecture Notes

- **Two-phase server build**: `T0ProviderServer` collects service registrations, then `RunAsync()` calls `Build()` + middleware + `MapGrpcService<T>()`
- **Raw bytes signing**: `SignatureVerificationMiddleware` reads body bytes BEFORE gRPC deserialization
- **DelegatingHandler pattern**: `SigningDelegatingHandler` wraps HttpClient to auto-sign outgoing requests
- **First-envelope signing**: for `application/grpc`, `application/grpc+*` and `application/connect+*` the handler signs only the first envelope as sent and pipes the rest unbuffered (`FirstFrameThenPipeContent`); a client stream goes out once its first message is written
- **Deadlines, not HttpClient.Timeout**: every `NetworkClient` factory installs the internal `DefaultDeadlineInterceptor`, which gives a call without its own deadline the default for its kind and refuses bidirectional streams
- **Transport**: one process-wide `SocketsHttpHandler` shared by every client (each client has its own `SigningDelegatingHandler` on top), with no HTTP/2 keepalive pings, no cookies kept and no redirect following (a redirect would re-send the signed request elsewhere); clients need no disposing; no client-side request validation
- Streaming rules: [`docs/STREAMING.md`](../docs/STREAMING.md)
- **Custom signers**: a client takes a `SignFn` delegate, so a test or an HSM signer is a lambda

## Signature Protocol

```
digest  = Keccak256(body_bytes || LE_uint64(timestamp_ms))
headers = { X-Public-Key: "0x...", X-Signature: "0x...", X-Signature-Timestamp: "<ms>" }
```

- `body_bytes`: for enveloped content the first envelope only, prefix included (for a unary or server-streaming gRPC call that is the whole body); otherwise the whole body
- Server (`SignatureVerificationMiddleware`): the key, timestamp, ±60 s window, gRPC framing, body limit and error codes follow [`docs/CROSS_SDK_RULES.md`](../docs/CROSS_SDK_RULES.md), the same in every SDK
- Signatures: `Signer` makes 65 bytes (r[32] + s[32] + v[1]); a custom signer may return 64 or 65 bytes, and a failing one fails the call with Internal "signing the request failed: <cause>"; verification accepts 64 and 65 bytes
- Signing produces a low s (s ≤ n/2); verification accepts a high s and refuses an r or s of n or more

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
| `network.NewServiceClient()` | `NetworkClient.Create()` |
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

CI builds the Go helper automatically. A test that needs it is marked `[GoHelperFact]` (`CrossTest/GoHelper.cs`): without the helper it is skipped, with the reason, outside CI, and fails in CI (`CI` set).

## Documentation

Docs live in [`docs/csharp/`](../docs/csharp/):
- [`ARCHITECTURE.md`](../docs/csharp/ARCHITECTURE.md) — Architecture and design decisions
- [`QUICKSTART.md`](../docs/csharp/QUICKSTART.md) — Getting started guide
- [`STREAMING.md`](../docs/STREAMING.md) (shared by all SDKs) — Streaming calls