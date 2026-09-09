# Unified CLI (`t0-init`)

`cli/` is a Go program that scaffolds a standalone T-0 Network provider project from the starter templates in this repository. It is built as a static binary, published on every GitHub Release as `t0-init`, and synced into other t-0 product repositories, where the same code runs under that product's name.

[`cli/README.md`](../cli/README.md) is the short how-to. This document is the reference for the binary, the templates and the sync, followed by maintainer how-tos.

---

## Installation

The installed binary is `t0-init`. The usage text, the banner and `version` print the product name `t0` from `config.go`; the file on disk, the release assets and both installers use `t0-init`.

### Release assets

`publish.yaml`'s `publish-cli` job uploads these to the GitHub Release `vX.Y.Z`:

| Asset | Platform |
|---|---|
| `t0-init-linux-amd64` | Linux x86_64 |
| `t0-init-linux-arm64` | Linux aarch64 |
| `t0-init-darwin-amd64` | macOS Intel |
| `t0-init-darwin-arm64` | macOS Apple silicon |
| `t0-init-windows-amd64.exe` | Windows x64 |
| `t0-init-windows-arm64.exe` | Windows ARM64 |
| `start.sh` | installer for Linux and macOS |
| `start.ps1` | installer for Windows |

`gh release upload` runs with `--clobber`, so re-running a failed publish replaces the assets in place.

### `start.sh` — Linux and macOS

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh
```

The script (`#!/bin/sh`, `set -eu`), step by step:

1. Maps `uname -s` to `linux`/`darwin` and `uname -m` to `amd64` (`x86_64`, `amd64`) or `arm64` (`aarch64`, `arm64`). Anything else exits 1 with `Error: unsupported OS: …` or `Error: unsupported architecture: …`.
2. Downloads `https://github.com/t-0-network/provider-sdk/releases/latest/download/t0-init-<os>-<arch>` into a `mktemp -d` directory with `curl -fsSL`, or `wget -qO` when `curl` is absent; with neither it exits 1 with `Error: curl or wget is required`.
3. `chmod +x`, then moves the file to `/usr/local/bin/t0-init` when `/usr/local/bin` is writable, otherwise to `${HOME}/.local/bin/t0-init` (directory created).
4. Prints `Installed t0-init to <dir>/t0-init`. When `<dir>` is not on `PATH` it adds:

   ```
   Add <dir> to your PATH:
     export PATH="<dir>:$PATH"
   ```

5. Ends with `Verify:` and `  t0-init --version`.

The script installs only; run `t0-init init …` afterwards.

### `start.ps1` — Windows

```powershell
iwr -useb https://github.com/t-0-network/provider-sdk/releases/latest/download/start.ps1 | iex
```

The script (`$ErrorActionPreference = "Stop"`), step by step:

1. Picks `arm64` when `$env:PROCESSOR_ARCHITECTURE` is `ARM64`, otherwise `amd64`; the asset is `t0-init-windows-<arch>.exe`.
2. Creates `$env:LOCALAPPDATA\t0-init\` and downloads the asset to `t0-init.exe` in it with `Invoke-WebRequest -UseBasicParsing`; a download failure prints `Failed to download: …` and exits 1.
3. Appends the directory to the **user** `PATH` (`[Environment]::SetEnvironmentVariable(…, "User")`) and to the current session's `$env:Path` when it is missing, printing `Added <dir> to user PATH (restart your terminal for it to take effect)`; otherwise `<dir> is already in PATH`.
4. Ends with `Verify:` and `  t0-init --version`.

### Pinning a version

Both installers resolve `releases/latest`. To install a specific release, download the asset for your platform from that release's URL and make it executable:

```bash
curl -fsSL -o t0-init https://github.com/t-0-network/provider-sdk/releases/download/v<X.Y.Z>/t0-init-<os>-<arch>
chmod +x t0-init
./t0-init --version
```

`--version` prints the version compiled in from the tag (`t0 <X.Y.Z>`). Move the file onto your `PATH` (`start.sh` uses `/usr/local/bin` or `~/.local/bin`) to run it as `t0-init`.

---

## Commands

```
Usage: t0 <command> [options]

