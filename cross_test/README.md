# Cross-language test infrastructure

## Test vectors

`test_vectors.json` is the shared fixture for the request signature. Every SDK reads it, so
a change here is a change every language has to agree with:

| SDK | Test |
|---|---|
| Go | `go/crypto/cross_test.go` |
| Node | `node/sdk/test/crypto.test.ts` |
| Java | `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java` |
| Python | `python/sdk/tests/crypto/test_cross_vectors.py` |
| C# | `csharp/sdk/T0.ProviderSdk.Tests/Crypto/CrossTestVectors.cs` |

Hex values carry no `0x` prefix. Signatures are 64 bytes (`r || s`) unless a case
appends a recovery byte.

### The scheme

```
digest    = keccak256(body || uint64le(timestamp_ms))
signature = secp256k1 ECDSA over that 32-byte digest — RFC 6979 deterministic k
            (HMAC-SHA256), low-S
```

Deterministic `k` is what lets a fixture name exact signature bytes: the same key and
digest give the same `r` and `s` in every library, so a vector can be an equality
assertion rather than a sign-then-verify round trip.

### What is in the file

| Key | Contents |
|---|---|
| `keys` | the key the signing cases use and the verification cases are checked against |
| `impostor_keys` | a second key, for cases that must fail |
| `keccak256` | text input → hash |
| `request_signing` | one signing case with a text `body` |
| `request_signing_cases` | signing cases with a `body_hex`, so a body can be binary, framed or empty |
| `signature_verification` | a presented request → does it verify |

`body_hex` is the exact preimage: whatever the transport put in the body, before the
timestamp is appended and before anything decodes it. `grpc-framed-body` carries the gRPC
frame (`0x00` + `uint32be(length)` + message) because that is what the network signs when
it calls a provider over gRPC, while Connect callers sign the message alone — the Java
provider verifies against both.

`signature_verification` answers one question: does this signature verify against this
public key for this body and timestamp. It stops there on purpose. Whether a request is
*accepted* also depends on the timestamp window, which every provider measures against a
clock only it can see, so that belongs in each SDK's own middleware tests, not in a shared
fixture.

### Adding a case

Sign it with any one SDK and run the other four. On the wire every SDK ignores `v`.
Python's `verify_signature` helper — which the Python cross-vector test calls — does
not, so fixture 65-byte signatures must carry the recovery byte that recovers the
trusted key.

## Go helper (`go_helper/`)

A single Go binary that all cross-language server-to-server tests share. It signs,
verifies, hashes, runs a ConnectRPC provider server, and makes signed client calls.

### Building

```bash
cd cross_test/go_helper && go build -o go_helper .
```

CI builds the helper automatically (each language's CI workflow sets up Go and builds it).

### Commands

| Command | Description |
|---|---|
| `hash <hex_data>` | Keccak256 hash |
| `sign <hex_private_key> <hex_digest>` | Sign + return signature and public key |
| `verify <hex_public_key> <hex_digest> <hex_signature>` | Verify signature |
| `pubkey <hex_private_key>` | Derive public key |
| `serve <port> <hex_public_key>` | Provider server (h2c, Connect + gRPC) |
| `call-pay-out <url> <hex_private_key> <hex_public_key> [--grpc]` | Signed PayOut RPC |
| `call-health <url> <hex_private_key> [--grpc]` | Signed health check |

Default protocol is Connect (HTTP/1.1). Pass `--grpc` for gRPC protocol over h2c.

### Cross-language server tests

| Direction | Python | Node | C# | Java |
|---|---|---|---|---|
| **Lang→Go** | ✅ Health | ✅ Health | ✅ Health | ✅ Health + PayOut |
| **Go→Lang** | ✅ Health (ASGI+WSGI) | ✅ Health | ✅ Health + PayOut | ✅ Health + PayOut |

| Language pair | Test file | Protocol |
|---|---|---|
| Python ↔ Go | `python/tests/cross_test/test_cross_server.py` (async), `test_cross_server_sync.py` (sync) | Connect |
| C# ↔ Go | `csharp/sdk/T0.ProviderSdk.Tests/CrossTest/CrossServerTests.cs` | gRPC |
| Node ↔ Go | `node/sdk/test/cross_server.test.ts` | Connect |
| Java ↔ Go | `java/sdk/src/test/java/network/t0/sdk/integration/CrossServerTests.java` | gRPC |

Each language also has crypto-level tests (`test_cross_signature.py` for Python) that use
the `hash`, `sign`, `verify`, and `pubkey` commands.

Full documentation: [`docs/CROSS_LANGUAGE_TESTING.md`](../docs/CROSS_LANGUAGE_TESTING.md).
