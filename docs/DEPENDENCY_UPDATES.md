# Dependency updates

Dependabot opens the update pull requests; [`.github/dependabot.yml`](../.github/dependabot.yml) decides which ones. This document says how to handle them.

The five SDKs sign and verify the same bytes, checked against `cross_test/test_vectors.json`. A regression in a hash, curve, signer or framing library therefore shows up in the bytes, in every language. Most updates only need CI. Updates to libraries on the signing path need an audit first.

## How Dependabot groups updates

- **One weekly batch.** Every dependency, in every ecosystem, goes into one pull request, `ci-batch`, opened on Mondays. Each ecosystem uses `patterns: ["*"]`. In a group that spans ecosystems, Dependabot updates only the dependencies that match the group's patterns and opens no pull request for the others (`run_ungrouped_dependency_updates unless job.multi_ecosystem_update?` in dependabot-core). An allowlist would silently drop every dependency it does not name, and until November 2026 it did: the signing-path libraries and new dependencies got no pull request at all.
- **Not watched, updated by hand:** `.github/tools/sumtool` (every pull request Dependabot built for it was refused, HTTP 400), `cross_test/go_helper` (a Go workspace; its versions follow `go/go.mod`), and the Python starter's floors, which are customer-facing ([`python/CLAUDE.md`](../python/CLAUDE.md)). The C# and Java starters are listed as directories of their own, because `csharp.slnx` and the Java build do not cover them (the Java starter's Gradle wrapper).
- **Signing-path libraries arrive in the batch.** The list is in the header of `.github/dependabot.yml`. When the batch moves one, do its audit (below) in the batch pull request. To update it on its own instead, hold it back with `@dependabot ignore <dependency-name> minor version` (or `patch version`) on the batch, and follow the audit in a pull request of its own.
- **Release-owned pins are ignored.** The starter templates' pins of the SDK itself are ignored, because `release.yaml` updates them.
- **Updates wait three days** after a release (`cooldown`).

## The weekly batch

CI is the gate. Read the changelog of each update in the batch, and look out for:
- defaults that change;
- deprecations and removed APIs;
- stricter lint or type rules;
- changes in runtime behaviour.

Examples are a major release of `ruff` or `mypy`, `uvicorn` (request handling), `io.grpc`, and protovalidate (constraint semantics). If CI passes and nothing looks risky, merge the batch.

To hold one update back, comment `@dependabot ignore <dependency-name> major version` (or `minor version`) on the batch pull request. Dependabot then rebuilds the batch without it. Name the dependency instead of writing `this`, because the batch is a grouped pull request.

## Is a dependency on the signing path?

Treat a dependency as being on the signing path if any of these is true, and also when you are unsure.

- **It is reachable from the code that signs or verifies requests.** That covers computing the digest, deriving keys and checking the signature timestamp. Trace from:
  - Go: `go/crypto/` and `cli/keygen.go`;
  - Node: `node/sdk/src/client/signer.ts`, `node/sdk/src/common/service.ts` and `node/sdk/src/common/client/`;
  - Python: `python/sdk/src/t0_provider_sdk/crypto/`;
  - Java: `java/sdk/src/main/java/network/t0/sdk/crypto/`;
  - C#: `csharp/sdk/T0.ProviderSdk/Crypto/`.
- **It builds the bytes a client signs.** The Node client signs the unary body and the first stream envelope that `@connectrpc/connect` builds, through its `@private` `CommonTransportOptions`. So:
  - `@connectrpc/connect` and `@connectrpc/connect-node` are pinned to one exact version in `node/sdk/package.json`.
  - They are updated together in one pull request, because `connect-node` depends on the exact version of `connect`.
  - Besides the vector tests, run `node/sdk/test/streaming.test.ts`, `node/sdk/test/unary_wire.test.ts` and `node/sdk/test/cross_stream.test.ts`.
  - The Go client signs the body and the first stream envelope that `connectrpc.com/connect` builds, so that module is on the signing path too. Besides the vector tests, run `go test ./network/...` and the cross tests against `go_helper`. `golang.org/x/net` is handled the same way, because an update of it also moves `golang.org/x/crypto`.