Commands:
  init [options] <project-name>  Initialize a new T-0 Network provider project
  keygen                         Generate a new secp256k1 keypair
  version                        Show version

Languages: go, node, python, java, csharp

Init options (before or after the project name):
  --lang string        Language/ecosystem (required)
  --module string      Go module path (Go only)
  --repository string  Java SDK repository: jitpack|maven-central (Java only)
  --dir string         Target directory (default: ./<project-name>)
  --no-color           Disable colored output
  --version            Show version
```

This is what `t0-init` with no arguments prints (exit 2), and what `help`, `--help` and `-h` print (exit 0). An unknown command prints `unknown command "<name>"` on stderr, then the usage, exit 2.

### `init`

Creates a project. The full invocation in placeholder form is in [`cli/README.md`](../cli/README.md); the form `ci-cli.yaml` runs is:

```bash
t0-init init --lang=go --module=github.com/test/my-go-provider --no-color --dir=test-go my-go-provider
```

The project name is the single positional argument. Flags are Go `flag` flags: `--lang=go` and `--lang go` are equivalent, a single dash works, and after the first parse the remaining arguments are parsed again so flags may come before or after the name. `init --help` prints the flag set with defaults; `init --version` prints `<ProductName> init <version>` (`dev` for a local build) and exits before any validation.

| Flag | Default | Validation |
|---|---|---|
| `--lang` | — | Required (exit 2 when missing). One of `go`, `node`, `python`, `java`, `csharp` (exit 1 otherwise). |
| `--module` | the sanitized project name | Go module path. Registered because `config.go` lists `go`; applied only when `--lang=go`. |
| `--repository` | `jitpack` | `jitpack` or `maven-central`; validated when `--lang=java` (exit 1 on any other value). |
| `--dir` | `./<project-name>` | Target directory. Refused when it exists and contains at least one entry (exit 1); an existing empty directory is accepted. |
| `--no-color` | off | Removes the ANSI color codes from all output. |
| `--version` | — | Prints `<ProductName> init <version>` and returns. |

Checks run in this order, each on its own exit code:

1. `--version` — print and return.
2. Project name present, else `[ERROR] project name is required`, a blank line and `Usage: t0 init <project-name> --lang=<language>` on stderr (exit 2).
3. Sanitization: trim, lowercase, spaces to `-`, every character outside `[a-z0-9_-]` dropped. `"My Cool Provider!"` becomes `my-cool-provider`. An empty result is `[ERROR] invalid project name — use only lowercase letters, numbers, hyphens, underscores` (exit 1).
4. The PascalCase form (split on `-` and `_`, first letter of each part uppercased) must start with a letter, else `[ERROR] project name must start with a letter (got "123abc")` (exit 1).
5. `--lang` present, else `[ERROR] --lang is required (options: go, node, python, java, csharp)` (exit 2); known, else `[ERROR] unknown language "rust" (options: go, node, python, java, csharp)` (exit 1).
6. For `--lang=java`, `--repository` in the list, else `[ERROR] unknown repository "nexus" (options: jitpack, maven-central)` (exit 1).
7. Target directory empty or absent, else `[ERROR] directory "<dir>" already exists and is non-empty` (exit 1).
8. `--module` empty and `--lang=go`: the module path becomes the sanitized project name.

`init` records whether it created the target directory. When a later step fails it removes the directory only if it created it; a directory that existed beforehand is kept.

What `--module` rewrites: the Go template is embedded with its module path replaced by `{{MODULE_PATH}}`, so `go.mod` becomes `module <path>` and the imports in `cmd/main.go` become `"<path>/internal"` and `"<path>/internal/handler"`.

What `--repository` rewrites: the Java template declares `val sdkRepository = "jitpack"` in `build.gradle.kts`; `--repository=maven-central` rewrites that one line to `val sdkRepository = "maven-central"`. The build file itself selects the repository and the coordinates from that value — `https://jitpack.io` with `com.github.t-0-network:provider-sdk`, or Maven Central with `network.t-0:provider-sdk-java`. A release build of the CLI additionally pins both coordinates from `:+` to its own version; a `dev` build leaves `:+`.

