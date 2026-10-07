# C# Provider SDK — Architecture

## Overview

The C# SDK provides tools for building T-0 Network payment providers on ASP.NET Core with gRPC. It handles cryptographic signing/verification (secp256k1 + Keccak-256), HTTP request authentication, and server lifecycle management.

## Project Structure

```
csharp/
├── sdk/T0.ProviderSdk/              # Core SDK library
│   ├── Crypto/                       # Signing, verification, hashing
│   │   ├── SignFn.cs                 # Custom signer delegate: digest → (signature, public key)
│   │   ├── Signer.cs                 # secp256k1 ECDSA implementation (converts to SignFn)
│   │   ├── ISignatureVerifier.cs     # Interface for verification
│   │   ├── DefaultSignatureVerifier.cs
│   │   ├── SignatureVerifier.cs      # Static verification methods
│   │   ├── Keccak256.cs              # Keccak-256 hashing
│   │   └── SignResult.cs             # Immutable signing result
│   ├── Network/                      # Client-side (outbound calls)
│   │   ├── NetworkClient.cs          # Factory for auto-signing gRPC clients
│   │   ├── NetworkClientOptions.cs   # Client configuration (BaseUrl, Timeout, StreamTimeout)
│   │   ├── DefaultDeadlineInterceptor.cs # Default deadline per call type (internal)
│   │   ├── SigningDelegatingHandler.cs # HTTP message signing
│   │   └── FirstFrameThenPipeContent.cs # Sends the signed first envelope, then pipes the rest
│   ├── Provider/                     # Server-side (incoming requests)
│   │   ├── SignatureVerificationMiddleware.cs
│   │   ├── ValidationInterceptor.cs  # Validates responses against buf.validate annotations
│   │   ├── HealthServiceImpl.cs      # grpc.health.v1 service
│   │   └── ProviderServerOptions.cs
│   ├── Common/
│   │   ├── Headers.cs                # Header constants + timestamp encoding
│   │   ├── HexUtils.cs               # Hex encoding/decoding
│   │   ├── Messages.cs               # Every SDK-raised error text (checked against cross_test/test_vectors.json)
│   │   └── ValidationUtils.cs        # Formats buf.validate violations
│   ├── Api/                          # Generated protobuf + gRPC code
│   │   ├── Tzero/V1/Payment/         # Payment service definitions
│   │   ├── Tzero/V1/PaymentIntent/   # PaymentIntent service definitions
│   │   ├── Tzero/V1/Common/          # Shared types
│   │   └── Ivms101/V1/              # IVMS-101 compliance types
│   ├── T0Config.cs                   # Typed configuration (the starter's Config.FromEnvironment fills it)
│   └── T0ProviderServer.cs           # Server builder
├── sdk/T0.ProviderSdk.Tests/         # Unit tests
└── starter/template/                 # Starter template (scaffolded by the unified CLI): Config.cs reads the
                                      # environment; Services/QuotePublisherService.cs publishes quotes
```

## Key Design Decisions

### Raw Bytes for Signatures

**CRITICAL**: Protobuf encoding is not canonical. Re-encoding a deserialized message produces different bytes. All signing and verification operates on original wire bytes:

- **Server-side**: `SignatureVerificationMiddleware` reads `Request.Body` as raw bytes BEFORE gRPC deserialization
- **Client-side**: `SigningDelegatingHandler` signs `request.Content` bytes as sent. For enveloped content (`application/grpc`, `application/grpc+*`, `application/connect+*`) it signs only the first envelope and pipes the rest through unbuffered (`FirstFrameThenPipeContent`); other content is read whole before sending. See [STREAMING.md](../STREAMING.md).

### Deadlines