- **It is used by `cross_test/`,** or by the vector tests listed in step 6 below.
- **It describes itself as crypto,** hash, signature, curve, KDF, MAC or random number generation.

## Updating a signing-path dependency

Audit every signing-path library an update moves, whether it arrives in the weekly batch or in a pull request of its own. Coupled pairs move together: `@noble/curves` with `@noble/hashes`, the two `@connectrpc/connect` packages, and `golang.org/x/net` with `golang.org/x/crypto`. PR #99 is the reference.

1. **List the call sites.** Find every function of the library that the SDK uses, as a table: function | call site | direct test? (yes / indirect only / no).
2. **Run the tests on the current version, before updating.** Note the test count and which suites cover the library.
3. **Add a direct test for each function that has none.**
   - A direct test calls the function with concrete inputs and checks the result.
   - A passing end-to-end flow is only indirect coverage. A verifier that always returns `false` would still pass a "rejects an unsigned request" test.
   - Pin the new tests to `cross_test/test_vectors.json` where you can.
4. **Run all tests on the current version, with the new tests.** They must pass, which shows that they describe the current behaviour.
5. **Update the library.** Update the lock file, and rebuild from clean.
6. **Run all tests again.** The vector tests must pass unchanged, which shows that signatures are still byte-identical. CI runs these tests on the pull request too:
   - Go: `go/crypto/cross_test.go`
   - Node: `node/sdk/test/crypto.test.ts`
   - Python: `python/sdk/tests/crypto/test_cross_vectors.py`
   - Java: `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java`
   - C#: `csharp/sdk/T0.ProviderSdk.Tests/Crypto/CrossTestVectors.cs`
7. **Write it up.** For a library updated on its own, open a pull request titled `chore(deps): bump <pkg> to <ver>`. For one in the weekly batch, add the same sections to the batch pull request's description or a comment on it. The sections, in order:
   - Summary;
   - Supersedes (`#N` of a Dependabot pull request it replaces, if any);
   - Pre-bump call-site audit (the table from step 1);
   - Test additions (step 3);
   - Crypto safety attestation (the vector tests unchanged);
   - Test plan, covering both versions.

## Commands

The build and test commands for each area are in [CONTRIBUTING.md](../CONTRIBUTING.md#build-and-test).

| Ecosystem | Manifests | Installed version | Update one dependency |
| --- | --- | --- | --- |
| Go | `go/go.mod`, `go/starter/template/go.mod`, `cli/go.mod`; Dependabot does not watch `.github/tools/sumtool/go.mod` or `cross_test/go_helper/go.mod` (its `go.work` also uses `../../go`), so update them by hand, `go_helper` to match `go/go.mod` | `go list -m <pkg>` | edit `go.mod`, then `go mod tidy` |
| Node | `node/sdk/package.json`, `node/starter/template/package.json` | `npm ls <pkg>` | `npm install <pkg>@x.y.z` |
| Python | `python/pyproject.toml`, `python/sdk/pyproject.toml`, `python/starter/template/pyproject.toml` | `uv pip show <pkg>` or `uv tree` | edit `pyproject.toml`, then `uv sync --all-packages` |
| Java | `java/sdk/build.gradle.kts`, `java/starter/template/build.gradle.kts` | `./gradlew :sdk:dependencyInsight --dependency <pkg>` | edit `build.gradle.kts`, then `./gradlew build` |
| C# | `csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj`, `csharp/starter/template/my-provider.csproj` | `dotnet list package` | `dotnet add package <pkg> -v x.y.z` |

## Never

- Release from a dependency pull request. Releases are a separate step; see [RELEASE_AND_PUBLISH.md](RELEASE_AND_PUBLISH.md).
- Update a signing-path library without the cross-language vector tests.
- Update a dependency without reading its changelog. Use the release notes or `CHANGELOG.md`, not only the registry's metadata.
- Force-merge or bypass branch protection.
