# T-0 Network Provider SDK

Monorepo containing provider SDKs and starter templates for all supported languages.

## Structure

| Directory | Description | Package |
|-----------|-------------|---------|
| `proto/` | Shared protobuf definitions (synced from backend) | — |
| `go/` | Go SDK | `github.com/t-0-network/provider-sdk/go` |
| `go/starter/` | Go starter CLI | `github.com/t-0-network/provider-sdk/go/starter` |
| `node/sdk/` | TypeScript SDK | `@t-0/provider-sdk` (npm) |
| `node/starter/` | TypeScript starter CLI | `@t-0/provider-starter-ts` (npm) |
| `python/sdk/` | Python SDK | `t0-provider-sdk` (PyPI) |
| `python/starter/` | Python starter CLI | `t0-provider-starter` (PyPI) |
| `java/sdk/` | Java SDK | `network.t-0:provider-sdk-java` (Maven Central) |
| `java/starter/` | Java starter template | — |
| `java/cli/` | Java provider-init CLI | — |

## Versioning

All SDKs share a unified version, managed via git tags (`vX.Y.Z`).

## Release

Trigger the **Release** workflow (`workflow_dispatch`) with a bump type (patch/minor/major). This will:
1. Bump version in all language-specific files
2. Validate builds for all languages
3. Create a git tag and GitHub Release
4. Trigger per-language publish workflows

## Proto Updates

Proto files are synced from the backend via the `proto_sync.yaml` workflow. When protos change, the `generate-clients` workflow auto-generates language-specific client code.
