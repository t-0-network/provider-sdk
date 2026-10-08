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

`public_key_parsing` is the rule every provider server applies to its configured network key and to the `X-Public-Key` header, `timestamp_parsing` the rule for `X-Signature-Timestamp`, and `base_url_parsing` the base URL rule of every client. Go and Java run it from the provider package, where the parser lives: `go/provider/cross_test.go` and `java/sdk/src/test/java/network/t0/sdk/provider/PublicKeyParsingVectorTest.java`. Node, Python and C# run it from the files above.

`private_key_parsing` is the rule every SDK parses the private key a client signs with (S5 in [`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md)). Every SDK runs it from the files above.

`signer_cases` is the exact output of each SDK's signer-from-hex factory for fixed digests. Every SDK runs it from the files above.

`constants` holds the values every SDK defines as named constants, `messages` every error text an SDK raises itself, `max_body_size_cases` how a configured body limit is read, `server_cases` the answer every provider server gives to each request of a set, and `client_cases` what every client must do against a reference server. Each SDK compares its constants and messages with the file in a contract test, runs `go_helper probe` against its own server and `go_helper client-probe` against its own client, so a difference in any of them fails that SDK's CI. The tests are listed in [`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md#receiving-calls-from-the-network-provider-servers).

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
| `probe <url> --sdk <name> [--protocol connect\|grpc] [--vectors <path>]` | Sends every `server_cases` request to a provider server and checks each answer |
| `client-probe --sdk <name> [--vectors <path>]` | Serves the `client_cases` on a free port (Connect over HTTP/1.1, gRPC over h2c) and prints `READY <base_url>`; checks the signature and deadline headers of every request an SDK client sends |

`serve` also mounts `test.v1.StreamTest` ([`cross_test/stream_test.proto`](../cross_test/stream_test.proto), reference only — every SDK builds the two methods by hand on `google.protobuf.StringValue`). It is served behind the Go SDK's own signature verification, which checks a streaming request the way the T-0 Network does: the signature over the first envelope only (or, for gRPC, its payload without the prefix), before the handler reads the rest.

A refused request fails with the code from [`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md#error-codes-and-messages) and the reason as its message. Every reply to a verified request starts with the framing it was verified over (`envelope:` or `payload:`), so the streaming cross tests check both from the call itself. The helper also logs the verdict to stderr (`<path> verified over the first envelope|payload` or `<path> rejected: <reason>`); the cross tests still wait for that line to check that the request went out with its first message, and look for its absence when nothing may be sent, so its wording is a contract with those tests. `cd cross_test/go_helper && go test ./...` checks the helper's wiring with the Go client; the verifier is tested in `go/provider`. Step by step, with every refusal: [`cross_test/README.md`](../cross_test/README.md#commands).

Default protocol is Connect (HTTP/1.1). Pass `--grpc` for gRPC protocol over h2c.

## Server-to-server test matrix

| Direction | Python | Node | C# | Java |
|---|---|---|---|---|
| **Lang→Go** | Health | Health | Health | Health + PayOut |
| **Lang→Go streaming** | Client + server stream (async + sync) | Client + server stream | Client + server stream | Client + server stream |
| **Go→Lang** | Health (ASGI+WSGI) | Health | Health + PayOut | Health + PayOut |

Health checks set `service` to `grpc.health.v1.Health`, so the protobuf body is not empty (about 23 bytes). The signature hashes that body to 32 bytes, which is enough for interop coverage. PayOut calls in some SDKs are historical and are not required for that coverage.

Streaming runs one way only: providers don't serve streaming RPCs, so there is no Go→Lang streaming test. The streaming cross tests check that each SDK's client signs the bytes the network verifies and sends the request with its first message; the verifier's own verdicts are pinned in the Go SDK (`go/provider`). The rules they test: [`docs/STREAMING.md`](STREAMING.md).

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

Every provider server accepts a gRPC body signed with or without its 5-byte frame prefix: rule V6 in [`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md). The Java ↔ Go cross tests exercise the fallback, because Java's `SigningClientInterceptor` signs above the gRPC framer. C# clients sign the framed body (`SigningDelegatingHandler` sits below the framer) and pass on the primary path.

## CI integration

Each SDK's CI workflow:
1. Sets up Go with `actions/setup-go@v7`
2. Caches the Go helper binary (keyed on Go sources + go.sum)
3. Builds the helper
4. Runs the SDK's tests (which include cross-language tests)

The Go workflow also runs the helper's own tests (`go test -race ./...` in `cross_test/go_helper`).

Tests **fail** (not skip) if the Go helper binary is missing in CI, and the shared `setup-go-helper` action fails the job when the binary was not built. The Go SDK's own tests (`cd go && go test ./...`) need no helper: the Go server and client run against the probes from the helper's tests.

## Adding a new SDK

1. Create test files that start/call the Go helper for bidirectional health round-trips (with `service` field set for non-empty body)
2. Add Go setup + helper build to the SDK's CI workflow (see `ci-python.yaml` for the pattern)
3. Add `go/**` and `cross_test/**` to the CI workflow's path triggers
4. Tests must fail (not skip) if the helper binary is missing in CI
5. Streaming against `go_helper serve`: a client stream of several messages and a server stream, each verified over the expected framing; no buffering (message 2 after the helper logged message 1 as verified); a large first message; and refusals of a stale timestamp and an empty stream
