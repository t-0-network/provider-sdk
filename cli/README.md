# t0-init

`t0-init` creates a standalone T-0 Network provider project in Go, Node, Python, Java or C# from the starter templates in this repository, with a fresh secp256k1 keypair already written to `.env`.

## Install

Linux and macOS -- the installer picks the release asset for your OS and architecture and puts it in `/usr/local/bin` when that is writable, otherwise in `~/.local/bin`:

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh
```

Windows (PowerShell) -- installs to `%LOCALAPPDATA%\t0-init\t0-init.exe` and adds that directory to your user `PATH`:

```powershell
iwr -useb https://github.com/t-0-network/provider-sdk/releases/latest/download/start.ps1 | iex
```

Both installers print what they did and end with the verification command:

```bash
t0-init --version
```

## Create a project

Install first, then run `init` -- the installers only download the binary:

```bash
t0-init init --lang=<language> <project-name>
```

The project name is lowercased and reduced to `[a-z0-9_-]`; after any leading `-` or `_`, the first character must be a letter. Flags go before or after the name.

| `--lang` | Language-specific flag | Run command printed by `init` |
|---|---|---|
| `go` | `--module <path>` -- Go module path, default: the project name | `go run ./cmd` |
| `node` | -- | `npm install && npm run dev` |
| `python` | -- | `uv sync && uv run python -m provider.main` |
| `java` | `--repository jitpack\|maven-central` -- where the SDK is resolved from, default `jitpack` | `./gradlew run` |
| `csharp` | -- | `dotnet run` |

Optional for every language:

| Flag | Effect |
|---|---|
| `--dir <path>` | Target directory, default `./<project-name>`. An existing directory is accepted when it is empty. |
| `--no-color` | Plain output without ANSI colors. |

## What you get

- The template for the language with every `my-provider` and `MyProvider` literal replaced by your project name and its PascalCase form, in file names as well as file contents.
- `.gitignore`, from the template's `dot-gitignore`.
- `.env` with mode `0600`: `.env.example` with `PROVIDER_PRIVATE_KEY=` set to a freshly generated key and the matching public key recorded in the comment block below it.
- The public key printed on the terminal -- share it with the T-0 team to register your provider.
- Next steps printed on the terminal: `cd` into the project, then the run command from the table above.

## Generate a keypair

`init` writes the project's keypair itself. For a key outside a scaffold -- a rotation, or a project that was not scaffolded -- `keygen` prints a fresh pair and exits:

```bash
t0-init keygen
```

It prints `Private key: 0x...` and `Public key:  0x04...`. Put the private key in `PROVIDER_PRIVATE_KEY` in `.env`, replace the public key `init` recorded in the comment below it so the two stay matched, and share the new public key with the T-0 team.

## Maintainer notes

`cli/` is the upstream of the unified CLI for every t-0 product repository. [`cli_sync.yaml`](../.github/workflows/cli_sync.yaml) copies the files listed in `.github/workflows/cli-sync-config/<product>.yaml` into each product repository as a pull request, so anything product-shaped stays behind `CLIConfig` in `config.go`, which is never synced. Templates live at `<lang>/starter/template` (Python's path is overridden in `generate.go`) and `go generate ./...` embeds them under `internal/embed/`; the `my-provider`/`MyProvider` literals and `dot-gitignore` are replaced when a project is scaffolded, and the Go template's module path becomes `{{MODULE_PATH}}` at embed time. `config_test.go`, `scaffold_test.go`, `keygen_test.go` and `internal/sync/main_test.go` run through `ci-cli.yaml` on every push and pull request to `master` that touches `cli/`, a template, `node/sdk/**` or the workflow itself, and that workflow also scaffolds all five languages and verifies each project against the SDKs built from the tree (a compile, or for Python an install and import). The `publish-cli` job in `publish.yaml` cross-compiles the binary for six OS/architecture pairs and uploads them with `start.sh` and `start.ps1` to the GitHub Release. Everything else -- every flag, what `init` writes, the `CLIConfig` fields, adding a language -- is in [`docs/CLI.md`](../docs/CLI.md).
