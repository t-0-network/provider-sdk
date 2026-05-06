# SystemService

`tzero.v1.system.SystemService` is auto-registered by every provider SDK on the customer's gRPC server. Customers do not implement it; bumping the SDK dependency is the only action required to expose it.

This document is split into a **User Guide** (provider integrators) and a **Maintainer Reference** (SDK contributors).

---

## User Guide

### What is SystemService?

A built-in service every provider exposes alongside the customer's own services. It currently has one RPC: `Health()`. Future operational RPCs (e.g. `Diagnostics`, `BuildInfo`) will be added to **the same service** so they appear automatically on the next SDK bump — no customer code change.

### Health RPC — what it returns

Defined in [`proto/tzero/v1/system/system.proto`](../proto/tzero/v1/system/system.proto). Response fields:

| Field | Type | Example | Purpose |
|---|---|---|---|
| `services` | `repeated string` | `["tzero.v1.payment.ProviderService", "tzero.v1.system.SystemService"]` | Fully-qualified protobuf service names registered on this server. Always includes `SystemService` itself. |
| `current_time` | `google.protobuf.Timestamp` | `2026-05-06T15:30:45.123456Z` | Server wall-clock time. Useful for detecting clock skew (signature verification breaks at ±60 s drift). |
| `sdk_version` | `string` | `"1.1.15"` | Semver of the SDK build serving this request. |
| `sdk_ecosystem` | `SdkEcosystem` (enum) | `SDK_ECOSYSTEM_GO` / `_NODE` / `_PYTHON` / `_JAVA` / `_CSHARP` | Which SDK runtime built this provider. |

`Health` is annotated `idempotency_level = NO_SIDE_EFFECTS` — safe to retry, may be cached.

### How callers reach it

- **Path:** `/tzero.v1.system.SystemService/Health`
- **Auth:** Same signature requirement as every other provider RPC — `X-Public-Key`, `X-Signature`, `X-Signature-Timestamp` headers. The signing keypair is the **T-0 Network's** keypair, not the provider's. So in practice only the network calls `Health` on a provider; provider engineers normally don't.
- **Not suitable as a Kubernetes liveness probe** — the probe would need the network's private key. Expose a separate unsigned HTTP `/health` if you need a probe. A TCP connectivity check on the gRPC port is also fine.

### Reverse proxy / ingress

If your ingress has a service-name allowlist, allow `/tzero.v1.system.SystemService/` (or all `/tzero.v1.*`). TLS, rate-limit, and routing are otherwise identical to your other RPCs.

### Upgrading: what changes

Bumping the SDK version exposes Health automatically. Your `main` file (`cmd/main.go` / `src/index.ts` / `src/provider/main.py` / `Main.java`) does **not** need any edit — the auto-registration happens inside the SDK's server-construction wrapper that you already call.

When future RPCs land on `SystemService`, the same property holds: a fresh SDK version exposes them automatically on your next deploy.

### Per-ecosystem upgrade UX

| Ecosystem | Dependency line | Operation to pull new version |
|---|---|---|
| Go | `go/starter/template/go.mod` line 9: `github.com/t-0-network/provider-sdk/go vX.Y.Z` (exact) | `go get -u github.com/t-0-network/provider-sdk/go && go mod tidy` |
| Node | `node/starter/template/package.json`: `"@t-0/provider-sdk": "^X.Y.Z"` (caret) | `npm install` (lockfile bump within same major) |
| Python | starter `pyproject.toml.template`: `"t0-provider-sdk>=0.1.0"` (floor) | `uv sync` (always picks latest) |
| Java | `java/starter/template/build.gradle.kts`: `provider-sdk:+` (latest) | `./gradlew build --refresh-dependencies` |
| C# | `csharp/starter/.../TemplateFiles.cs`: pinned per-CLI release | redownload starter CLI |

Full version-management details: [`VERSIONING.md`](./VERSIONING.md).

---

## Maintainer Reference

### Where the auto-registration happens

Each SDK has a wrapper around server construction that the starter calls. Inside that wrapper, after the customer's services are registered, we append `SystemService` with the same interceptor stack (signature verification + response validation). Customers' source code never names `SystemService`.

| Language | Wrapper | File |
|---|---|---|
| Go | `provider.NewHttpHandler` | [`go/provider/handler.go`](../go/provider/handler.go) |
| Node | `createService` | [`node/sdk/src/service/service.ts`](../node/sdk/src/service/service.ts) |
| Python | `new_asgi_app`, `new_wsgi_app` | [`python/sdk/src/t0_provider_sdk/provider/handler.py`](../python/sdk/src/t0_provider_sdk/provider/handler.py) |
| Java | `ProviderServer.Builder.buildGrpcServer` | [`java/sdk/src/main/java/network/t0/sdk/provider/ProviderServer.java`](../java/sdk/src/main/java/network/t0/sdk/provider/ProviderServer.java) |
| C# | *(not implemented yet — TODO at C# parity)* | — |

The customer-facing FQN list returned by `Health.services` is collected at registration time:

