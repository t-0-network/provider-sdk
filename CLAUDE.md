# CLAUDE.md - T-0 Provider SDK Monorepo

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output. This applies to ALL languages.

## Repository Layout

```
proto/              Shared protobuf definitions (source of truth)
go/                 Go SDK + starter CLI
node/sdk/           TypeScript SDK (@t-0/provider-sdk)
node/starter/       TypeScript starter CLI (@t-0/provider-starter-ts)
python/sdk/         Python SDK (t0-provider-sdk)
python/starter/     Python starter CLI (t0-provider-starter)
java/sdk/           Java SDK (network.t-0:provider-sdk-java)
java/cli/           Java provider-init CLI (GitHub Release JAR)
java/starter/       Java starter template (embedded in CLI)
cross_test/         Cross-language test vectors
.github/workflows/  CI, Release, Publish workflows
```

## Versioning

All SDKs share a unified version managed via git tags (`vX.Y.Z`). Version is bumped in all language files simultaneously by the Release workflow.

Go requires additional module tags: `go/vX.Y.Z`, `go/starter/vX.Y.Z`, `go/starter/template/vX.Y.Z`.

## Workflows

- **release.yaml** — Triggered manually. Bumps version, creates tags + GitHub Release.
- **publish.yaml** — Triggered by tag push. Publishes to npm, PyPI, Maven Central, Go Module Proxy.
- **proto_sync.yaml** — Syncs proto files from backend.
- **generate-clients.yaml** — Regenerates language-specific code from protos.

## Build & Test (all languages)

```bash
cd go && go test ./...                            # Go
cd node/sdk && npm ci && npm run build && npm test # Node
cd python && uv sync --all-packages && uv run pytest -v  # Python
cd java && ./gradlew build                        # Java
```

## Cross-Language Test Vectors

All languages share `cross_test/test_vectors.json` for crypto compatibility (Keccak256, secp256k1 signing/verification).

## Signature Protocol

```
digest  = Keccak256(body_bytes || little_endian_uint64(timestamp_ms))
headers = { X-Public-Key: "0x...", X-Signature: "0x...", X-Signature-Timestamp: "<ms>" }
```

- Timestamp tolerance: ±60 seconds
- Public keys: uncompressed secp256k1 (65 bytes, 0x04 prefix)
- Signatures: 64 or 65 bytes (r + s + optional recovery id)
- Hash: Keccak-256 (NOT NIST SHA-3)

## Releasing

**NEVER release manually.** Releases are handled exclusively by the `release.yaml` GitHub Actions workflow.

- DO NOT run `gh workflow run release.yaml` without explicit user request
- DO NOT create version tags manually (e.g., `git tag vX.Y.Z`) — the release workflow manages all tags
- DO NOT push tags directly — the workflow creates and pushes `vX.Y.Z`, `go/vX.Y.Z`, etc.
- The publish workflow (`publish.yaml`) is triggered automatically by tag push — never trigger it manually

When the user asks to release, trigger it via `gh workflow run release.yaml -f bump=<type> --ref master`. Default to `patch` unless the user specifies otherwise.

## Git Workflow

- NEVER commit or push without explicit user request
- Run builds/tests locally before suggesting commits
