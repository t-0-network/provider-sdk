# Contributing

This repository holds the T-0 Network provider SDKs for Go, TypeScript, Python, Java and C#, a starter template for each, and the `t0-init` CLI that scaffolds a project from those templates. This guide covers how to build, test and change them. How to use the SDKs is in the [README](README.md) and each SDK's README. How they work is in [`docs/`](docs/).

## Prerequisites

- [buf](https://buf.build/docs/installation/), for protobuf code generation
- [Go](https://go.dev/dl/) 1.27+
- [Node.js](https://nodejs.org/) 20.19+ with npm
- [Python](https://www.python.org/downloads/) 3.13+ with [uv](https://docs.astral.sh/uv/)
- [Java](https://adoptium.net/) 17+ (the Gradle wrapper is included)
- [.NET](https://dotnet.microsoft.com/download) 10 SDK

## Repository layout

```
proto/          Shared protobuf definitions (source of truth)
cli/            Unified starter CLI (t0-init), embeds every starter template
go/             Go SDK + starter template
node/sdk/       TypeScript SDK (@t-0/provider-sdk)
node/starter/   TypeScript starter template
python/sdk/     Python SDK (t0-provider-sdk)
python/starter/ Python starter template
java/sdk/       Java SDK (network.t-0:provider-sdk-java)
java/starter/   Java starter template
csharp/         C# SDK + starter template
cross_test/     Cross-language test vectors + shared Go helper
docs/           Design and process documentation
```

## Build and test

These are the checks CI runs. Run them for every area you change:

```sh
cd go && go vet ./... && go test -race ./...
cd node/sdk && npm ci && npm run build && npm test
cd python && uv sync --all-packages && uv run ruff check . && uv run ruff format --check . && uv run pytest
cd java && ./gradlew build
cd csharp && dotnet test
cd cli && go generate ./... && go test ./...
```

The Node, Python, Java and C# suites include cross-language tests against a shared Go helper, so build the helper first (see below).

To try the CLI against your checkout, run `go generate` first, then build the CLI and scaffold a project. `go generate` copies the starter templates from your tree into the CLI.

```sh
cd cli && go generate ./... && go build -o /tmp/t0-init .
/tmp/t0-init init --lang=java --dir=/tmp/my-test-project my-test-project
```

The CLI's flags are documented in [cli/README.md](cli/README.md). [docs/CLI.md](docs/CLI.md) covers how it embeds the templates and what it syncs to other repositories.

## Cross-language tests

All SDKs check their crypto against the vectors in `cross_test/test_vectors.json`. They also call a Go helper, and the helper calls them back, to check that signed requests verify in both directions. Build the helper and run its own tests:

```sh
cd cross_test/go_helper && go build -o go_helper . && go vet ./... && go test ./...
```

The suites above run the cross tests: Python in `tests/cross_test/`, Java in `CrossServerTests`, and Node and C# as part of their default suites. In CI they fail rather than skip when the helper is missing. [docs/CROSS_LANGUAGE_TESTING.md](docs/CROSS_LANGUAGE_TESTING.md) covers the vectors, the helper's commands and the steps for adding an SDK.

## Definition of done

Before you open a pull request, check that:

- The checks above pass for every area you changed. The helper is rebuilt, and the cross tests pass in all five SDKs.
- A change to behaviour that the SDKs share is made in all five SDKs. Update the shared test rows or vectors too: `cross_test/test_vectors.json`, and the tables every SDK tests, such as the base URL rows.
- The READMEs and docs that describe the changed behaviour are updated.
- Generated code was regenerated, not edited by hand.

## Rules that are easy to break

- **Sign and verify raw wire bytes.** Protobuf encoding is not canonical, so re-encoding a parsed message gives different bytes. Never sign or verify re-serialized output, in any language.
- **Java's two verification paths are both required.** The Java provider accepts a signature over the unframed message or over the gRPC-framed body, because the network signs either one depending on its transport. See [docs/java/SIGNATURE_VERIFICATION.md](docs/java/SIGNATURE_VERIFICATION.md).
- **In a stream, only the first message is signed, and it is sent at once.** Never buffer a stream to sign it. Every SDK follows [docs/STREAMING.md](docs/STREAMING.md).
- **Every place that holds the version is listed in the release workflows.** If you add one, add it to `release.yaml` and `publish.yaml` too. See [docs/VERSIONING.md](docs/VERSIONING.md).
- **Dependencies follow [docs/DEPENDENCY_UPDATES.md](docs/DEPENDENCY_UPDATES.md).** Dependabot batches ordinary updates. Dependencies on the signing path get their own pull request and an audit.
- **`cli/` is copied into other product repositories.** Keep product-specific values in `config.go`. See [docs/CLI.md](docs/CLI.md).

## Protobuf code generation

`proto/` is the source of truth. The `proto_sync.yaml` workflow syncs it from the backend, and `generate-clients.yaml` then regenerates the committed code. To regenerate locally, run this from the repository root:

```sh
uv sync --project python --all-packages   # the Python connect plugin runs from this workspace
buf generate
```

This writes `go/api/`, `node/sdk/src/common/gen/`, `python/sdk/src/t0_provider_sdk/api/` and `csharp/sdk/T0.ProviderSdk/Api/`. Java generates its code at build time. For language-specific details, see [docs/python/PITFALLS.md](docs/python/PITFALLS.md) and [docs/java/PROTO_SCHEMA_MANAGEMENT.md](docs/java/PROTO_SCHEMA_MANAGEMENT.md).

## Pull requests

- Branch from `master`, and keep each pull request to one topic.
- Write the title as a [Conventional Commit](https://www.conventionalcommits.org/), for example `feat(cli): …`, `fix: …`, `docs: …` or `chore(deps): …`. Pull requests are squash-merged, so the title becomes the commit message on `master`.
- In the description, describe each change in behaviour relative to `master`, and list the breaking changes separately. They decide the version bump of the next release.
- A pull request needs passing CI and one approving review.

## Releases

Maintainers release only from GitHub Actions. They run the Release workflow (`release.yaml`) with a `patch`, `minor` or `major` bump. It updates every version, tags `vX.Y.Z`, and the tag starts `publish.yaml`. Never create or push tags, and never run `publish.yaml` by hand. [docs/RELEASE_AND_PUBLISH.md](docs/RELEASE_AND_PUBLISH.md) covers how to choose the bump, what each publish job does, and the secrets the workflows use.

## Further reading

- [docs/STREAMING.md](docs/STREAMING.md): the streaming rules every SDK follows
- [docs/CROSS_LANGUAGE_TESTING.md](docs/CROSS_LANGUAGE_TESTING.md): test vectors and the Go helper
- [docs/HEALTH_SERVICE.md](docs/HEALTH_SERVICE.md): the health service
- [docs/CLI.md](docs/CLI.md): the CLI, its templates and the product-repository sync
- [docs/VERSIONING.md](docs/VERSIONING.md) and [docs/RELEASE_AND_PUBLISH.md](docs/RELEASE_AND_PUBLISH.md): versions, releases and publishing
- [docs/DEPENDENCY_UPDATES.md](docs/DEPENDENCY_UPDATES.md): handling Dependabot pull requests, and the audit for signing-path libraries
- Go: [go/README.md](go/README.md)
- TypeScript: [node/sdk/README.md](node/sdk/README.md)
- Python: [python/README.md](python/README.md), [docs/python/ARCHITECTURE.md](docs/python/ARCHITECTURE.md), [docs/python/PITFALLS.md](docs/python/PITFALLS.md)
- Java: [java/README.md](java/README.md), [java/sdk/README.md](java/sdk/README.md), [docs/java/](docs/java/) (signature verification, proto schemas, GitHub and Maven Central setup, past issues)
- C#: [csharp/README.md](csharp/README.md), [docs/csharp/ARCHITECTURE.md](docs/csharp/ARCHITECTURE.md), [docs/csharp/QUICKSTART.md](docs/csharp/QUICKSTART.md)
