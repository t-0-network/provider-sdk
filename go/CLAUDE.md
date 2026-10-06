# CLAUDE.md - Go SDK

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output.

## Signature Verification — what is signed (DO NOT NARROW)

`verify_signature.go`'s middleware decides by content type, with the rule the client uses (`go/internal/envelope`):

- **Enveloped** (`application/connect+*`, `application/grpc`, `application/grpc+*`): the signature covers the **first envelope**, prefix included (`SignedEnvelope`), or, over gRPC with an uncompressed first envelope, its **payload without the 5-byte prefix** (`SignedPayload`). The middleware reads only that envelope, checking its length against the limit first, and passes the rest of the body on unread, so a stream reaches its handler as it arrives. gRPC unary takes this path too: connect-go accepts a unary gRPC body only as exactly one frame.
- **Anything else** (Connect unary): the **whole body** (`SignedBody`).

The payload path is for gRPC callers whose signer sits above the framer (the Java SDK's `SigningClientInterceptor`); the envelope path for callers that sign the HTTP body (C#'s `SigningDelegatingHandler`, Go, Python, the network over gRPC). Removing either silently breaks one of them with `UNAUTHENTICATED`. Every SDK's server follows the same rules ([`docs/CROSS_SDK_RULES.md`](../docs/CROSS_SDK_RULES.md)); Java's is explained in [`docs/java/SIGNATURE_VERIFICATION.md`](../docs/java/SIGNATURE_VERIFICATION.md).

The headers are checked before any of the body is read: present and well-formed, the ±60 s window, then `X-Public-Key` compared with the network key as a point, then the signature length. The verdict goes into the request context; `provider.SignatureVerification(ctx)` reads it.

## Build Commands

```bash
go build ./...       # Build
go test ./...        # Run tests
go test -v ./...     # Verbose tests
go fmt ./...         # Format
go vet ./...         # Static analysis
```

## Project Structure

```
go/
├── api/                  # Generated protobuf code (committed)
├── common/               # Shared constants (header names)
├── crypto/               # Keccak256, secp256k1 signing/verification
├── internal/envelope/    # Which content types are enveloped (client and server share it)
├── internal/pubkey/      # The one public key parser (network key, X-Public-Key, crypto helpers)
├── network/              # Network client with signing transport
├── provider/             # Server, handler, signature verification middleware
└── starter/template/     # Starter template (scaffolded by the unified CLI)
```

## Key Packages

- `provider.StartServer()` — Starts HTTP/2 (h2c) server, returns immediately with shutdown function
- `provider.NewHttpHandler()` — Creates handler with signature verification middleware.
- `provider.Handler()` — Registers ConnectRPC service with options (`WithMaxBodySize`, `WithConnectHandlerOptions`); unary and streaming procedures alike
- `provider.SignatureVerification(ctx)` — How the request was verified (`SignedBody`, `SignedEnvelope`, `SignedPayload`), or the error its call fails with
- `network.NewServiceClient()` — Creates auto-signing ConnectRPC client: unary calls signed over the whole body, client-/server-streaming calls over their first request envelope (by content type; see [`docs/STREAMING.md`](../docs/STREAMING.md)); `WithTimeout` (unary, 15s), `WithStreamTimeout` (streams, 5 min), a context deadline replaces them; `WithWireFormat`, `WithProtocol` (gRPC on `http://` runs over HTTP/2 without TLS); GET and bidi calls are refused.
- `crypto.NewSigner()` / `crypto.VerifySignature()` — secp256k1 operations
- `sdkversion.Version` — the version the running SDK reports about itself. Bumped by `release.yaml`, validated by `publish.yaml`. See [`docs/VERSIONING.md`](../docs/VERSIONING.md).

## Module Tags

The SDK module requires a separate tag for releases:
- `go/vX.Y.Z` — SDK module

## Architecture Notes

- HTTP/2 cleartext (h2c) enabled automatically via `h2c.NewHandler()`
- Server uses functional options pattern for configuration
- `StartServer()` is async — returns after confirming server is listening (5s timeout)
- Shutdown function is idempotent and safe for concurrent calls
- Size limit, 10 MiB by default (`WithMaxBodySize`): the body of a unary call, and each message of a stream; a larger one is `ResourceExhausted`. The middleware reads through `http.MaxBytesReader`, and a rejected request keeps that limit for connect-go's read of its body, so at most limit+1 bytes are read. An accepted stream is not limited as a whole; `Handler()` passes the limit to `connect.WithReadMaxBytes` (first, so a caller's option replaces it), which limits each later message.
- Signature errors are stored in the context and turned into ConnectRPC errors by `signatureErrorInterceptor`, for unary and streaming handlers: a rejected stream fails before its handler runs. connect-go decodes a unary request before the interceptor runs, so a unary body over the limit fails as `ResourceExhausted` first. A streaming procedure called with a unary content type gets HTTP 415 from connect-go before any interceptor. The interceptor reaches a connect handler only through the `opts` that `Handler()` gives its factory, so a factory that drops them serves requests that failed the signature check (documented on `Handler`).
- Uses `github.com/decred/dcrd/dcrec/secp256k1/v4` for signing/verification
- Uses `golang.org/x/crypto/sha3.NewLegacyKeccak256()` — must be Legacy variant, not standard SHA-3

## Git Workflow

- NEVER commit or push without explicit user request
- Run builds/tests locally before suggesting commits
