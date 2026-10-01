# Dependency updates

Dependabot opens the update pull requests; [`.github/dependabot.yml`](../.github/dependabot.yml) decides which ones. This document says how to handle them.

The five SDKs sign and verify the same bytes, checked against `cross_test/test_vectors.json`. A regression in a hash, curve, signer or framing library therefore shows up in the bytes, in every language. Most updates only need CI. Updates to libraries on the signing path need an audit first.

## How Dependabot groups updates

- **One weekly batch.** Every dependency that CI can validate goes into one pull request across all ecosystems, `ci-batch`, opened on Mondays. The config lists these dependencies per ecosystem as allowlist `patterns`, because Dependabot has no exclude patterns for groups that span ecosystems.
- **Signing-path libraries get their own pull requests.** They are deliberately left out of every allowlist, so Dependabot opens one pull request per library. The list is in the header of `.github/dependabot.yml`. Never add one of them to a pattern.
- **New dependencies start alone.** A dependency that is in no allowlist also gets its own pull request. Decide whether it is on the signing path (see below). If it is not, add it to its ecosystem's `patterns` so later updates join the batch.
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
  - Node: `node/sdk/src/client/signer.ts`, `node/sdk/src/service/service.ts` and `node/sdk/src/common/client/`;
  - Python: `python/sdk/src/t0_provider_sdk/crypto/`;
  - Java: `java/sdk/src/main/java/network/t0/sdk/crypto/`;
  - C#: `csharp/sdk/T0.ProviderSdk/Crypto/`.
- **It builds the bytes a client signs.** The Node client signs the unary body and the first stream envelope that `@connectrpc/connect` builds, through its `@private` `CommonTransportOptions`. So:
  - `@connectrpc/connect` and `@connectrpc/connect-node` are pinned to one exact version in `node/sdk/package.json`.
  - They are updated together in one pull request, because `connect-node` depends on the exact version of `connect`.
  - Besides the vector tests, run `node/sdk/test/streaming.test.ts`, `node/sdk/test/unary_wire.test.ts` and `node/sdk/test/cross_stream.test.ts`.
- **It is used by `cross_test/`,** or by the vector tests listed in step 6 below.
- **It describes itself as crypto,** hash, signature, curve, KDF, MAC or random number generation.

## Updating a signing-path dependency

Update one library per pull request. The exception is a coupled pair: `@noble/curves` with `@noble/hashes`, or the two `@connectrpc/connect` packages. PR #99 is the reference.

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
7. **Open a pull request** titled `chore(deps): bump <pkg> to <ver>`. Its description has these sections, in order:
   - Summary;
   - Supersedes (`#N` of the Dependabot pull request);
   - Pre-bump call-site audit (the table from step 1);
   - Test additions (step 3);
   - Crypto safety attestation (the vector tests unchanged);
   - Test plan, covering both versions.

## Commands

The build and test commands for each area are in [CONTRIBUTING.md](../CONTRIBUTING.md#build-and-test).

| Ecosystem | Manifests | Installed version | Update one dependency |
| --- | --- | --- | --- |
| Go | `go/go.mod`, `go/starter/template/go.mod`, `cli/go.mod` | `go list -m <pkg>` | edit `go.mod`, then `go mod tidy` |
| Node | `node/sdk/package.json`, `node/starter/template/package.json` | `npm ls <pkg>` | `npm install <pkg>@x.y.z` |
| Python | `python/pyproject.toml`, `python/sdk/pyproject.toml`, `python/starter/template/pyproject.toml` | `uv pip show <pkg>` or `uv tree` | edit `pyproject.toml`, then `uv sync --all-packages` |
| Java | `java/sdk/build.gradle.kts`, `java/starter/template/build.gradle.kts` | `./gradlew :sdk:dependencyInsight --dependency <pkg>` | edit `build.gradle.kts`, then `./gradlew build` |
| C# | `csharp/sdk/T0.ProviderSdk/T0.ProviderSdk.csproj`, `csharp/starter/template/my-provider.csproj` | `dotnet list package` | `dotnet add package <pkg> -v x.y.z` |

## Never

- Release from a dependency pull request. Releases are a separate step; see [RELEASE_AND_PUBLISH.md](RELEASE_AND_PUBLISH.md).
- Update a signing-path library without the cross-language vector tests.
- Update a dependency without reading its changelog. Use the release notes or `CHANGELOG.md`, not only the registry's metadata.
- Force-merge or bypass branch protection.
