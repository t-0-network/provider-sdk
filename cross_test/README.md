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
| `stream_signing_cases` | a streaming request body → the bytes its signature covers, and the signature |
| `public_key_parsing` | a public key string → accepted or not, and its 65-byte uncompressed form |
| `timestamp_parsing` | an `X-Signature-Timestamp` value → accepted or not, and its value |
| `base_url_parsing` | a client's base URL → accepted or not, and the error message |

`body_hex` is the exact preimage: whatever the transport put in the body, before the
timestamp is appended and before anything decodes it. `grpc-framed-body` carries the gRPC
frame (`0x00` + `uint32be(length)` + message) because that is what the network signs when
it calls a provider over gRPC, while Connect callers sign the message alone — the Java
provider verifies against both.

`stream_signing_cases` are streaming requests (client streaming, server streaming), whose
signature covers only the first message. `body_hex` is the whole body as sent — several
envelopes — and `signed_hex` the part that is signed: the first envelope, prefix included
(`covers: first_envelope`, what a signer below the gRPC framer covers — Go, Node, Python, C#),
or its payload alone (`covers: first_payload`, what the Java SDK covers over gRPC). The network
accepts both over gRPC. `content_type` is what the request carries: it is how a client decides
to sign the first envelope rather than the whole body. `empty-client-stream` is a client stream
closed before its first message: every SDK signs those empty bytes and sends the request, and the
network rejects it.

`public_key_parsing`, `timestamp_parsing` and `base_url_parsing` check rules V2, V3 and C2 of
[`docs/CROSS_SDK_RULES.md`](../docs/CROSS_SDK_RULES.md). A valid public key row gives the key's
65-byte uncompressed form, so the compressed and uncompressed forms of a key compare equal; a
valid timestamp row gives its value.

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
| `call-pay-out <url> <hex_private_key> [--grpc]` | Signed PayOut RPC |
| `call-health <url> <hex_private_key> [--grpc]` | Signed health check |

`serve` also serves `test.v1.StreamTest` ([`stream_test.proto`](stream_test.proto), reference
only). Like the provider service, it is built with `provider.Handler`, so the Go SDK's signature
verification checks every request, as the T-0 Network does for streaming RPCs. Each SDK's streaming
cross test calls it with hand-built methods on `google.protobuf.StringValue`; the helper builds its
side the same way (`stream.go`).

For a streaming request the verifier, in order:

1. checks the headers before it reads any of the body: present and well-formed, the timestamp
   within ±60 s, `X-Public-Key` the network key, a signature of 64 or 65 bytes;
2. reads exactly the first envelope and verifies the signature over it, prefix included, or, for
   gRPC only, over its payload without the prefix, which is what a signer above the gRPC framer
   (Java) covers. The codec does not matter: Connect JSON streams are signed the same way;
3. hands the handler the whole body, first envelope included, as it arrives.

A refused request fails with an RPC error whose message is the reason, and the handler never runs:

| Reason | Code |
|---|---|
| a missing header; `X-Signature` not hex; a malformed `X-Signature-Timestamp`; `timestamp is outside the allowed time window` | `invalid_argument` |
| `X-Public-Key` not the network key (`invalid public key`, `request signed with unknown public key`); a signature not 64 or 65 bytes (`invalid signature`); `signature verification failed`; `no first message`; `truncated first message` | `unauthenticated` |
| a first message over the size limit (10 MiB) | `resource_exhausted` |

A streaming procedure called with a unary content type (`application/proto`) gets HTTP 415 from
connect-go. Every reply to a verified request starts with the framing it was verified over,
`envelope:` or `payload:` (gRPC without the prefix, as Java signs): `ClientStream` answers
`<framing>:<values joined by ",">`, and `ServerStream` sends `<framing>:<value>` three times. The
streaming cross tests check the framing and the refusal from the call itself.

The helper also logs the verdict to stderr before the handler reads past the first message:
`<path> verified over the first envelope|payload` or `<path> rejected: <reason>`, for every path
under `/test.v1.StreamTest/`. The cross tests still use that log in two places, so its wording is a
contract: a request went out with its first message (message 2 is produced only once message 1 is
logged as verified), and a call cancelled before its first message sent nothing (no line at all).
`go test ./...` here checks the helper's wiring (the replies, a refusal and the log lines) with the
Go client over Connect, Connect JSON and gRPC (Go signs below the gRPC framer, so it is always
verified over the envelope). The verifier itself is tested in the Go SDK (`go/provider`). The client
rules: [`docs/STREAMING.md`](../docs/STREAMING.md).

Default protocol is Connect (HTTP/1.1). Pass `--grpc` for gRPC protocol over h2c.

### Cross-language server tests

| Direction | Python | Node | C# | Java |
|---|---|---|---|---|
| **Lang→Go** | ✅ Health | ✅ Health | ✅ Health | ✅ Health + PayOut |
| **Lang→Go streaming** | ✅ Client + server stream | ✅ Client + server stream | ✅ Client + server stream | ✅ Client + server stream |
| **Go→Lang** | ✅ Health (ASGI+WSGI) | ✅ Health | ✅ Health + PayOut | ✅ Health + PayOut |

| Language pair | Test file | Protocol |
|---|---|---|
| Python ↔ Go | `python/tests/cross_test/test_cross_server.py` (async), `test_cross_server_sync.py` (sync); streaming: `test_cross_stream.py` | Connect (streaming: Connect binary and JSON, gRPC) |
| C# ↔ Go | `csharp/sdk/T0.ProviderSdk.Tests/CrossTest/CrossServerTests.cs` (incl. streaming) | gRPC |
| Node ↔ Go | `node/sdk/test/cross_server.test.ts`; streaming: `cross_stream.test.ts` | Connect (streaming: binary and JSON) |
| Java ↔ Go | `java/sdk/src/test/java/network/t0/sdk/integration/CrossServerTests.java` (incl. streaming) | gRPC |

Each language also has crypto-level tests (`test_cross_signature.py` for Python) that use
the `hash`, `sign`, `verify`, and `pubkey` commands.

Full documentation: [`docs/CROSS_LANGUAGE_TESTING.md`](../docs/CROSS_LANGUAGE_TESTING.md).
