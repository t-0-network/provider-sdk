# CLAUDE.md - Project Context & Requirements

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output. See implementation pattern in `sdk/src/main/java/network/t0/sdk/crypto/`.

```java
// WRONG — re-encoded bytes will differ
Message msg = parseFrom(bytes);
verifySignature(msg.toByteArray(), signature);

// CORRECT — use original wire bytes
byte[] rawBytes = getOriginalRequestBytes();
verifySignature(rawBytes, signature);
```

## Signature Verification — Dual Framing (DO NOT REMOVE)

`SignatureVerificationInterceptor.verifySignature` accepts signatures over **either** unframed protobuf **or** the 5-byte-gRPC-framed body. Both paths are load-bearing in production:

- **Unframed path** — Java SDK's own `NetworkClient` (signs above the gRPC framer), Connect-protocol callers in Go / Node / Python (no frame exists), and the T-0 Network when configured to call this provider via Connect protocol.
- **gRPC-framed path** — T-0 Network when configured to call this provider via gRPC protocol. The signer sits below the gRPC framer, so the signed payload covers the 5-byte frame prefix (1 byte compressed flag + 4 bytes big-endian length) followed by the protobuf message bytes.

Removing either path silently breaks one class of caller with `UNAUTHENTICATED` errors.

GitHub issue #89 raised concern that the framed path looked like dead code — investigation confirmed it is alive and required because the network's gRPC-protocol path signs framed bodies. See [`docs/java/SIGNATURE_VERIFICATION.md`](../../docs/java/SIGNATURE_VERIFICATION.md) for the precise signing-payload definitions per transport and conditions under which simplification would be safe.

---

## Cross-Language Testing

Cross-server tests live in `sdk/src/test/java/network/t0/sdk/integration/CrossServerTests.java` and exercise bidirectional server-to-server communication with the Go helper at `cross_test/go_helper/`.

```bash
cd cross_test/go_helper && go build -o go_helper .                              # Build Go helper
cd java && ./gradlew test --tests "network.t0.sdk.integration.CrossServerTests" # Run cross-tests
```

Tests cover:
- **Go→Java**: Health check + PayOut (via `--grpc`)
- **Java→Go**: Health check (Java `BlockingNetworkClient` → Go server with dual-framing)

In CI, tests **fail** (not skip) if the Go helper binary is missing.

## Build Commands

```bash
./gradlew build              # Build everything
./gradlew test               # Run tests
./gradlew build -x test      # Build without tests
./gradlew :sdk:build         # Build SDK only
```

## Project Structure

```
java/
├── sdk/                  # Core SDK library (published to Maven Central + JitPack)
│   ├── src/main/java/    # Crypto, gRPC interceptors, client/server
│   ├── src/main/proto/   # Protobuf definitions (generated code not committed)
│   └── src/test/         # Tests + JMH benchmarks
├── starter/template/     # Template project (scaffolded by the unified CLI)
└── .github/workflows/    # CI, Release, Publish workflows
```

## Publishing & Artifacts

| Artifact | JitPack | Maven Central |
|----------|---------|---------------|
| **SDK** | `com.github.t-0-network:provider-sdk:<version>` | `network.t-0:provider-sdk-java:<version>` |

- **JitPack is the default** — fast, builds on demand from GitHub
- Maven Central publication can be slow (10-30 min, sometimes hours)
- Tags use bare version numbers (`1.0.33`), NOT `v`-prefixed
- JitPack builds only `:sdk:publishToMavenLocal` (see `jitpack.yml`)

### Release Process

1. Trigger "Release" workflow (manual dispatch, select patch/minor/major)
2. Workflow bumps version, commits, tags, creates GitHub Release
3. Tag push triggers "Publish" workflow → Maven Central
4. JitPack verify job checks artifact availability

## Versioning

Runtime version: `META-INF/sdk-version.properties` (classpath resource, so it survives jar shading). Full details: [`docs/VERSIONING.md`](../docs/VERSIONING.md).

## Key Technical Details

- **Java 17+** required
- **Protobuf**: Generated code lives in `sdk/build/generated/` (not committed)
- **Dockerfile**: Uses `eclipse-temurin:17-jre-noble` (not alpine — ARM64 support needed)
- **Gradle application plugin**: `applicationName = "provider"` ensures `build/install/provider/` path is stable regardless of `rootProject.name`
- **Template build.gradle.kts**: `sdkRepository` variable controls JitPack vs Maven Central; CLI does exact string replacement

## Git Workflow

- **NEVER commit or push without explicit user request**
- Run builds/tests locally before suggesting commits
- Do not commit debug changes

## Troubleshooting

See [`docs/java/ISSUES_AND_LESSONS.md`](../../docs/java/ISSUES_AND_LESSONS.md) for historical issues and solutions.

## Documentation

Docs live in the top-level [`docs/java/`](../../docs/java/) directory:
- [`SIGNATURE_VERIFICATION.md`](../../docs/java/SIGNATURE_VERIFICATION.md) — dual-path verification rationale (CRITICAL — read before touching `SignatureVerificationInterceptor`)
- [`GITHUB_SETUP.md`](../../docs/java/GITHUB_SETUP.md) — CI/CD, secrets, publishing setup
- [`PROTO_SCHEMA_MANAGEMENT.md`](../../docs/java/PROTO_SCHEMA_MANAGEMENT.md) — protobuf code generation
- [`ISSUES_AND_LESSONS.md`](../../docs/java/ISSUES_AND_LESSONS.md) — historical issues and solutions
