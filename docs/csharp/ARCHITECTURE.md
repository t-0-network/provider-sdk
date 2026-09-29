# C# Provider SDK — Architecture

## Overview

The C# SDK provides tools for building T-0 Network payment providers on ASP.NET Core with gRPC. It handles cryptographic signing/verification (secp256k1 + Keccak-256), HTTP request authentication, and server lifecycle management.

## Project Structure

```
csharp/
├── sdk/T0.ProviderSdk/              # Core SDK library
│   ├── Crypto/                       # Signing, verification, hashing
│   │   ├── ISigner.cs                # Interface for ECDSA signing
│   │   ├── Signer.cs                 # secp256k1 ECDSA implementation
│   │   ├── ISignatureVerifier.cs     # Interface for verification
│   │   ├── DefaultSignatureVerifier.cs
│   │   ├── SignatureVerifier.cs      # Static verification methods
│   │   ├── Keccak256.cs              # Keccak-256 hashing
│   │   └── SignResult.cs             # Immutable signing result
│   ├── Network/                      # Client-side (outbound calls)
│   │   ├── NetworkClient.cs          # Factory for auto-signing gRPC clients
│   │   ├── NetworkClientOptions.cs   # Client configuration (Timeout, StreamTimeout)
│   │   ├── DefaultDeadlineInterceptor.cs # Default deadline per call type
│   │   ├── SigningDelegatingHandler.cs # HTTP message signing
│   │   └── FirstFrameThenPipeContent.cs # Sends the signed first gRPC frame, then pipes the rest
│   ├── Provider/                     # Server-side (incoming requests)
│   │   ├── SignatureVerificationMiddleware.cs
│   │   └── ProviderServerOptions.cs
│   ├── Hosting/
│   │   └── QuotePublisherService.cs  # Abstract BackgroundService for quotes
│   ├── Common/
│   │   ├── Headers.cs                # Header constants + timestamp encoding
│   │   └── HexUtils.cs              # Hex encoding/decoding
│   ├── Api/                          # Generated protobuf + gRPC code
│   │   ├── Tzero/V1/Payment/         # Payment service definitions
│   │   ├── Tzero/V1/PaymentIntent/   # PaymentIntent service definitions
│   │   ├── Tzero/V1/Common/          # Shared types
│   │   └── Ivms101/V1/              # IVMS-101 compliance types
│   ├── T0Config.cs                   # Typed configuration
│   └── T0ProviderServer.cs           # Server builder
├── sdk/T0.ProviderSdk.Tests/         # Unit tests
└── starter/template/                 # Starter template (scaffolded by the unified CLI)
```

## Key Design Decisions

### Raw Bytes for Signatures

**CRITICAL**: Protobuf encoding is not canonical. Re-encoding a deserialized message produces different bytes. All signing and verification operates on original wire bytes:

- **Server-side**: `SignatureVerificationMiddleware` reads `Request.Body` as raw bytes BEFORE gRPC deserialization
- **Client-side**: `SigningDelegatingHandler` reads `request.Content` bytes BEFORE sending. For gRPC content (`application/grpc`, `application/grpc+*`) it reads only the first frame: it starts the original content writing into a pipe, takes `flags || uint32be len || payload` exactly as written (compressed bytes if flag bit 0 is set), signs it, and sends at once. `FirstFrameThenPipeContent` writes that frame and then forwards the rest of the pipe chunk by chunk, so a client stream is never buffered. Other content is signed and sent whole.

Sending waits for the first frame, so a client-streaming call goes out when its first message is written (or the stream is completed, which signs empty bytes; the network rejects that call). Write before awaiting response headers.

### Deadlines

`HttpClient.Timeout` only runs until response headers arrive, which for a client stream is the whole upload, so `NetworkClient.Create` sets it to infinite. Timeouts are gRPC deadlines instead: `DefaultDeadlineInterceptor` sets `CallOptions.Deadline` on calls that have none, from `NetworkClientOptions.Timeout` (unary, default 15 s) or `NetworkClientOptions.StreamTimeout` (client and server streaming, default none). It also rejects a bidirectional (duplex) call with `Unimplemented` before anything is sent. The `Create*ServiceClient` helpers install it. A raw `GrpcChannel` from `Create`/`CreateChannel` cannot carry an interceptor, so set deadlines per call or use `channel.Intercept(new DefaultDeadlineInterceptor(options))`.

### Two-Phase Server Architecture

```
HTTP Request
  → SignatureVerificationMiddleware (raw bytes, signature check)
  → gRPC deserialization (protobuf → typed message)
  → ProviderService handler (business logic)
```

The middleware runs before gRPC deserialization. If signature verification fails, a gRPC error frame is written directly (status 3=InvalidArgument or 16=Unauthenticated).

### T0ProviderServer Builder

Uses a two-phase build pattern because ASP.NET Core requires service registration before `Build()`:

1. **Configuration phase**: Constructor + `MapPaymentService()` / `MapPaymentIntentService()` / `AddHostedService()` register services on `WebApplicationBuilder`
2. **Run phase**: `RunAsync()` calls `Build()`, wires middleware, maps endpoints, and starts the server

### Interface-Based Crypto

`ISigner` and `ISignatureVerifier` enable mocking in tests without touching real crypto:

```csharp
// Production
ISigner signer = Signer.FromHex(privateKey);

// Test
ISigner signer = new FakeSigner(); // Your mock
```

## Signature Protocol

```
digest  = Keccak256(body_bytes || LE_uint64(timestamp_ms))
headers = {
  X-Public-Key: "0x" + hex(uncompressed_pubkey_65_bytes),
  X-Signature: "0x" + hex(r[32] + s[32] + v[1]),
  X-Signature-Timestamp: "<milliseconds>"
}
```

- **body_bytes**: for gRPC requests, the first request frame as sent, 5-byte prefix included (unary and server streaming: the whole body; a client stream completed without a message: empty); otherwise the whole body. Bidirectional streaming is not supported: `DefaultDeadlineInterceptor` rejects it.
- **Hash**: Keccak-256 (legacy, NOT NIST SHA-3)
- **Curve**: secp256k1 (same as Ethereum)
- **Nonce**: RFC 6979 deterministic (HMAC-SHA256)
- **Canonical**: `s` forced to lower half of curve order
- **Timestamp tolerance**: ±60 seconds

## Go SDK Mapping

| Go SDK | C# SDK |
|--------|--------|
| `crypto.Sign()` | `Signer.Sign()` |
| `crypto.VerifySignature()` | `SignatureVerifier.Verify()` |
| `crypto.Keccak256()` | `Keccak256.Hash()` |
| `network.NewServiceClient()` | `NetworkClient.CreateNetworkServiceClient()` |
| `network.SigningTransport` | `SigningDelegatingHandler` |
| `provider.NewHttpHandler()` | `T0ProviderServer` |
| `provider.StartServer()` | `T0ProviderServer.RunAsync()` |
| `provider.Handler()` | `T0ProviderServer.MapPaymentService<T>()` |

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| BouncyCastle.Cryptography | 2.6.2 | secp256k1, ECDSA, Keccak-256 |
| Google.Protobuf | 3.34.0 | Protobuf runtime |
| Grpc.AspNetCore | 2.76.0 | gRPC server |
| Grpc.Net.Client | 2.76.0 | gRPC client |

Target: .NET 10.0
