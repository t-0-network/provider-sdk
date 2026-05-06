# Versioning

All SDKs share a unified version managed via git tags `vX.Y.Z`. The same version appears in many places across the monorepo. This document is the **single source of truth for where the version lives** and the rules for keeping all sites in lockstep.

The release flow that bumps these sites and the publish flow that validates them is documented in [`RELEASE_AND_PUBLISH.md`](./RELEASE_AND_PUBLISH.md).

---

## Three categories of version sites

Each ecosystem has up to three places that hold a version. Knowing the category makes it clear who reads it and when it must be bumped.

### A) Package-level version

The version field that the package manager (npm / PyPI / Maven Central / NuGet / Go module proxy) consumes. This is what shows up in dependency listings.

### B) Starter-template SDK pin

The SDK dependency declared in the starter template that gets scaffolded into a new customer project. Determines the version a freshly scaffolded project starts with. Some ecosystems pin exactly, others use floating ranges (so customers always pick up the newest published SDK without a starter regeneration).

### C) Runtime version constant

A constant compiled or bundled into the SDK that is returned by `SystemService.Health.sdk_version`. Read at request time. Independent of package-level version because it must work under packaging modes that lose package metadata (Go binaries with `-trimpath`, jar shading, editable installs, bundlers that tree-shake `package.json`).

---

## Locations per ecosystem

| Ecosystem | (A) Package version | (B) Starter-template pin | (C) Runtime constant |
|---|---|---|---|
| **Go** | `go/go.mod` (module path; tagged via git, not edited at release time) | `go/starter/template/go.mod` line 9 — `github.com/t-0-network/provider-sdk/go vX.Y.Z` (exact, rewritten by release) | `go/sdkversion/version.go` — `const Version = "X.Y.Z"` |
| **Node SDK** | `node/sdk/package.json` — `"version"` | n/a (SDK isn't scaffolded) | `node/sdk/src/version.ts` — `export const SDK_VERSION = "X.Y.Z"` |
| **Node Starter** | `node/starter/package.json` — `"version"` | `node/starter/template/package.json` — `"@t-0/provider-sdk": "^X.Y.Z"` (caret, rewritten by release) | n/a |
| **Python SDK** | `python/sdk/pyproject.toml` — `version =` | n/a | `python/sdk/src/t0_provider_sdk/_version.py` — `__version__ = "X.Y.Z"` |
| **Python Starter** | `python/starter/pyproject.toml` — `version =` | `python/starter/src/t0_provider_starter/template/pyproject.toml.template` — `t0-provider-sdk>=0.1.0` (floor, **NOT bumped**) | n/a |
| **Java SDK** | `java/gradle.properties` — `version=X.Y.Z` | n/a (the `cli` module is the starter) | `java/sdk/src/main/resources/META-INF/sdk-version.properties` — `sdk.version=X.Y.Z` (classpath resource) |
| **Java CLI** | (uses same `gradle.properties`) | `java/starter/template/build.gradle.kts` — `provider-sdk:+` (latest, **NOT bumped**) | n/a |
| **C# SDK** | `csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj` — `<Version>` | n/a | n/a (not implemented yet) |
| **C# Starter** | `csharp/starter/T0.ProviderStarter/T0.ProviderStarter.csproj` — `<Version>` | `csharp/starter/T0.ProviderStarter/TemplateFiles.cs` — embedded string `T0.ProviderSdk" Version="X.Y.Z"` (rewritten by release) | n/a |

---

## Starter pinning strategies — why they differ

When a new SDK is published, customer projects scaffolded from the starter should pick it up. Different ecosystems handle this differently:

| Strategy | Used by | Behaviour | Implication for customers |
|---|---|---|---|
| **Exact pin, rewritten on release** | Go (`vX.Y.Z`), C# (embedded `Version="X.Y.Z"`) | The starter literally references one version. The release workflow rewrites the line. | A customer who scaffolded **before** the bump keeps the old version until they re-run the starter or manually edit `go.mod` / regenerate from the new CLI. |
| **Caret range, rewritten on release** | Node (`^X.Y.Z`) | Range allows minor/patch updates within the same major; release rewrites the floor. | `npm install` after a minor bump pulls the new version automatically. Re-scaffolding is unnecessary for minor/patch. |
| **Floor only, NOT rewritten** | Python (`>=0.1.0`) | Lower bound only. `uv sync` / `pip install` always resolves to the newest published version. | Customers always get the latest by default. The starter never has to be re-released to bump SDK pins. |
| **Floating latest, NOT rewritten** | Java (`+`) | Gradle resolves to the newest published artifact every build. | Same as Python — but reproducibility relies on a lock file or pinning manually in their own `build.gradle.kts` if they want determinism. |

Trade-off: exact-pin gives reproducible scaffolds at the cost of stale starters; floor/floating gives auto-upgrade at the cost of non-deterministic generation. We don't try to unify the strategies — we follow the convention of each ecosystem.

Customer-facing UX details (commands, what to run after a bump): see [`SYSTEM_SERVICE.md` § Per-ecosystem upgrade UX](./SYSTEM_SERVICE.md#per-ecosystem-upgrade-ux).

---

## Why a separate runtime constant?

`SystemService.Health.sdk_version` must report a version even when:

- The Go binary was built with `-trimpath` (no module path / version metadata available).
- A jar was shaded into a customer's fat jar, losing the `META-INF/MANIFEST.MF` Maven metadata.
- The Python wheel is installed in editable mode (`importlib.metadata.version` is unreliable across `uv` / `pip --user` / system / venv combinations).
- The Node bundle was tree-shaken to remove `package.json` reads.

A small runtime constant (one line of code or a properties file) is the most robust way to bake the version in. Maintaining it is cheap because the release workflow updates all four constants in lockstep with the package-level versions.

---

## The rule for adding a new version site

Whenever a new place needs to know the SDK version (a new ecosystem, a new consumer of the version, a new starter-template pin), update **all four** of the following:

1. The new file itself (write the version into it).
2. [`release.yaml`](../.github/workflows/release.yaml) — add a `sed` / equivalent in the "Update …" steps so the new file is rewritten at release time.
3. [`release.yaml`](../.github/workflows/release.yaml) — add a check in the "Validate updated files" step so a missed bump fails the release.
4. [`publish.yaml`](../.github/workflows/publish.yaml) — add a check in the relevant publish-* job's "Verify SDK version matches tag" step so a stale value blocks publish.

Skip any of those four and the next release will silently drift.

---

## Quick reference: how to check what version is in flight

```bash
# Package-level
grep '^version' python/sdk/pyproject.toml python/starter/pyproject.toml
grep '^version' java/gradle.properties
node -p "require('./node/sdk/package.json').version"
node -p "require('./node/starter/package.json').version"
grep '<Version>' csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj
grep '<Version>' csharp/starter/T0.ProviderStarter/T0.ProviderStarter.csproj

# Starter-template pins
grep 'provider-sdk' go/starter/template/go.mod
node -p "require('./node/starter/template/package.json').dependencies['@t-0/provider-sdk']"
grep 'T0.ProviderSdk' csharp/starter/T0.ProviderStarter/TemplateFiles.cs

# Runtime constants
grep 'const Version' go/sdkversion/version.go
grep 'SDK_VERSION' node/sdk/src/version.ts
grep '__version__' python/sdk/src/t0_provider_sdk/_version.py
grep 'sdk.version' java/sdk/src/main/resources/META-INF/sdk-version.properties
```

All of the above should match the current git tag during a release; mismatches are caught by the release validation step or the publish gate.
