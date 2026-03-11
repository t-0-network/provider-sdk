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
go/             Go SDK
node/sdk/       TypeScript SDK (@t-0/provider-sdk)
node/starter/   TypeScript starter CLI
python/sdk/     Python SDK (t0-provider-sdk)
python/starter/ Python starter CLI
java/sdk/       Java SDK (provider-sdk-java)
java/cli/       Java provider-init CLI
java/starter/   Java starter template
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
uv sync
uv run ruff check .
uv run mypy .
uv run pytest
```

### Java

```sh
cd java
chmod +x gradlew
./gradlew build --no-daemon
```

## Protobuf Code Generation

Proto files in `proto/` are the source of truth. Code is generated via `buf generate`:

```sh
# Generate Go, Node, Python clients (Java generates at build time via Gradle plugin)
buf generate
```

Generated code locations:
- Go: `go/api/`
- Node: `node/sdk/src/common/gen/`
- Python: `python/sdk/src/t0_provider_sdk/api/`
- Java: `java/sdk/gen/java/` (generated at build time, not committed)

## Testing

All languages share test vectors in `cross_test/test_vectors.json` for cross-language crypto compatibility.

Run tests per language:
- **Go**: `cd go && go test -v ./...`
- **Node**: `cd node/sdk && npm test`
- **Python**: `cd python && uv run pytest`
- **Java**: `cd java && ./gradlew test --no-daemon`

## Release Process

1. Trigger the **Release** workflow via GitHub Actions (`workflow_dispatch`)
2. Select bump type: `patch`, `minor`, or `major`
3. The workflow automatically:
   - Validates all builds
   - Bumps version in all language files
   - Creates a git tag (`vX.Y.Z`) and GitHub Release
   - Triggers the **Publish** workflow which publishes to npm, PyPI, Maven Central, and Go Module Proxy
