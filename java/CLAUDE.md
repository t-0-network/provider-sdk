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

---

## Build Commands

```bash
./gradlew build              # Build everything
./gradlew test               # Run tests
./gradlew build -x test      # Build without tests
./gradlew :cli:shadowJar     # Build CLI fat JAR
./gradlew :sdk:build         # Build SDK only
```

## Project Structure

```
java/
├── sdk/                  # Core SDK library (published to Maven Central + JitPack)
│   ├── src/main/java/    # Crypto, gRPC interceptors, client/server
│   ├── src/main/proto/   # Protobuf definitions (generated code not committed)
│   └── src/test/         # Tests + JMH benchmarks
├── cli/                  # Provider init CLI (published as GitHub Release JAR)
│   └── src/main/java/    # InitCommand, TemplateExtractor, KeyGenerator
├── starter/template/     # Template project (embedded in CLI JAR as resources)
└── .github/workflows/    # CI, Release, Publish workflows
```

**Why `cli/` and `starter/` are separate (differs from Go/Node):**
Go and Node starters are single directories because they can run from source (`go run ...@latest`, `npx ...`). Java has no equivalent — the CLI must be distributed as a self-contained fat JAR. This requires `cli/` as a separate Gradle module to: (1) build a Shadow JAR with all dependencies, (2) embed `starter/template/` as JAR resources at build time, and (3) depend on `:sdk` for key generation. The template is also a dual-mode Gradle project — it builds as a subproject of the SDK repo for CI verification, but switches to using the published SDK artifact when extracted as a standalone project.

## Publishing & Artifacts

| Artifact | JitPack | Maven Central |
|----------|---------|---------------|
| **SDK** | `com.github.t-0-network:provider-java:<version>` | `network.t-0:provider-sdk-java:<version>` |
| **CLI** | N/A (GitHub Release only) | N/A (GitHub Release only) |

- **JitPack is the default** — fast, builds on demand from GitHub
- Maven Central publication can be slow (10-30 min, sometimes hours)
- Tags use bare version numbers (`1.0.33`), NOT `v`-prefixed
- CLI JAR is uploaded to GitHub Releases by the Publish workflow
- JitPack builds only `:sdk:publishToMavenLocal` (see `jitpack.yml`)

### Release Process

1. Trigger "Release" workflow (manual dispatch, select patch/minor/major)
2. Workflow bumps version, commits, tags, creates GitHub Release
3. Tag push triggers "Publish" workflow → Maven Central + GitHub Release asset
4. JitPack verify job checks artifact availability

## CLI Tool

```bash
# Download and run
curl -fsSL -L https://github.com/t-0-network/provider-java/releases/latest/download/provider-init.jar -o provider-init.jar
java -jar provider-init.jar [OPTIONS] [PROJECT_NAME]

# Options: -r/--repository (jitpack|maven-central), -d/--directory, --no-color
```

- Interactive prompts for project name and repository if not provided via flags
- Template uses `${PROJECT_NAME}` and `${SDK_VERSION}` placeholders
- Generated projects include Dockerfile with `applicationName = "provider"` (fixed install path)

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
- [`GITHUB_SETUP.md`](../../docs/java/GITHUB_SETUP.md) — CI/CD, secrets, publishing setup
- [`STARTER_ARCHITECTURE.md`](../../docs/java/STARTER_ARCHITECTURE.md) — how the init system works
- [`PROTO_SCHEMA_MANAGEMENT.md`](../../docs/java/PROTO_SCHEMA_MANAGEMENT.md) — protobuf code generation
- [`ISSUES_AND_LESSONS.md`](../../docs/java/ISSUES_AND_LESSONS.md) — historical issues and solutions
