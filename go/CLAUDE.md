# CLAUDE.md - Go SDK & Starter

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output.

## Signature Verification — Dual Framing (DO NOT REMOVE)

`verify_signature.go`'s middleware accepts signatures over **either** the full HTTP body **or** the body with the 5-byte gRPC frame prefix stripped. Both paths are required:

- **Full body** — Connect-protocol callers (Go, Node, Python) and gRPC-protocol callers whose signer covers the framed body (C# SDK's `SigningDelegatingHandler` sits below the gRPC framer in the HttpClient pipeline, so it signs the already-framed bytes).
- **Unframed fallback** — gRPC-protocol callers whose signing interceptor sits above the gRPC framer (Java SDK's `SigningClientInterceptor`). The signed payload is unframed protobuf, but the HTTP body includes the gRPC frame prefix.

Removing the fallback silently breaks **Java SDK** clients with `UNAUTHENTICATED`. The Java SDK has the same dual-path on its server side — see [`docs/java/SIGNATURE_VERIFICATION.md`](../docs/java/SIGNATURE_VERIFICATION.md).

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
├── network/              # Network client with signing transport
├── provider/             # Server, handler, signature verification middleware
├── examples/             # Usage examples (test files)
├── starter/              # Starter CLI
│   ├── main.go           # Generator: fetches template, rewrites imports, generates keys
│   └── template/         # Embedded project template
└── tools/                # Build tooling
```

## Key Packages

- `provider.StartServer()` — Starts HTTP/2 (h2c) server, returns immediately with shutdown function
- `provider.NewHttpHandler()` — Creates handler with signature verification middleware.
- `provider.Handler()` — Registers ConnectRPC service with options (`WithMaxBodySize`, `WithVerifySignatureFn`)
- `network.NewServiceClient()` — Creates auto-signing ConnectRPC client
- `crypto.Sign()` / `crypto.VerifySignature()` — secp256k1 operations
- `sdkversion.Version` — the version the running SDK reports about itself. Bumped by `release.yaml`, validated by `publish.yaml`. See [`docs/VERSIONING.md`](../docs/VERSIONING.md).

## Module Tags

Three Go modules require separate tags for releases:
- `go/vX.Y.Z` — SDK module
- `go/starter/vX.Y.Z` — Starter CLI module
- `go/starter/template/vX.Y.Z` — Template module

## Architecture Notes

- HTTP/2 cleartext (h2c) enabled automatically via `h2c.NewHandler()`
- Server uses functional options pattern for configuration
- `StartServer()` is async — returns after confirming server is listening (5s timeout)
- Shutdown function is idempotent and safe for concurrent calls
- Default max request body size: 1 MB (configurable via `WithMaxBodySize`)
- Signature errors stored in context, converted to ConnectRPC errors by interceptor
- Uses `github.com/decred/dcrd/dcrec/secp256k1/v4` for signing/verification
- Uses `golang.org/x/crypto/sha3.NewLegacyKeccak256()` — must be Legacy variant, not standard SHA-3

## Starter

- Uses Go module cache to fetch template (`go mod download`)
- Key generation uses `github.com/ethereum/go-ethereum/crypto`
- Rewrites import paths in generated `.go` files to match the new module name

## Git Workflow

- NEVER commit or push without explicit user request
- Run builds/tests locally before suggesting commits
