# CLAUDE.md - T-0 Provider SDK Monorepo

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

Sign and verify the raw wire bytes, never a re-encoded protobuf message. Rule S2 in [`docs/CROSS_SDK_RULES.md`](docs/CROSS_SDK_RULES.md). All languages.

## Repository Layout

```
proto/              Shared protobuf definitions (source of truth)
cli/                Unified starter CLI (Go, cross-compiled static binary)
go/                 Go SDK + starter template
node/sdk/           TypeScript SDK (@t-0/provider-sdk)
node/starter/       TypeScript starter template
python/sdk/         Python SDK (t0-provider-sdk)
python/starter/     Python starter template
java/sdk/           Java SDK (network.t-0:provider-sdk-java)
java/starter/       Java starter template
csharp/             C# SDK + starter template
cross_test/         Cross-language test vectors + shared Go helper
.github/workflows/  CI, Release, Publish workflows
```

## Versioning

All SDKs share a unified version managed via git tags (`vX.Y.Z`). The version lives in three categories of files (package-level, starter-template pin, runtime constant) across five ecosystems. **Whenever you add a new version site, also update [`release.yaml`](.github/workflows/release.yaml) (bump + validate) and [`publish.yaml`](.github/workflows/publish.yaml) (per-job tag-vs-version assertion) — otherwise the tag will silently drift.** Full file-by-file breakdown: [`docs/VERSIONING.md`](docs/VERSIONING.md). End-to-end CI flow: [`docs/RELEASE_AND_PUBLISH.md`](docs/RELEASE_AND_PUBLISH.md). Go requires one additional module tag: `go/vX.Y.Z`.

## Workflows

- **release.yaml** — Triggered manually. Bumps version, creates tags + GitHub Release.
- **publish.yaml** — Triggered by tag push. Publishes to npm, PyPI, Maven Central, Go Module Proxy.
- **proto_sync.yaml** — Syncs proto files from upstream proto source.
- **generate-clients.yaml** — Regenerates language-specific code from protos.

## Starter Templates

All starter templates are buildable standalone projects using `my-provider` as the literal project name (and `MyProvider` as PascalCase). The unified CLI (`cli/`) replaces these literals with the actual project name during scaffolding. Go's module path is read from `go.mod.tmpl` at scaffold time and replaced with the `--module` value. Each template ships `dot-gitignore` which the scaffolder renames to `.gitignore`.

`cli/` is also the scaffolder of other products: `cli_sync.yaml` copies the files listed in `.github/workflows/cli-sync-config/<product>.yaml` into each product repo as a PR, overwriting them there. `config.go` is deliberately not in that list — it is where a product describes itself (`CLIConfig`: name and `Description` for usage, languages, roles, `JavaRepositories`/`JavaSDKArtifacts` for the Java template's registry choice and version pin, `NextSteps` printed after `init`, `RunSteps` for product-specific run commands, `PostScaffold` for anything else). Flags and usage text follow that config: a product without Go gets no `--module`, without `JavaRepositories` no `--repository`, without roles no `--role`. Keep the synced files free of product specifics and everything product-shaped behind `CLIConfig`; a downstream `config.go` that stops compiling after a sync is a breaking change of that contract.

## Build & Test (all languages)

```bash
cd go && go test ./...                            # Go
cd node/sdk && npm ci && npm run build && npm test # Node
cd python && uv sync --all-packages && uv run pytest -v  # Python
cd java && ./gradlew build                        # Java
cd csharp && dotnet test                          # C#
```

## Cross-Language Testing

Vectors, the Go helper, and the server-to-server matrix: [`docs/CROSS_LANGUAGE_TESTING.md`](docs/CROSS_LANGUAGE_TESTING.md). The shared behavior rules: [`docs/CROSS_SDK_RULES.md`](docs/CROSS_SDK_RULES.md).

Build the helper before these tests (`cd cross_test/go_helper && go build -o go_helper .`). The binary is not in the repository. A suite that skips because it is missing has not run. Build the helper and run that suite. Do not report the skip as a pass.

## Definition of Done

A rule change follows [`docs/CROSS_SDK_RULES.md`](docs/CROSS_SDK_RULES.md) ("How to change a rule"). Before a change is done, the cross-language tests must pass:

```bash
cd cross_test/go_helper && go build -o go_helper .   # Rebuild helper
cd cross_test/go_helper && go vet ./... && go test ./... # Helper's verifier + Go client against it
cd go && go vet ./... && go test -race ./...          # Go
cd node/sdk && npm ci && npm run build && npm test    # Node (includes cross-tests)
cd python && uv run pytest tests/cross_test/ -v       # Python ↔ Go
cd csharp && dotnet test --filter "CrossServerTests"  # C# ↔ Go
cd java && ./gradlew test --tests "*.CrossServerTests" # Java ↔ Go
```

## Signature Protocol

Every shared rule, and the vector or test that checks it: [`docs/CROSS_SDK_RULES.md`](docs/CROSS_SDK_RULES.md). Read that page before changing signing or verification in one SDK. Streaming: [`docs/STREAMING.md`](docs/STREAMING.md).

**gRPC-Web is out of scope for good.** The network never speaks it and no SDK supports it. Do not add handling, tests, vectors or docs for `application/grpc-web*`, and do not raise it in reviews or plans.

The gRPC dual path (the framed body, then the uncompressed message without its 5-byte prefix) is required, not defensive. See [`docs/java/SIGNATURE_VERIFICATION.md`](docs/java/SIGNATURE_VERIFICATION.md) before touching it.

## Releasing

**NEVER release manually.** Releases are handled exclusively by the `release.yaml` GitHub Actions workflow.

- DO NOT run `gh workflow run release.yaml` without explicit user request
- DO NOT create version tags manually (e.g., `git tag vX.Y.Z`) — the release workflow manages all tags
- DO NOT push tags directly — the workflow creates and pushes `vX.Y.Z`, `go/vX.Y.Z`, etc.
- The publish workflow (`publish.yaml`) is triggered automatically by tag push — never trigger it manually

When the user asks to release, trigger it via `gh workflow run release.yaml -f bump=<type> --ref master`. Use `patch` only when the release has nothing but fixes; a release with new API or breaking changes needs `minor` (see [`docs/RELEASE_AND_PUBLISH.md`](docs/RELEASE_AND_PUBLISH.md)). The user's choice of bump wins.

## Dependency updates

When triaging a Dependabot PR or bumping a library, follow [`docs/DEPENDENCY_UPDATES.md`](docs/DEPENDENCY_UPDATES.md). Non-crypto deps land in a single weekly `ci-batch` PR across all ecosystems (CI is the gate; read the changelogs in the batch); `.github/tools/sumtool` and `cross_test/go_helper` are updated by hand. Crypto / signing-path deps get solo PRs and the seven-step audit there: direct tests before the bump, then byte-identical cross-language vectors after it (PR #99 pattern). New deps appear as solo PRs until added to the allowlist in `.github/dependabot.yml`.

## Git Workflow

- Run builds/tests locally before suggesting commits