### `keygen`

Prints a fresh secp256k1 keypair and exits 0:

```
Private key: 0x<64 hex characters>
Public key:  0x04<128 hex characters>
```

The public key is the 65-byte uncompressed form. `init` uses the same generator for the project's `.env`.

### `version`, `--version`, `-v`

Print `t0 <version>` — `t0 dev` for a local build, `t0 <X.Y.Z>` for a release build, where the version comes from `-ldflags "-X main.Version=<X.Y.Z>"`.

### `help`, `--help`, `-h`

Print the usage block above, exit 0.

---

## What `init` writes

Steps, in the order they run and print:

1. **Template extraction** — `[INFO] Extracting template files...` / `[OK] Template files extracted`. Every file under the embedded `internal/embed/<lang>/` is written into the target directory. File names and text contents have `my-provider` replaced by the project name and `MyProvider` by its PascalCase form; a `.tmpl` suffix is stripped; `dot-gitignore` becomes `.gitignore`; for Go, `{{MODULE_PATH}}` becomes the module path; for Java, the repository line and SDK pins described under `--repository`. Files with a binary extension (`.jar`, `.class`, `.exe`, `.png`, `.jpg`, `.gif`, `.ico`, `.zip`, `.gz`, `.tar`, `.woff`, `.woff2`, `.ttf`) are copied byte for byte. `gradlew` and `*.sh` are written with mode `0755`, everything else `0666` (before umask).
2. **Keypair** — `[INFO] Generating secp256k1 keypair...` / `[OK] Keypair generated`.
3. **`.env`** — `[INFO] Creating .env file...` / `[OK] Environment configured`. `.env.example` is read from the project; the first active `PROVIDER_PRIVATE_KEY=` line (or `PRIVATE_KEY=`) is replaced whole with the private key, and the marker line `# your_public_key_here` is replaced with `# <public key>`. A template with no marker gets `# Public key for the line above (share it with t-0): <public key>` inserted under the key line. The result is written to `.env` and `chmod 0600`; `.env` is written only when the template ships `.env.example`.
4. **Completion output**.

