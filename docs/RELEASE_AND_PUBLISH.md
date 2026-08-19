# Release & Publish CI Flow

Two workflows coordinate every release:

- **[`release.yaml`](../.github/workflows/release.yaml)** — manually triggered. Bumps versions across the monorepo, validates them, commits, tags, and creates a GitHub Release. Does **not** publish artifacts.
- **[`publish.yaml`](../.github/workflows/publish.yaml)** — triggered automatically by a `vX.Y.Z` tag push. Builds, validates each ecosystem's tag-vs-version match, then publishes to the per-ecosystem registry.

A complete release is `release.yaml` → `publish.yaml`. Never trigger `publish.yaml` manually.

For *which files* hold versions, see [`VERSIONING.md`](./VERSIONING.md). For *what SystemService is*, see [`SYSTEM_SERVICE.md`](./SYSTEM_SERVICE.md).

---

## release.yaml — manual bump

Triggered by `gh workflow run release.yaml -f bump=<patch|minor|major> --ref master`. Default bump is `patch`. Steps in order:

1. **Build gate** — `build-go`, `build-node`, `build-java`, `build-python`, `build-csharp` all compile in parallel against the current commit. If any fails, the release is aborted and nothing changes.

2. **`release` job** runs only after the gate. Steps:

   1. **Calculate version** — read latest tag, parse semver, increment by the requested bump. Output: e.g. `1.1.15`.
   2. **Bump package-level versions** — Node (`npm version` for sdk + starter), Python (`sed` on both `pyproject.toml`s), Java (`sed` on `gradle.properties`), C# (`sed` on both `.csproj`s).
   3. **Bump SDK runtime version constants** — the four files that `SystemService.Health` reads:
      - `go/sdkversion/version.go`
      - `node/sdk/src/version.ts`
      - `python/sdk/src/t0_provider_sdk/_version.py`
      - `java/sdk/src/main/resources/META-INF/sdk-version.properties`
   4. **Bump starter-template SDK pins** — only for ecosystems with rewritten pins (Go `go.mod`, Node `package.json` caret, C# embedded template). Python and Java starters use floating versions and are not edited.
      - The Go step rewrites the `go.mod` require line **and** records the matching `go.sum` entry. See [Precomputing the Go template's `go.sum`](#precomputing-the-go-templates-gosum) below for how it can do that before the tag exists.
   5. **Validate updated files** — re-greps every package-level, runtime-constant, and starter-template version site and confirms they all equal the calculated version. Any mismatch fails the release before tagging.
   6. **Commit and tag** — single commit `Release X.Y.Z`, root tag `vX.Y.Z`.
   7. **Create GitHub Release** — `vX.Y.Z` with that title.

   (The `go/*` module tags are **not** created here — they're created by `publish.yaml`'s `publish-go` job, right before it warms the proxy. See below.)

3. **Tag push triggers `publish.yaml`** automatically (next section).

### Precomputing the Go template's `go.sum`

`go/starter/template` is a nested module that pins `github.com/t-0-network/provider-sdk/go vX.Y.Z`. Step 4 writes the new version into its `go.mod` — but that version has no git tag yet, since `publish.yaml` creates it minutes later. It still records the correct `go.sum` entry, because **a module's `h1:` hash is a function of its source tree, not of its tag**:

```
go -C .github/tools/sumtool run . "$GITHUB_WORKSPACE/go" "v$VERSION" "$PROXY_DIR"
cd go/starter/template
go mod edit -require "github.com/t-0-network/provider-sdk/go@v$VERSION"
export GOPROXY="file://$PROXY_DIR,https://proxy.golang.org,direct"
export GONOSUMDB="github.com/t-0-network"
go mod tidy
go build ./...
```

- **`sumtool`** ([`.github/tools/sumtool`](../.github/tools/sumtool)) packs the working tree's `go/` directory into a `file://` GOPROXY layout at the future version, using `x/mod/zip.CreateFromDir` — the same code path `cmd/go` uses. `go mod tidy` then resolves the pin against that layout and records the real checksums, byte-identical to what `proxy.golang.org` serves once `publish-go` pushes `go/vX.Y.Z`.
- **`tidy`, not two hand-appended lines**, so a new transitive dependency introduced by the SDK is picked up too.
- **`GONOSUMDB` is scoped to `github.com/t-0-network`.** Every other module still goes through the public proxy and the checksum database.
- **`sumtool` refuses to run if `go/LICENSE` is missing.** `cmd/go` synthesizes the repo-root `LICENSE` into a module zip when the module directory has none; `x/mod/zip` does not. Without that guard the locally computed hash would silently diverge from the proxy's.
- **The `go build ./...` is the in-job sanity check**, and because the whole step sits *above* "Commit version bump", a bad hash or a template that won't build against the new SDK fails the release before anything is committed or tagged. **Keep it in that position.** `publish-go` re-verifies with `go build -mod=readonly ./...` against the real proxy after the tags exist — readonly so a missing `go.sum` line fails the same way a wrong hash does.

Two things not to do here:

- **Don't tidy under a local `replace`.** Go records no `go.sum` entries for replaced modules, so tidy *strips* the SDK's lines instead of adding them. That was the previous approach; it left master unbuildable after 1.1.16, 1.1.21, 1.1.25 and 1.1.26, and shipped template zips with zero SDK checksums.
- **Don't mutate `go/` after this step.** The tree `sumtool` hashes must equal the future tag's `go/` subtree. True today — the release runs on a clean checkout and every edit is scripted — but a later step that touched `go/` would diverge silently, detectable only by `publish-go`'s post-tag build.

---

## publish.yaml — tag-driven publish

Triggered by a push of any tag matching `v[0-9]+.[0-9]+.[0-9]+`. Layout:

```
build-go   build-node   build-java   build-python   build-csharp
   \           |            |             |              /
    \----------+----+-------+-------------+-------------/
                    |
        ┌───────────┼─────────────┬─────────────┬───────────────┐
        │           │             │             │               │
   publish-go   publish-node-*  publish-py-*  publish-java   publish-csharp
                                                                 │
                                                          verify-jitpack
```

### Build gate

All five `build-*` jobs must pass before any publish job runs. Same compilation as in `release.yaml` — extra defence in depth in case a tagged commit somehow drifted.

### Per-publish-job version validation

Each `publish-*` job's first real step (after `Setup`) is a "Verify SDK version matches tag" assertion that compares the **package-level version** and the **runtime constant** (where applicable) against the git tag. Pseudocode:

```bash
VERSION="${GITHUB_REF#refs/tags/v}"
RUNTIME=$(grep '...' <runtime-constant-file>)
PKG=$(grep '...' <package-version-file>)
[ "$RUNTIME" = "$VERSION" ] || { echo "::error::runtime $RUNTIME != tag v$VERSION"; exit 1; }
[ "$PKG" = "$VERSION" ]     || { echo "::error::pkg $PKG != tag v$VERSION"; exit 1; }
```

Inlined per-job (rather than as a single shared `validate-versions` job) so each job is self-contained, the failing assertion shows up next to the publish step it gates, and the workflow's `needs:` lists stay short.

### What each publish job does

| Job | Runner | Validates | Then |
|---|---|---|---|
| `publish-go` | blacksmith | `go/sdkversion/version.go` matches tag | Creates + pushes the three Go module tags (`go/vX.Y.Z`, `go/starter/vX.Y.Z`, `go/starter/template/vX.Y.Z`), then `go list -m` against `proxy.golang.org` to warm the module proxy. Tags are created here (not in `release.yaml`) so they exist before the proxy is first queried — otherwise the proxy negative-caches a 404. Finally `go build -mod=readonly ./...` in `go/starter/template` against the default proxy, verifying that the `go.sum` entry `release.yaml` precomputed matches the module the proxy now serves. `-mod=readonly` fails on a missing line as well as a wrong hash. This job pushes tags, never commits. No artifact upload — Go modules are served from the git tag itself. |
| `publish-node-sdk` | **`ubuntu-latest`** (npm provenance requires GitHub-hosted) | `node/sdk/src/version.ts` + `node/sdk/package.json` match tag | `npm publish --provenance --access public`. |
| `publish-node-starter` | **`ubuntu-latest`** | `node/starter/package.json` matches tag | `npm publish --provenance --access public`. |
| `publish-python-sdk` | blacksmith, env `pypi-sdk` | `_version.py` + `pyproject.toml` match tag | `uv build --package t0-provider-sdk` then `uv publish --trusted-publishing always`. |
| `publish-python-starter` | blacksmith, env `pypi-starter` | starter `pyproject.toml` matches tag | `uv build --package t0-provider-starter`, `uv publish`. |
| `publish-java` | blacksmith (2vcpu — most time is Maven Central polling) | `META-INF/sdk-version.properties` + `gradle.properties` match tag | `./gradlew publishAggregationToCentralPortal`, then upload `provider-init.jar` to the GitHub Release. |
| `publish-csharp` | blacksmith, env `nuget` | both csproj `<Version>`s match tag | `dotnet pack` for sdk and starter; `dotnet nuget push` to `nuget.org`. Auth via `NuGet/login@v1` OIDC → temporary key (no long-lived token). |

### Post-publish verification

`verify-jitpack` polls `jitpack.io` for the Java artifact for up to ~10 minutes. JitPack builds on demand, so this confirms the published Maven Central artifact is also reachable by Gradle users who pick the JitPack repository in `build.gradle.kts`.

---

## Why version validation exists in two places

The release workflow's "Validate updated files" step ensures the bump itself is internally consistent (all 14+ sites agree on the calculated version). The publish workflow's per-job validation ensures the **tagged commit** still has matching versions when the publish job runs — protection against:

- A `vX.Y.Z` tag manually pushed against a commit where the bump wasn't completed.
- A revert that left tags behind.
- Drift between `release.yaml`'s knowledge of version sites and a new site someone added without updating both workflows.

Both gates are necessary and inexpensive (each is a few greps).

---

## Operating notes

- **Never trigger `publish.yaml` manually.** It will refuse to publish if the tag doesn't match the runtime constants, but it will also try to actually publish to npm / PyPI / Maven Central / NuGet on success — there's no "dry run" mode.
- **Never `git tag vX.Y.Z` by hand.** `release.yaml` pushes the root `vX.Y.Z` tag (which triggers publish); `publish.yaml`'s `publish-go` job then creates the three `go/*` module tags. Don't create any of them manually.
- **Re-running a failed publish job:** safe for idempotent steps (Go module proxy warm-up, JitPack verify). For npm/PyPI/Maven Central/NuGet, the registry rejects duplicate version uploads, so a re-run after a successful publish will fail loudly — that's the intended behaviour. If a publish job partially failed, fix the cause and ask the user before re-running.
- **`provider-init.jar` upload:** `publish-java` writes the file to the existing GitHub Release with `--clobber`. The Release was created earlier by `release.yaml`.