Timeouts are gRPC call deadlines: a call without its own deadline gets `NetworkClientOptions.Timeout` (unary, 15 s) or `StreamTimeout` (client and server streams, 5 min), and the caller's own deadline replaces the default. Every `NetworkClient` factory applies both through an interceptor, which also refuses bidirectional streams; `HttpClient.Timeout` is infinite. All clients share one transport (connection pool), so a client is cheap to create and needs no disposing; the transport sends no keepalive pings, keeps no cookies and does not follow redirects, which would re-send the signed request to another server. The client does not validate requests; the network does. See [STREAMING.md](../STREAMING.md#stream-timeout).

### Two-Phase Server Architecture

```
HTTP Request
  → SignatureVerificationMiddleware (raw bytes, signature check)
  → gRPC deserialization (protobuf → typed message)
  → ProviderService handler (business logic)
```

The middleware runs before gRPC deserialization. If signature verification fails, a gRPC error frame is written directly, with the status code that every SDK uses for that failure ([`CROSS_SDK_RULES.md`](../CROSS_SDK_RULES.md#error-codes)).

### T0ProviderServer Builder

Uses a two-phase build pattern because ASP.NET Core requires service registration before `Build()`:

1. **Configuration phase**: Constructor + `MapPaymentService()` / `MapPaymentIntentService()` / `AddHostedService()` register services on `WebApplicationBuilder`
2. **Run phase**: `RunAsync()` calls `Build()`, wires middleware, maps endpoints, and starts the server

### Pluggable Crypto

The client takes a `SignFn` delegate, digest → (signature, 65-byte uncompressed public key), so a test or an external key store needs no real key. `Signer` converts to it, and `ISignatureVerifier` can be replaced for server tests:

```csharp
// Production
SignFn signer = Signer.FromHex(privateKey);

// Test or external key store
SignFn signer = digest => (fakeSignature, fakePublicKey);
```

Before a request is sent, the client checks the output: the signature is 64 or 65 bytes, then the key is 65 bytes uncompressed. A failure, or a signer that throws, fails the call with Internal "signing the request failed: <cause>", and nothing is sent ([rule S7](../CROSS_SDK_RULES.md#signing)).

## Signature Protocol

```
digest  = Keccak256(body_bytes || LE_uint64(timestamp_ms))
headers = {
  X-Public-Key: "0x" + hex(uncompressed_pubkey_65_bytes),
  X-Signature: "0x" + hex(r[32] + s[32] + v[1]),
  X-Signature-Timestamp: "<milliseconds>"
}
```

- **body_bytes**: for enveloped content, the first envelope as sent, 5-byte prefix included (for a unary gRPC call, the whole body); otherwise the whole body ([STREAMING.md](../STREAMING.md#what-is-signed))
- **Hash**: Keccak-256 (legacy, NOT NIST SHA-3)
- **Curve**: secp256k1 (same as Ethereum)
- **Nonce**: RFC 6979 deterministic (HMAC-SHA256)
- **Canonical**: `s` forced to lower half of curve order
- **Server rules** (timestamp window, key, framing, body limit, error codes): [`CROSS_SDK_RULES.md`](../CROSS_SDK_RULES.md)

## Go SDK Mapping

| Go SDK | C# SDK |
|--------|--------|
| `crypto.Sign()` | `Signer.Sign()` |
| `crypto.VerifySignature()` | `SignatureVerifier.Verify()` |
| `crypto.Keccak256()` | `Keccak256.Hash()` |
| `network.NewServiceClient()` | `NetworkClient.Create()` |
| `network.SigningTransport` | `SigningDelegatingHandler` |
| `provider.NewHttpHandler()` | `T0ProviderServer` |
| `provider.StartServer()` | `T0ProviderServer.RunAsync()` |
| `provider.Handler()` | `T0ProviderServer.MapPaymentService<T>()` |

## Dependencies

BouncyCastle.Cryptography (secp256k1, ECDSA, Keccak-256), Google.Protobuf, Grpc.AspNetCore (server) and Grpc.Net.Client (client). Versions: [`T0.ProviderSdk.csproj`](../../csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj).

Target: .NET 10.0