- **Go:** path strings from each `BuildHandler` are collected into `registered`, FQN = `strings.Trim(path, "/")`.
- **Node:** the user's `Router.service(desc, impl)` call is wrapped to capture `desc.typeName`.
- **Python:** `routes.keys()` are stripped of leading `/` to recover FQNs.
- **Java:** `BindableService.bindService().getServiceDescriptor().getName()` returns the FQN directly.

Implementations live next to the wrapper:
- Go: [`go/provider/system.go`](../go/provider/system.go)
- Node: [`node/sdk/src/service/system.ts`](../node/sdk/src/service/system.ts)
- Python: [`python/sdk/src/t0_provider_sdk/provider/system.py`](../python/sdk/src/t0_provider_sdk/provider/system.py)
- Java: [`java/sdk/src/main/java/network/t0/sdk/provider/SystemServiceImpl.java`](../java/sdk/src/main/java/network/t0/sdk/provider/SystemServiceImpl.java)

### Adding a new RPC to SystemService

1. **Edit the proto** [`proto/tzero/v1/system/system.proto`](../proto/tzero/v1/system/system.proto) — add the RPC and its request/response messages.
2. **Regenerate code:** from repo root, `buf generate`.
3. **Implement in each SDK's `system.*` file** (signatures will be required by the generated stubs):
   - Go: add a method on `*systemServiceImpl`.
   - Node: add a method on the object returned by `createSystemServiceImpl`.
   - Python: add a method on both `SystemServiceImpl` (async, ASGI) and `SystemServiceImplSync` (WSGI).
   - Java: override the method on `SystemServiceImpl extends SystemServiceGrpc.SystemServiceImplBase`.
4. **Don't change any wrapper code** — the auto-registration loop already covers all RPCs of `SystemService`.
5. **No customer code change** — the new RPC appears on every provider after they bump the SDK.

### Adding a new SDK ecosystem (e.g. Rust)

1. Add an enum entry in `system.proto`: `SDK_ECOSYSTEM_RUST = N;`. Regenerate.
2. Pick a runtime version constant location (per [VERSIONING.md](./VERSIONING.md) conventions).
3. Implement `SystemService` in the new SDK's server-construction wrapper — same shape as the four existing languages.
4. Add a `publish-rust` job in [`publish.yaml`](../.github/workflows/publish.yaml) with an inline tag-vs-version assertion (mirror the existing `publish-go` job).
5. Add a "Bump runtime version constant" line in [`release.yaml`](../.github/workflows/release.yaml)'s "Update SDK runtime version constants" step plus a validation entry in the "Validate updated files" block.

### Per-language gotchas

**Java — `services.isEmpty()` validation is preserved.** `Builder.build()` rejects construction when no customer service was added via `withService(...)`. SystemService is appended **inside** `buildGrpcServer()`, never into `Builder.services`, so the check still catches "you forgot to add ProviderService".

**Java — runtime version is a classpath resource, not a constant.** `META-INF/sdk-version.properties` ships in the jar; `SystemServiceImpl.loadSdkVersion()` reads it via `getResourceAsStream`. This works under jar shading, but consumers who repackage with relocation rules need to keep `META-INF/` intact.

**Node — public API contract.** The wrapped `Router` cast in `service.ts` is small (one method); if the underlying `ConnectRouter.service` signature changes, update the wrapper or starters break.

**C# — not yet wired.** Generated stubs exist at [`csharp/sdk/T0.ProviderSdk/Api/Tzero/V1/System/`](../csharp/sdk/T0.ProviderSdk/Api/Tzero/V1/System/) but no equivalent of `SystemServiceImpl` or auto-registration is in place. Tracked for parity work when the C# SDK is published.

### Future endpoints (design intent)

`SystemService` is the chosen home for *operational* RPCs: introspection, diagnostics, build/runtime metadata. Examples that fit:

- `Diagnostics()` — recent error counts, last-error timestamps, connection state to upstream.
- `BuildInfo()` — git commit, build time, container image digest.
- `Watch()` — gRPC streaming health updates (only if a customer requests this).

Examples that do **not** fit and should be separate services:

- Anything customer-implementable (defeats the auto-registration premise).
- Mutations or anything with side effects.
- Domain-specific business RPCs (those have their own `tzero.v1.X.YService`).

### Tests covering Health

- Go: [`go/provider/system_test.go`](../go/provider/system_test.go) — three tests: response shape, fully-signed end-to-end through `network.NewServiceClient`, and unsigned-request rejection (proves signature middleware applies to Health).
- Node, Python, Java: existing test suites build green; **no Health-specific tests yet** — see "Known gaps" below.

### Cross-language testing

[`cross_test/`](../cross_test/) is intentionally **not** used for Health. Its purpose is to lock down crypto + wire-format invariants (Keccak256, secp256k1 signing/verification) shared across languages — see [`cross_test/test_vectors.json`](../cross_test/test_vectors.json). Health is an ordinary RPC layered on those primitives; per-ecosystem unit + integration tests cover it without cross-vector duplication.

### Known gaps

- No Python/Node/Java Health-specific tests yet (see "Tests covering Health" above).
