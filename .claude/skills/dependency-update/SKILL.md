---
name: dependency-update
description: Use when triaging Dependabot PRs, bumping a library version in any ecosystem (Go/Node/Python/Java/C#), or asking "is this dep update safe to merge". Classifies each bump into dev-only / behavior-jump / crypto-or-security-path tiers, runs the audit + test additions + verification flow that protects the SDK signing path, and produces the right PR shape for each tier.
---

# Dependency update workflow

This monorepo ships five SDKs that all sign or verify the same wire bytes against the shared `cross_test/test_vectors.json`. A regression in a hash, curve, signer, or framing dep is byte-visible across every language. This skill captures how to tell a routine bump from one that needs guarding before merging.

## When this skill applies

Triggers: Dependabot PRs, "bump `<pkg>` to `<ver>`", "update dependency", "is this dep update safe to merge", "test the dep update locally". Out of scope: choosing a new dep for a greenfield component, or feature work that incidentally touches a dep.

## Three-tier decision

Classify the bump first, then act. The taxonomy mirrors the precedent in PR #98 (Tier 1 batch) and PR #99 (Tier 3 noble libs).

### Tier 1 — dev-only / safe routine

Examples: test runners (`pytest`, `node:test`, JUnit), type stubs (`@types/*`), linters/formatters (when the changelog has no rule changes — otherwise Tier 2), build wrappers (gradle wrapper, typescript compiler, tsx), doc tooling. Anything declared under `devDependencies` / `dev-dependencies` / `[dependency-groups].dev` / `testImplementation(...)` / `<PackageReference>` test-only blocks. Patch and minor bumps to *runtime* deps that are not on the signing path also land here.

**Action:** no new tests required. Run the existing suite for the affected ecosystem; if it passes, batch with the other Tier 1 bumps into one PR titled `chore(deps): batch safe dependabot updates`, body listing every `Supersedes #N`. PR #98 is the reference shape.

### Tier 2 — behavior or strictness jump

Examples seen on this repo: `ruff` major bump (new lint rules), `mypy` major (defaults flipped), `uvicorn` (request handling behavior), `io.grpc` (Android DNS, baggage), `buf.build/go/protovalidate` (constraint semantics). Any changelog entry that mentions defaults flipping, deprecations, removed APIs, stricter type/lint rules, or runtime behavior changes.

**Action:** do not bump in this skill's flow. Surface a comment on the Dependabot PR with the changelog excerpt and the specific risk so a human can decide whether to take the change deliberately. No code change.

### Tier 3 — crypto / security path

Identification is principle-based, not a hard-coded list — a future crypto dep must not silently slip into Tier 1. A dep is Tier 3 if **any** of these hold:

- It is imported (directly or transitively reachable) by code that produces or verifies the request signature, computes the request digest, derives keys, or validates the signature timestamp. Trace from the per-language signer/verifier entry points: `go/crypto/`, `node/sdk/src/client/signer.ts` + `node/sdk/src/service/service.ts`, `python/sdk/src/.../crypto/`, `java/sdk/src/main/java/.../crypto/`, `csharp/sdk/T0.ProviderSdk/Crypto/`.
- It is consumed by tests under `cross_test/` or by the per-language consumers of `cross_test/test_vectors.json` listed in the reference table below.
- The package self-describes as crypto, hash, signature, curve, kdf, mac, or rng (e.g. anything in `@noble/*`, `org.bouncycastle:*`, `golang.org/x/crypto`, `coincurve`, `BouncyCastle.Cryptography`, `secp256k1`-named libs, etc. — these are illustrative, not exhaustive).

When unsure, treat as Tier 3.

**Action:** the seven-step workflow below. One Tier 3 dep per PR (or a tightly-coupled pair like `@noble/curves` + `@noble/hashes`). PR #99 is the reference shape.

## Tier 3 workflow

1. **List call sites.** Grep for every import and symbol the dep exposes that the SDK actually uses. Produce a table — `function | call site | direct test? (yes / indirect-only / no)`. PR #99's "Pre-bump call-site audit" body section is the format.

2. **Run the existing test suite on the *current* version** (don't bump yet) to establish the baseline. Record the test count and which suites cover the dep, so step 6 can compare apples to apples.

3. **For each used function with no direct test, add one.** "Direct" means: exercises the function with concrete inputs and asserts the return shape or value. "The end-to-end flow happens to pass" is **indirect** and does not count — a verifier that always returns `false` would still pass the end-to-end "rejects unsigned request" path. Pin the new tests against `cross_test/test_vectors.json` where possible so cross-language drift is caught.

4. **Re-run all tests on the *current* version** with the new tests added. They must pass. This proves the new tests encode the existing contract, not a future hope.

5. **Bump the dep**, regenerate the lockfile, clean rebuild. See the per-ecosystem table for exact commands.

6. **Re-run all tests, including the new ones.** The cross-language signature vector in `cross_test/test_vectors.json` must verify byte-identically — this is the canonical proof that signing output is unchanged.

7. **Open a dedicated PR.** Title: `chore(deps): bump <pkg> to <ver>`. Body sections, in order: Summary (one sentence + delta), Supersedes (`#N` of the Dependabot PR), Pre-bump call-site audit (the table from step 1), Test additions (the list from step 3), Crypto safety attestation (cross-language vector still byte-identical), Test plan checklist with both versions covered. PR #99 is the reference shape.

## Per-ecosystem reference

Manifests, build/test commands, version-inspection commands, single-dep bump commands. Sourced from the ecosystem CLAUDE.md files at `go/CLAUDE.md`, `node/CLAUDE.md`, `python/CLAUDE.md`, `java/CLAUDE.md`, `csharp/CLAUDE.md`.

| Ecosystem | Manifest(s) | Install + build + test | Inspect installed version | Bump single dep |
| --- | --- | --- | --- | --- |
| Go | `go/go.mod`, `go/starter/go.mod`, `go/starter/template/go.mod` | `cd go && go test ./...` | `go list -m <pkg>` | edit `go.mod` → `go mod tidy` |
| Node | `node/sdk/package.json`, `node/starter/package.json` | `cd node/sdk && npm ci && npm run build && npm test` | `npm ls <pkg>` | `npm install <pkg>@x.y.z` |
| Python | `python/pyproject.toml` (workspace), `python/sdk/pyproject.toml` | `cd python && uv sync --all-packages && uv run pytest -v` | `uv pip show <pkg>` or `uv tree` | edit `pyproject.toml` → `uv sync --all-packages` |
| Java | `java/sdk/build.gradle.kts`, `java/cli/build.gradle.kts`, `java/starter/build.gradle.kts` | `cd java && ./gradlew build` | `./gradlew :sdk:dependencyInsight --dependency <pkg>` | edit `build.gradle.kts` → `./gradlew build` |
| C# | `csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj` and sibling projects | `cd csharp && dotnet build && dotnet test` | `dotnet list package` (per project) | `dotnet add package <pkg> -v x.y.z` |

Cross-language test vector consumers (re-run these in Tier 3 step 6 to confirm byte-identical signature output across the bump):

- Go: `go/crypto/cross_test.go`
- Node: `node/sdk/test/crypto.test.ts`
- Python: `python/sdk/tests/crypto/test_cross_vectors.py`
- Java: `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java`
- C#: `csharp/sdk/T0.ProviderSdk.Tests/Crypto/CrossTestVectors.cs`

## What this skill explicitly does *not* do

- Trigger releases. Releases run from `master` via `release.yaml` only; that is a separate, user-initiated action.
- Bypass `cross_test/` cross-language verification when bumping a Tier 3 dep.
- Bump a dep without first reading its changelog (release notes from the GitHub release or `CHANGELOG.md`, not just the npm/PyPI/etc. metadata).
- Comment on or merge closed/locked PRs.
- Force-merge anything, or override branch protection.
