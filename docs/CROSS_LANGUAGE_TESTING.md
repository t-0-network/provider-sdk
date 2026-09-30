# Cross-Language Testing

## Overview

All SDKs share cross-language test infrastructure in `cross_test/` to verify cryptographic interoperability and server-to-server communication between languages.

## Test vectors

`cross_test/test_vectors.json` is the shared fixture for the request signature scheme. Every SDK reads it:

| SDK | Test file |
|---|---|
| Go | `go/crypto/cross_test.go` |
| Node | `node/sdk/test/crypto.test.ts` |
| Java | `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java` |
| Python | `python/sdk/tests/crypto/test_cross_vectors.py` |
| C# | `csharp/sdk/T0.ProviderSdk.Tests/Crypto/CrossTestVectors.cs` |

`stream_signing_cases` covers streaming RPCs, whose signature covers only the first request message. See [`cross_test/README.md`](../cross_test/README.md).

## Go helper

A single Go binary at `cross_test/go_helper/` that all server-to-server tests share.

### Building

```bash
cd cross_test/go_helper && go build -o go_helper .
```

CI builds it automatically (each language's CI workflow sets up Go and builds it).

### Commands

| Command | Description |
|---|---|
| `hash <hex_data>` | Keccak256 hash |
| `sign <hex_private_key> <hex_digest>` | Sign + return signature and public key |
| `verify <hex_public_key> <hex_digest> <hex_signature>` | Verify signature |
| `pubkey <hex_private_key>` | Derive public key |
| `serve <port> <hex_public_key>` | Start a provider server (h2c, Connect + gRPC) |
| `call-pay-out <url> <key> [--grpc]` | Signed PayOut RPC |
| `call-health <url> <key> [--grpc]` | Signed health check |

`serve` also mounts `test.v1.StreamTest` ([`cross_test/stream_test.proto`](../cross_test/stream_test.proto), reference only — every SDK builds the two methods by hand on `google.protobuf.StringValue`). Its verifier checks a streaming request the way the T-0 Network does: the signature over the first envelope only (or, for gRPC, its payload without the prefix), before the handler reads the rest.

A refused request fails with an `unauthenticated` RPC error whose message is the reason, and every reply to a verified request starts with the framing it was verified over (`envelope:` or `payload:`), so the streaming cross tests check both from the call itself. The verifier also logs its verdict to stderr (`<path> verified over the first envelope|payload` or `<path> rejected: <reason>`); the cross tests still wait for that line to check that the request went out with its first message, and look for its absence when nothing may be sent, so its wording is a contract with those tests. `cd cross_test/go_helper && go test ./...` tests the verifier itself and runs the Go client against it. The verifier step by step: [`cross_test/README.md`](../cross_test/README.md#commands).

Default protocol is Connect (HTTP/1.1). Pass `--grpc` for gRPC protocol over h2c.

## Server-to-server test matrix

| Direction | Python | Node | C# | Java |
|---|---|---|---|---|
| **Lang→Go** | Health | Health | Health | Health + PayOut |
| **Lang→Go streaming** | Client + server stream (async + sync) | Client + server stream | Client + server stream | Client + server stream |
| **Go→Lang** | Health (ASGI+WSGI) | Health | Health + PayOut | Health + PayOut |

Streaming runs one way only: providers don't serve streaming RPCs, so there is no Go→Lang streaming test. The streaming cross tests check that each SDK's client signs the bytes the network verifies and sends the request with its first message; the verifier's own verdicts are pinned in `cross_test/go_helper`. The rules they test: [`docs/STREAMING.md`](STREAMING.md).

### Test files

| Language | File | Protocol |
|---|---|---|
| Python (async) | `python/tests/cross_test/test_cross_server.py` | Connect |
| Python (sync) | `python/tests/cross_test/test_cross_server_sync.py` | Connect |
| Python streaming (async + sync) | `python/tests/cross_test/test_cross_stream.py` | Connect (binary and JSON) + gRPC |
| Node | `node/sdk/test/cross_server.test.ts` | Connect |
| Node streaming | `node/sdk/test/cross_stream.test.ts` | Connect (binary and JSON) |
| C# (incl. streaming) | `csharp/sdk/T0.ProviderSdk.Tests/CrossTest/CrossServerTests.cs` | gRPC |
| Java (incl. streaming) | `java/sdk/src/test/java/network/t0/sdk/integration/CrossServerTests.java` | gRPC |

## Dual-framing (gRPC interop)

The Go server's signature verification middleware accepts signatures over both gRPC-framed and unframed protobuf bodies. This enables Java gRPC clients (whose `SigningClientInterceptor` signs above the gRPC framer) to interoperate with the Go server (which reads the gRPC-framed HTTP body). C# clients sign the framed body (`SigningDelegatingHandler` sits below the gRPC framer in the HttpClient pipeline) and pass on the primary verification path.

The fallback logic: try full body first; if that fails AND the request is `application/grpc` with a valid 5-byte gRPC frame prefix, strip the prefix and retry. See `go/provider/verify_signature.go`.

## CI integration

Each SDK's CI workflow:
1. Sets up Go with `actions/setup-go@v7`
2. Caches the Go helper binary (keyed on Go sources + go.sum)
3. Builds the helper
4. Runs the SDK's tests (which include cross-language tests)

The Go workflow also runs the helper's own tests (`go test -race ./...` in `cross_test/go_helper`).

Tests **fail** (not skip) if the Go helper binary is missing in CI.

## Adding a new SDK

1. Create test files that start/call the Go helper for bidirectional health round-trips (with `service` field set for non-empty body)
2. Add Go setup + helper build to the SDK's CI workflow (see `ci-python.yaml` for the pattern)
3. Add `go/**` and `cross_test/**` to the CI workflow's path triggers
4. Tests must fail (not skip) if the helper binary is missing in CI
