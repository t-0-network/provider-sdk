# Health service

Every SDK mounts the standard [`grpc.health.v1.Health`](https://github.com/grpc/grpc/blob/master/src/proto/grpc/health/v1/health.proto) on the server it builds, behind the same signature-verification and response-validation stack as everything else. It is the only service the transport adds on its own. Customers never name it and never implement it; bumping the SDK is the whole of their involvement.

This document is maintainer-facing. Integrators do not touch this service — the T-0 Network calls it, they do not.

---

## Why health, and why it is the only thing mounted for you

The transport builds servers for more than one contract. A provider serves `tzero.v1.payment.ProviderService`; a pay participant serves the pay callbacks and is not a provider at all. Anything the transport mounts unconditionally therefore lands on servers that have no business hosting it — which is exactly what went wrong with the operational service that used to sit here.

`grpc.health.v1.Health` is the exception because it belongs to no business protocol, ours or the customer's. It is the one thing the transport can put on any server it builds without making a claim about what that server *is*.

There is no opt-out flag, and there should not be one. The Network needs a reachability preflight against every endpoint it will call; an endpoint that can turn the preflight off is an endpoint that cannot be registered.

---

## Nothing is generated. Every ecosystem takes this as a dependency

This is the point of the design, not an implementation detail: `health.proto` is not vendored, and no copy of it is generated into any SDK artifact. Each ecosystem consumes a published package, so nothing here puts a schema on a customer's classpath that they could conflict with.

| Ecosystem | Dependency | Registry |
|---|---|---|
| Go | `connectrpc.com/grpchealth` | Go module proxy — a hand-written library, so no generated code at all |
| Node | `@buf/grpc_grpc.bufbuild_es` | Buf Schema Registry npm (`@buf:registry` in `node/sdk/.npmrc`) |
| Python | `grpcio-health-checking` | PyPI |
| Java | `io.grpc:grpc-services` | Maven Central |
| C# | `Grpc.HealthCheck` | nuget.org |

**This is why the SDKs serve `Check` and not `List`.** `List` is a recent addition to the health protocol and none of these packages ship it — `Grpc.HealthCheck` has no `HealthList*` types at all, and the BSR Python build is pinned to a 2023 `health.proto`. Serving `List` would mean generating our own copy into every artifact, which is the thing this design exists to avoid. `Check` has been in `health.proto` since 2015 and answers the only question the probe asks.

**Python's package name is load-bearing.** `grpcio-health-checking` publishes under the top-level `grpc_health` package, not `grpc.health`. A generated `grpc.health.v1` would sit under a `grpc` *namespace* package, and grpcio ships a *regular* `grpc/__init__.py` that wins over it — so any customer venv containing grpcio (google-cloud-\*, any gRPC user) would fail to import the SDK at all. Do not "fix" this by generating into `grpc.health.v1`.

---

## Wire contract

### `Check`

| Request `service` | Response |
|---|---|
| a service the customer registered, e.g. `tzero.v1.payment.ProviderService` | `SERVING` |
| `grpc.health.v1.Health` itself | `SERVING` |
| `""` — the process as a whole | `SERVING` |
| anything else | `NOT_FOUND` |

The registered set is frozen when the server is constructed. Nothing about it is computed per request.

### SDK identity, in the response headers

```
t0-sdk-ecosystem: go | node | python | java | csharp
t0-sdk-version:   1.1.26
```

Set on the `Check` response and on nothing else. They are headers rather than fields because `HealthCheckResponse` has exactly one field and `Check` names its service in the *request* — the contract has nowhere to carry the identity of the SDK answering. Scoping them to this one handler is what makes headers acceptable: every callback the customer actually serves is untouched.

The version comes from the ecosystem's runtime version constant (see [`VERSIONING.md`](./VERSIONING.md)); the ecosystem token comes from the SDK doing the serving, so a server can only ever report the SDK it is actually running.

### Not implemented

**`Watch`** — server-streaming. The callback servers are unary-only (the Node one is HTTP/1.1), and the body-hash signature scheme has no stream story. Every ecosystem answers `UNIMPLEMENTED`, inherited from its package's base class rather than written by us.

---

## Auth

`Check` is signed with the **T-0 Network's** keypair, exactly like every other RPC on the endpoint. In practice that means only the Network calls it.

Two consequences worth knowing:

- **Not usable as a Kubernetes liveness probe** — the probe would need the Network's private key. Expose a separate unsigned HTTP endpoint if you need one, or use a TCP connectivity check on the port.
- **Ingress allowlists must include `/grpc.health.v1.Health/`.** An endpoint whose ingress drops that path cannot complete sandbox registration, because the registration probe is the only thing that calls it. TLS, rate-limiting and routing are otherwise identical to the customer's own RPCs.

---

## Per-language seams

The mount happens inside the server-construction wrapper the starter already calls, after the customer's services are registered, with the same interceptor stack:

| Language | Wrapper | Implementation | Registry it dumps |
|---|---|---|---|
| Go | `provider.NewHttpHandler` — [`go/provider/handler.go`](../go/provider/handler.go) | [`go/provider/health.go`](../go/provider/health.go) | `registered`, the path strings from each `BuildHandler`, trimmed of `/` |
| Node | `createService` — [`node/sdk/src/service/service.ts`](../node/sdk/src/service/service.ts) | [`node/sdk/src/service/health.ts`](../node/sdk/src/service/health.ts) | `collected`, captured by wrapping the customer's `Router.service(desc, impl)` |
| Python | `new_asgi_app` / `new_wsgi_app` — [`python/sdk/src/t0_provider_sdk/provider/handler.py`](../python/sdk/src/t0_provider_sdk/provider/handler.py) | [`python/sdk/src/t0_provider_sdk/provider/health.py`](../python/sdk/src/t0_provider_sdk/provider/health.py) | `routes.keys()`, stripped of the leading `/` |
| Java | `ProviderServer.Builder.buildGrpcServer` — [`ProviderServer.java`](../java/sdk/src/main/java/network/t0/sdk/provider/ProviderServer.java) | [`HealthServiceImpl.java`](../java/sdk/src/main/java/network/t0/sdk/provider/HealthServiceImpl.java) | `BindableService.bindService().getServiceDescriptor().getName()` |
| C# | `T0ProviderServer.RunAsync` — [`T0ProviderServer.cs`](../csharp/sdk/T0.ProviderSdk/T0ProviderServer.cs) | [`Provider/HealthServiceImpl.cs`](../csharp/sdk/T0.ProviderSdk/Provider/HealthServiceImpl.cs) | the FQN list each `Map*Service` call appends to |

How each ecosystem scopes the identity headers to this handler:

- **Go** — a `connect.HandlerOption` carrying one unary interceptor, passed only to `grpchealth.NewHandler`.
- **Node** — `ctx.responseHeader.set(...)` inside `check` itself.
- **Python** — `ctx.response_headers()[...]` inside `check` itself.
- **Java** — a `ServerInterceptor` applied only to the health `ServerServiceDefinition`.
- **C#** — `context.WriteResponseHeadersAsync(...)` inside the `Check` override.

**Java:** the health service is appended inside `buildGrpcServer()` and never into `Builder.services`, so `Builder.build()`'s "at least one service must be added with `withService()`" check still catches a customer who forgot to register their own.

**Node:** the published `.d.ts` in the BSR package loses its type brands through `ServiceImpl`'s generics, so connect-es widens the request to `Message<string>`. `health.ts` narrows it back with one cast; the descriptor is the real one at runtime.

**Python:** connect-python publishes no health bindings, so `health.py` assembles the ASGI/WSGI applications from `Endpoint` directly. Only the messages come from the package.

**C#:** the SDK version is read from `AssemblyInformationalVersionAttribute`, which MSBuild populates from `<Version>` in `T0.ProviderSdk.csproj`. Any `+gitsha` suffix is stripped. There is no second source of truth — the csproj `<Version>` is also the NuGet version.

---

## Adding a new SDK ecosystem

1. Pick a runtime version constant location, per [`VERSIONING.md`](./VERSIONING.md), and add it to `release.yaml`'s bump + validate steps.
2. Find that ecosystem's published `grpc.health.v1` package. Do not generate one.
3. Mount health in the new SDK's server-construction wrapper with the same interceptor stack, implement `Check`, and set the two identity headers on that response only.
4. Add a `publish-<ecosystem>` job in [`publish.yaml`](../.github/workflows/publish.yaml) with an inline tag-vs-version assertion.

---

## Tests

Each SDK covers the same three claims, end to end through its public server-construction wrapper:

1. Signed `Check` returns `SERVING` for a registered FQN, for health itself and for `""`, and `NOT_FOUND` for an unregistered name.
2. The `Check` response carries `t0-sdk-ecosystem` and `t0-sdk-version`, the latter matching the SDK's own version constant.
3. An **unsigned** call is refused by the signature interceptor — the mounted service is behind the same auth as everything else.

Where they live: [`go/provider/health_test.go`](../go/provider/health_test.go), [`node/sdk/test/health.test.ts`](../node/sdk/test/health.test.ts), [`python/sdk/tests/integration/test_health_signed.py`](../python/sdk/tests/integration/test_health_signed.py), [`HealthServiceIntegrationTest.java`](../java/sdk/src/test/java/network/t0/sdk/integration/HealthServiceIntegrationTest.java), [`HealthServiceImplTests.cs`](../csharp/sdk/T0.ProviderSdk.Tests/Provider/HealthServiceImplTests.cs).

[`cross_test/`](../cross_test/) is deliberately not used here. Its job is locking crypto and wire-format invariants (Keccak256, secp256k1) across languages; health is an ordinary unary RPC layered on those, and per-ecosystem tests cover it without cross-vector duplication.