The generated `.env` for every language (Node's template sets `PORT=3000`, the others `PORT=8080`):

```
# T-0 Network Provider Configuration
# Copy this file to .env and fill in your values.
# The starter CLI generates .env automatically with a fresh keypair.

# Your provider's private key (secp256k1, generated by the starter CLI)
PROVIDER_PRIVATE_KEY=0x<64 hex characters>

# Your provider's public key (share with the T-0 team to register your provider)
# 0x04<128 hex characters>

# T-0 Network's public key (used to verify incoming request signatures)
NETWORK_PUBLIC_KEY=0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b

# T-0 Network API endpoint
TZERO_ENDPOINT=https://api-sandbox.t-0.network

# Port for your provider server
PORT=8080

# Quote publishing interval in milliseconds (default: 5000)
# QUOTE_PUBLISHING_INTERVAL=5000
```

### Completion output

The full terminal output of `t0-init init --lang=go my-provider --dir /work/my-provider` (colors stripped; the banner, `[INFO]` and paths are blue, `[OK]` and the success box green, "Your public key" and "Next Steps:" yellow):

```

+-----------------------------------------------------------+
|     T0 — Project Initializer                          |
+-----------------------------------------------------------+

[INFO] Creating project: my-provider (go)
[INFO] Extracting template files...
[OK] Template files extracted
[INFO] Generating secp256k1 keypair...
[OK] Keypair generated
[INFO] Creating .env file...
[OK] Environment configured

+-----------------------------------------------------------+
|                  Project Created Successfully!            |
+-----------------------------------------------------------+

Your project is ready at: /work/my-provider

Your public key (share with T-0 team):
0x0409a7133d21469bc65da30df71fd611c309a804ffc5be901286c90cab06ae29b895f2631511b36e45d12bd2553cafab68e57a4b70bd4460b7c00bba3c1ccea114

Next Steps:

  1. Navigate to your project:
     cd /work/my-provider

  2. Run the application:
     go run ./cmd

```

`Your project is ready at:` is always absolute; the `cd` line echoes `--dir` as given (relative when you passed a relative path). `--repository` leaves the output unchanged. The lines that differ per language:

| `--lang` | `Creating project:` line | Step 2 |
|---|---|---|
| `go` | `my-provider (go)` | `Run the application:` — `go run ./cmd` |
| `node` | `my-provider (node)` | `Install dependencies and run:` — `npm install && npm run dev` |
| `python` | `my-provider (python)` | `Install dependencies and run:` — `uv sync && uv run python -m provider.main` |
| `java` | `my-provider (java)` | `Run the application:` — `./gradlew run` |
| `csharp` | `my-provider (csharp)` | `Run the application:` — `dotnet run` |

### Files per language

Top level of each scaffold, from the template of the same name:

| `--lang` | Top-level entries |
|---|---|
| `go` | `.env`, `.env.example`, `.gitignore`, `Dockerfile`, `go.mod`, `go.sum`, `cmd/`, `internal/` |
| `node` | `.dockerignore`, `.env`, `.env.example`, `.gitignore`, `Dockerfile`, `package.json`, `tsconfig.json`, `src/` |
| `python` | `.env`, `.env.example`, `.gitignore`, `Dockerfile`, `README.md`, `pyproject.toml`, `src/` |
| `java` | `.env`, `.env.example`, `.gitignore`, `Dockerfile`, `README.md`, `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle/`, `src/` |
| `csharp` | `.env`, `.env.example`, `.gitignore`, `Dockerfile`, `Program.cs`, `appsettings.json`, `<project-name>.csproj`, `Services/` |

Name substitution as seen in the scaffolds: Node's `package.json` `"name"`, Java's `settings.gradle.kts` `rootProject.name`, the C# `.csproj` file name, its `ENTRYPOINT ["dotnet", "<project-name>.dll"]` in `Dockerfile`, and the C# namespace `<PascalName>.Services`.

---

## Templates

| `--lang` | Template directory |
|---|---|
| `go` | `go/starter/template/` |
| `node` | `node/starter/template/` |
| `python` | `python/starter/src/t0_provider_starter/template/` |
| `java` | `java/starter/template/` |
| `csharp` | `csharp/starter/template/` |

The convention is `<lang>/starter/template/`; `generate.go` overrides it per language with `lang=path`, and Python is the one override:

```go
//go:generate go run ./internal/sync go node python=python/starter/src/t0_provider_starter/template java csharp
```

Every template is a buildable standalone project whose project name is the literal `my-provider` (PascalCase `MyProvider`, used by the C# namespace and the `<RootNamespace>` in the `.csproj`). Each ships `dot-gitignore` rather than `.gitignore`, because Gradle and NuGet packaging strip dotfiles; the scaffolder renames it. The Go template's own module path, `github.com/t-0-network/provider-sdk/go/starter/template`, is rewritten to `{{MODULE_PATH}}` when it is embedded. Python and Java templates ship a `README.md` that becomes the scaffolded project's README.

---

## Maintainers — embedding the templates

`go generate ./...` in `cli/` runs `internal/sync`, which for each language deletes `cli/internal/embed/<lang>/` and copies the template in:

- Skipped directories: `node_modules`, `dist`, `build`, `__pycache__`, `.venv`, `.git`, `.gradle`, `.idea`, `.vs`, `.DS_Store`, `obj`, `bin`, `.pytest_cache`, `.ruff_cache`. Skipped files: `.DS_Store`, `Thumbs.db`, `.env`, and every `.env.*` except `.env.example` — a developer's local key never enters the template.
- Go only: `*.go`, `go.mod` and `go.sum` gain a `.tmpl` suffix so they are embedded as data rather than compiled into the CLI, and the module path is replaced with `{{MODULE_PATH}}` in text files.
- The executable bit is preserved (`gradlew`), other files are written `0666`.

`cli/internal/embed/` is generated output; `scaffold.go` embeds it with `//go:embed all:internal/embed`. A language with no directory under `internal/embed/` in the build fails at scaffold time with `template not found for lang=<lang> — run 'go generate ./...' first`.

---

## Maintainers — product instances and the sync

`cli/` is the upstream of a CLI that other t-0 product repositories build under their own name. The product repositories and the files they receive are declared in one config file per product:

| Config | Target repository | Pull request label | Commit message |
|---|---|---|---|
| `.github/workflows/cli-sync-config/usdt-pay-sdk.yaml` | `t-0-network/usdt-pay-sdk` | `sync cli` | `Sync unified CLI from provider-sdk` |

[`cli_sync.yaml`](../.github/workflows/cli_sync.yaml) runs on a push to `master` that touches `cli/**` (with `cli/config.go`, `cli/generate.go`, `cli/start.sh` and `cli/start.ps1` excluded from the trigger), the workflow itself, or `.github/workflows/cli-sync-config/**`, and on `workflow_dispatch`. It mints a GitHub App token scoped to the target repository and runs `wadackel/files-sync-action@v4` with the product's config, which opens a pull request in the product repository overwriting these ten files at the same paths:

```
cli/main.go
cli/scaffold.go
cli/keygen.go
cli/keygen_test.go
cli/env.go
cli/go.mod
cli/go.sum
cli/internal/sync/main.go
cli/config_test.go
cli/scaffold_test.go
```

Everything else under `cli/` is owned by the repository it sits in and is never touched by the sync. In this repository that is `README.md`, `config.go`, `generate.go`, `starters_test.go`, `internal/sync/main_test.go`, `start.sh`, `start.ps1` and `internal/embed/`. A product repository keeps its own set alongside the ten synced files — its `config.go`, generator, tests, installers — which its own `docs/CLI.md` lists.

### The `CLIConfig` contract

`config.go` is the product's side of the contract — a single `var Config = CLIConfig{…}` that the synced files read. provider-sdk's own sets `ProductName` to `t0`, `Command` to `t0 init`, `Description` to `a new T-0 Network provider project`, `Languages` to `go, node, python, java, csharp`, `JavaRepositories` to `jitpack, maven-central` and `JavaSDKArtifacts` to `com.github.t-0-network:provider-sdk` and `network.t-0:provider-sdk-java`; `Command` is the product's `init` invocation as it appears in the missing-name error. Every field and what it changes:

| Field | Effect |
|---|---|
| `ProductName` | `Usage: <ProductName> <command> [options]`; the banner `<PRODUCTNAME> — Project Initializer`; `version` prints `<ProductName> <Version>`; `init --version` prints `<ProductName> init <Version>`. |
| `Command` | The usage line of the missing-name error: `Usage: <Command> <project-name> --lang=<language>` — for provider-sdk `Usage: t0 init <project-name> --lang=<language>`. |
| `Description` | The `init` line in usage: `Initialize <Description>`. Empty: `a new <ProductName> project`. |
| `RoleRequired`, `DefaultRole` | `--role` is registered when either is set. Usage shows `--role string        Role (required)` or `Role (default: <DefaultRole>)`; with `RoleRequired` an empty role is `[ERROR] --role is required` (exit 2). The template root becomes `internal/embed/<lang>/<role>`, and a missing template lists the available roles. provider-sdk leaves both empty. |
| `Languages` | The accepted `--lang` values, in usage and validation. `--module` is registered when the list contains `go`. `TestRun_InstantiatesEveryStarter` scaffolds every entry. |
| `JavaRepositories` | The accepted `--repository` values; the first is the default and must match the template's `val sdkRepository = "…"` line, which is rewritten when a different value is chosen. Empty: no `--repository` flag is registered and nothing is rewritten. |
| `JavaSDKArtifacts` | Coordinates the Java template depends on at version `+`; a release build (`Version` other than `""` or `dev`) rewrites each `"<artifact>:+"` to `"<artifact>:<Version>"`. Empty: the template pins its SDK itself. |
| `NextSteps` | Lines printed after `1. Navigate to your project:` and before the run command, numbered from 2 — what the user must do before the project works. |
| `RunSteps` | `map[string]RunStep` overriding the built-in per-language run command. Lookup order: `<lang>/<role>` → `<lang>` → built-in default. `RunStep.Label` is printed verbatim as the numbered line (include the trailing colon); `RunStep.Command` is the highlighted line below it. Nil or empty keeps the built-in per-language command. |
| `PostScaffold` | `func(ScaffoldOpts) error` run after the scaffold and before the keypair. An error aborts `init` and removes the target directory only when `init` created it. |

`ScaffoldOpts` carries `Lang`, `Role`, `ProjectName`, `ProjectDir`, `ModulePath`, `JavaRepo` and `Version` to `PostScaffold`.

The rule: the ten synced files carry nothing product-specific — no product name, language list, coordinates, next steps or file (test fixtures that exercise the generic mechanism with concrete values are not product-specific) — and everything product-shaped lives behind `CLIConfig`. A downstream `config.go` that stops compiling after a sync is a breaking change of that contract; a new field's zero value must keep the existing behavior.

---

## Maintainers — tests and CI

| File | Owner | Covers |
|---|---|---|
| `cli/config_test.go` | synced | Usage follows `Config` (no `--module`/`--repository` without Go/Java, `--role (required)`); `NextSteps` numbering; `RunSteps` role, language and zero-value lookup; Java pins only configured artifacts and never on `dev`; repository rewrite only for a non-default value; `.env` marker, no-marker, comment-line, `0600` and no-`.env.example` behaviors. |
| `cli/scaffold_test.go` | synced | Embed paths are forward-slash; `toPascalCase` and `sanitizeProjectName` tables; a `PostScaffold` error keeps a pre-existing directory. |
| `cli/starters_test.go` | this repository | Starter languages match `Config.Languages`; embedded templates have Dockerfile and exactly one dockerignore variant; every language scaffolds and yields `.gitignore`, `.env`, `Dockerfile`, `.dockerignore` without `dot-` variants and entry files; fresh private key, derived public key, `NETWORK_PUBLIC_KEY` equals example, `0600` permissions. |
| `cli/keygen_test.go` | synced | Key format and uniqueness. |
| `cli/internal/sync/main_test.go` | this repository | Skip lists; `.tmpl` renaming for Go only; `{{MODULE_PATH}}` replacement. |

Locally:

```bash
cd cli && go generate ./... && go build ./... && go test ./...
```

Workflows that run them:

- [`ci-cli.yaml`](../.github/workflows/ci-cli.yaml) (`CI / Unified CLI`) — on push and pull request to `master` touching `cli/**`, any of the five template directories, `node/sdk/**` or the workflow. Job `cli` (Ubuntu) runs `go generate`, builds with `-X main.Version=0.0.0-local`, runs `go test -v ./...`, scaffolds all five languages with `--no-color --dir=test-<lang>` (Go with `--module=github.com/test/my-go-provider`, Java with `--repository=maven-central`), asserts `.env`, `.gitignore` and `PROVIDER_PRIVATE_KEY=0x` in each, `test -x test-java/gradlew`, the `0.0.0-local` pin in `test-java/build.gradle.kts` and the name in `settings.gradle.kts`, `test-csharp/my-csharp-provider.csproj` — then verifies each scaffold against the SDKs built from the same tree: the Go, Node, Java and C# scaffolds compile (Go through a `file://` GOPROXY, Node from `npm pack`, Java from `publishToMavenLocal -Pversion=0.0.0-local`, C# from a local NuGet source), and the Python scaffold resolves and imports the tree's wheel (`uv add` the wheel, `uv sync`, `import t0_provider_sdk`). Job `cli-windows` builds `t0-init.exe`, runs the tests and scaffolds Node.
- [`release.yaml`](../.github/workflows/release.yaml) job `build-cli` — `go generate ./... && go build ./... && go test ./...`; the `release` job needs it, so a release is aborted when the CLI is broken.
- [`publish.yaml`](../.github/workflows/publish.yaml) job `build-cli` — the same three commands; every `publish-*` job needs it.

### `publish-cli`

Job `Publish Unified CLI` in `publish.yaml`; `needs: [build-go, build-node, build-java, build-python, build-csharp, build-cli, publish-go, publish-node-sdk, publish-python-sdk, publish-java, publish-csharp]`. Steps:

1. Version from the tag: `${GITHUB_REF#refs/tags/v}`.
2. `go generate ./...` in `cli`.
3. Cross-compile with `-ldflags "-X main.Version=${VERSION}"` for `linux/amd64`, `linux/arm64`, `darwin/amd64`, `darwin/arm64`, `windows/amd64`, `windows/arm64` into `dist/t0-init-<os>-<arch>` (`.exe` on Windows).
4. `gh release upload "v${VERSION}" dist/t0-init-* cli/start.sh cli/start.ps1 --clobber`, with a GitHub App token.

The version compiled in is what pins the Java template's SDK coordinates in a scaffolded project, so a release CLI scaffolds Java projects against the SDK released with it. The release process around this job is in [`RELEASE_AND_PUBLISH.md`](./RELEASE_AND_PUBLISH.md).

---

## Maintainers — adding a language or changing a template

### Changing a template

Edit the template under its directory from the [Templates](#templates) table, keeping `my-provider`/`MyProvider` as the project name and `dot-gitignore` as the ignore file. `ci-cli.yaml` triggers on the template path, regenerates the embed, scaffolds the language and verifies the result against the tree's SDK (a compile; for Python, an install and import). Locally, `go generate ./... && go test ./...` in `cli/` covers the scaffolding step; the per-language verification runs only in CI. `internal/embed/` is regenerated, never edited.

Files whose contents must survive untouched need a binary extension from the list in `scaffold.go` (`binaryExts`); any other file goes through the `my-provider`/`MyProvider` replacement.

### Adding a language

1. Create `<lang>/starter/template/` as a buildable project named `my-provider`, with `.env.example` (carrying `PROVIDER_PRIVATE_KEY=` and the `# your_public_key_here` marker) and `dot-gitignore`. A location outside the convention is passed as `<lang>=<path>` in `generate.go`.
2. Add `<lang>` to the `go:generate` line in `generate.go` and to `Languages` in `config.go`. Usage, validation and `TestRun_InstantiatesEveryStarter` follow from the list.
3. Add the language's case to the `switch opts.Lang` in `printCompletion` in `main.go` — the step-2 heading and the run command. `main.go` is synced, so the case reaches every product on the next sync.
4. Add a scaffold step and a compile-verify step for the language to `ci-cli.yaml`, and its template path to the workflow's `paths` lists.
5. For a product that should offer the language: it adds its own `<lang>/starter/template/`, the `generate.go` entry and the `Languages` entry in its repository. The sync carries the ten files, never templates.
