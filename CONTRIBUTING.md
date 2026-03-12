# Contributing

## Prerequisites

- [buf](https://buf.build/docs/installation/) (protobuf code generation)
- [Go](https://go.dev/dl/) 1.25+
- [Node.js](https://nodejs.org/) LTS + npm
- [Python](https://www.python.org/downloads/) 3.13+ with [uv](https://docs.astral.sh/uv/)
- [Java](https://adoptium.net/) 17+ with [Gradle](https://gradle.org/) (wrapper included)

## Project Structure

```
proto/          Shared protobuf definitions (source of truth)
go/             Go SDK + starter CLI
node/sdk/       TypeScript SDK (@t-0/provider-sdk)
node/starter/   TypeScript starter CLI (@t-0/provider-starter-ts)
python/sdk/     Python SDK (t0-provider-sdk)
python/starter/ Python starter CLI (t0-provider-starter)
java/sdk/       Java SDK (network.t-0:provider-sdk-java)
java/cli/       Java provider-init CLI (GitHub Release JAR)
java/starter/   Java starter template (embedded in CLI)
```

## Development Setup

### Go

```sh
cd go
go mod tidy
go test -v ./...
```

### Node/TypeScript

```sh
cd node/sdk
npm ci
npm run build
npm test
```

### Python

```sh
cd python
uv sync --all-packages
uv run ruff check .
uv run pytest -v
```

#### Cross-Tests with Go SDK

Validates interoperability between Python and Go SDKs (Keccak256, signatures, server-to-server):

```sh
cd python/tests/cross_test/go_helper && go build -o go_helper . && cd ../../../..
cd python && uv run pytest tests/cross_test/ -v
```

### Java

```sh
cd java
chmod +x gradlew
./gradlew build --no-daemon
```

#### Testing the Init CLI

```sh
./gradlew :cli:shadowJar
java -jar cli/build/libs/provider-init-*.jar my-test-project
cd my-test-project && ./gradlew build
```

## Protobuf Code Generation

Proto files in `proto/` are the source of truth. They are synced from the backend via the `proto_sync.yaml` workflow. When protos change, the `generate-clients.yaml` workflow auto-generates language-specific client code.

To regenerate manually:

```sh
# Go, Node, Python (Java generates at build time via Gradle plugin)
buf generate
```

Generated code locations:
- Go: `go/api/`
- Node: `node/sdk/src/common/gen/`
- Python: `python/sdk/src/t0_provider_sdk/api/`
- Java: `java/sdk/build/generated/` (generated at build time, not committed)

### Python-specific proto generation

```sh
cd python/sdk
buf dep update
buf generate
```

After regeneration, verify that `api/buf/__init__.py` and `api/buf/validate/__init__.py` exist.

## Testing

All languages share test vectors in `cross_test/test_vectors.json` for cross-language crypto compatibility.

Run tests per language:
- **Go**: `cd go && go test -v ./...`
- **Node**: `cd node/sdk && npm test`
- **Python**: `cd python && uv run pytest -v`
- **Java**: `cd java && ./gradlew test --no-daemon`

## Release Process

1. Trigger the **Release** workflow via GitHub Actions (`workflow_dispatch`)
2. Select bump type: `patch`, `minor`, or `major`
3. The workflow automatically:
   - Validates all builds
   - Bumps version in all language files
   - Creates a git tag (`vX.Y.Z`) and GitHub Release
   - Triggers the **Publish** workflow which publishes to npm, PyPI, Maven Central, and Go Module Proxy

## Platform-Specific Notes

### Go

**SDK Architecture:**
- HTTP/2 cleartext (h2c) is enabled automatically via `h2c.NewHandler()` -- no TLS required for HTTP/2 in development
- Server uses functional options pattern: `WithAddr`, `WithReadTimeout`, `WithTLSConfig`, etc.
- `StartServer()` returns immediately after confirming the server is listening (or 5s timeout). It returns a `ServerShutdownFn` for graceful shutdown (idempotent, safe for concurrent calls)
- Default max request body size: 1 MB (configurable via `WithMaxBodySize`)

**Starter:**
- Uses Go module cache to fetch the template (`go mod download`)
- Key generation uses `github.com/ethereum/go-ethereum/crypto`
- The starter rewrites import paths in generated `.go` files to match the new module name

**Module Tags:**
- Three Go modules require separate tags: `go/vX.Y.Z`, `go/starter/vX.Y.Z`, `go/starter/template/vX.Y.Z`
- The release workflow creates all three tags automatically

### Node/TypeScript

**SDK Architecture:**
- Dual ESM/CJS output: `lib/esm/` (via `tsconfig.esm.json`) and `lib/cjs/` (via `tsconfig.cjs.json`)
- The middleware chain pattern: `signatureValidation(nodeAdapter(createService(...)))` -- `signatureValidation` streams raw bytes for hashing before ConnectRPC deserializes
- Uses `@noble/secp256k1` for signing and `@noble/hashes` for Keccak-256

**Starter:**
- Key generation requires OpenSSL (`openssl ecparam` + `openssl ec`)
- Uses `inquirer` for interactive prompts, `chalk` for colored output
- Generates a complete git repository with `npm install` run automatically

**Publishing:**
- npm provenance requires GitHub-hosted runners (`ubuntu-latest`). Blacksmith/self-hosted runners are rejected by npm
- Trusted publishing uses OIDC (`id-token: write`) -- no npm tokens needed

### Python

**Key Dependencies:**

| PyPI Package | Import | Purpose | Notes |
|---|---|---|---|
| `connect-python` | `connectrpc` | ConnectRPC runtime | Do NOT use the `connectrpc` PyPI package (different, unmaintained) |
| `protobuf` | `google.protobuf` | Message serialization | >= 5.28 required |
| `coincurve` | `coincurve` | secp256k1 ECDSA | Signing, verification, key derivation |
| `pycryptodome` | `Crypto.Hash.keccak` | Keccak256 hash | Do NOT use `pysha3` (incompatible with Python 3.13) or `hashlib.sha3_256` (different padding) |

**SDK Architecture:**
- Two-phase signature verification: ASGI/WSGI middleware captures raw bytes and stores errors in `contextvars.ContextVar`, then ConnectRPC interceptor converts errors to `ConnectError` codes. Do not collapse into a single layer
- `SigningClient` wraps `pyqwest.Client` via delegation (not subclass) -- pyqwest is Rust-backed FFI, subclassing is undefined
- Async (ASGI/uvicorn) and sync (WSGI/gunicorn) variants available for both server and client

**Architecture: Go SDK Mapping**

Reference for porting changes from the Go SDK:

| Go SDK | Python SDK |
|---|---|
| `crypto/hash.go` | `sdk/src/t0_provider_sdk/crypto/hash.py` |
| `crypto/sign.go` | `sdk/src/t0_provider_sdk/crypto/signer.py` |
| `crypto/verify_signature.go` | `sdk/src/t0_provider_sdk/crypto/verifier.py` |
| `crypto/helper.go` | `sdk/src/t0_provider_sdk/crypto/keys.py` |
| `common/header.go` | `sdk/src/t0_provider_sdk/common/headers.py` |
| `network/signing_transport.go` | `sdk/src/t0_provider_sdk/network/signing.py` |
| `network/client.go` | `sdk/src/t0_provider_sdk/network/client.py` |
| `provider/verify_signature.go` | `sdk/src/t0_provider_sdk/provider/middleware.py` (ASGI), `middleware_wsgi.py` (WSGI) |
| `provider/signature_error.go` | `sdk/src/t0_provider_sdk/provider/interceptor.py` |
| `provider/handler.go` | `sdk/src/t0_provider_sdk/provider/handler.py` |

### Java

**SDK Architecture:**
- Uses gRPC (not ConnectRPC) with `io.grpc` framework
- Server MUST wrap service with `ServerInterceptors.useInputStreamMessages()` BEFORE adding signature verification interceptor -- otherwise raw bytes are not available and verification fails
- Supports both Connect protocol (raw protobuf) and gRPC protocol (5-byte frame: compression flag + 4-byte length) automatically
- Uses BouncyCastle for secp256k1 and Keccak-256, with canonical S-value normalization (lower half of curve order)

**Repository Structure:**

```
java/
├── sdk/                    # Core SDK library (published to Maven Central + JitPack)
├── cli/                    # Init CLI tool (published as GitHub Release asset)
├── starter/template/       # Template for new projects (embedded in CLI)
└── gradle.properties       # Version management
```

The CLI is a separate Gradle module because Java has no `go run`/`npx` equivalent -- it must be distributed as a self-contained fat JAR (Shadow JAR) with the template embedded as resources.

**Publishing:**

| Artifact | Channel | Coordinates |
|----------|---------|-------------|
| SDK | Maven Central | `network.t-0:provider-sdk-java` |
| SDK | JitPack (alternative) | `com.github.t-0-network:provider-sdk` |
| CLI | GitHub Releases | `provider-init.jar` |

Only the SDK is published to Maven Central and JitPack. The CLI (`provider-init.jar`) is published exclusively via GitHub Releases because it is a one-time scaffolding tool, not a library dependency. Users download it, run it to generate a project, then delete it. The generated project depends on the SDK artifact (resolved by Gradle), not on the CLI. Publishing the CLI to Maven Central would add unnecessary signing, POM metadata, and review overhead for an artifact that no build tool ever resolves as a dependency.

**Template Dual-Mode Build:**
- When built as part of the SDK repo: uses `:sdk` project dependency
- When extracted as a standalone project: uses published SDK artifact from JitPack or Maven Central
- The CLI performs string replacement to switch between modes during project generation

**Required GitHub Secrets:**

| Secret/Variable | Purpose |
|--------|---------|
| `OSSRH_USERNAME` | Maven Central authentication |
| `OSSRH_PASSWORD` | Maven Central authentication |
| `GPG_PRIVATE_KEY` | Artifact signing |
| `CI_APP_ID` / `CI_APP_PRIVATE_KEY` | Release workflow automation |

See [GitHub Setup Guide](docs/java/GITHUB_SETUP.md) for detailed setup instructions.
