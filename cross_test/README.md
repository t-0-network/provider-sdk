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

Hex values carry no `0x` prefix, except the `input` of the `*_parsing` sections, which is
given exactly as a caller would pass it. Signatures are 64 bytes (`r || s`) unless a case
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
| `signer_cases` | a private key and a digest → what the SDK's signer-from-hex factory gives: the 65-byte signature and public key, or the error |
| `signature_verification` | a presented request → does it verify |
| `stream_signing_cases` | a streaming request body → the bytes its signature covers, and the signature |
| `public_key_parsing` | a public key string → accepted or not, and its 65-byte uncompressed form |
| `timestamp_parsing` | an `X-Signature-Timestamp` value → accepted or not, and its value |
| `base_url_parsing` | a client's base URL → accepted or not, and the error message |
| `private_key_parsing` | a private key string → its 65-byte uncompressed public key, or the error message |
| `constants` | the values every SDK defines as named constants (body limit, timestamp window, header names, client defaults, timeout bound) |
| `server_cases` | a request to a provider server → the code and the message of its answer |
| `client_cases` | a call an SDK client makes to `go_helper client-probe` → the code it must end with |
| `messages`, `messages_scope` | every message an SDK raises itself, by name, and the SDKs that raise the ones not every SDK can |
| `max_body_size_cases` | a configured max body size → the limit a server uses (0 or less keeps the default) |

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

`public_key_parsing`, `timestamp_parsing`, `base_url_parsing` and `private_key_parsing` check
rules V2, V3, C2 and S5 of [`docs/CROSS_SDK_RULES.md`](../docs/CROSS_SDK_RULES.md). A valid public
key row gives the key's 65-byte uncompressed form, so the compressed and uncompressed forms of a
key compare equal; a valid timestamp row gives its value. A valid private key row gives the
uncompressed public key of the key it parses to, and an invalid one the message every SDK fails
with.

`constants` are the values every SDK defines, one named constant each. Each SDK has a contract
test that compares its constants with these and fails when the file names one the SDK lacks.

`server_cases` are requests to `grpc.health.v1.Health/Check`, the service every provider server
mounts, and the answer each must get: `ok` (a SERVING reply), or an error code with its exact
message. `go_helper probe` sends them to a running server (below). A case changes one thing of a
signed request: a header missing or malformed, the timestamp, the key, the signature, the body size
(`body_size` is the whole HTTP body, gRPC prefix included; the request is padded with an unknown
field), or a body that is not a request message. The `order-*` cases have two faults, so they pin
which check runs first. `health-unknown-service` asks Check for a service the server does not have,
and `response-invalid` and `response-invalid-nested-field` call `ProviderService/ApprovePaymentQuotes`
and `PayOut`, which the SDK's probe test serves with an invalid response: they pin the rendering of
a violation, `<field path>: <message>`. Every reply of the health service must carry
`T0-Sdk-Ecosystem` (the `--sdk` name) and `T0-Sdk-Version`. `library_message` names the SDKs whose message for that case comes from the
RPC library underneath; only the code is compared there.

`messages` holds every message an SDK raises itself, setup and RPC errors alike, under one name
each; every SDK holds them as named constants and its constants test checks them. A `{placeholder}`
is filled in when the message is raised. `go test` in `go_helper/` checks that every message the
other sections expect is one of them.

`signer_cases` pin the signer contract: the factory's signer returns 65 bytes `r‖s‖v` (v 0 or 1)
and the 65-byte uncompressed key, and refuses a digest that is not 32 bytes. A custom signer is a
function of the same shape; the `custom_signer` client cases check what a client does with its
output: it refuses a signature that is not 64 or 65 bytes or a key that is not 65 bytes
uncompressed, and sends any other signature exactly as returned, v included.

`client_cases` are calls each SDK's client makes to `grpc.health.v1.Health/Check` on `go_helper
client-probe` (below), one per case at the base URL `<base_url>/<case name>`, with the timeouts the
case sets. The probe checks each request it gets (the signature headers and the deadline header)
and answers SERVING, or `failed_precondition` with the reason; a case that expects an error checks
that a call which must fail does (a per-call timeout of 0, a redirect). `client_cases_note` is what
an SDK test must do.

`signature_verification` answers one question: does this signature verify against this
public key for this body and timestamp. It stops there on purpose. Whether a request is
*accepted* also depends on the timestamp window, which every provider measures against a
clock only it can see, so that belongs in each SDK's own middleware tests, not in a shared
fixture.

### Adding a case

Sign it with any one SDK and run the other four. Every SDK ignores `v`, on the wire and in
its public `verify_signature` helper, so a 65-byte fixture signature may carry any last byte
(`v-plus-27` and `wrong-recovery-id` pin this).

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
| `probe <url> --sdk <name> [--protocol connect\|grpc] [--vectors <path>]` | Sends every `server_cases` request to the server at url and checks each answer |
| `client-probe --sdk <name> [--vectors <path>]` | Serves the `client_cases` on a free port; prints `READY <base_url>` |

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

`probe` signs each case with `keys.private_key`; the server's network key is `keys.public_key`.
Connect goes over HTTP/1.1, with `Expect: 100-continue` for a body over 1 MiB so that a server that
refuses a request on its headers answers before the body is sent; gRPC goes over h2c. It prints one
`PASS` or `FAIL` line per case and protocol, and exits 1 if any case failed. Every SDK runs it
against its own server (the probe tests are listed in
[`docs/CROSS_SDK_RULES.md`](../docs/CROSS_SDK_RULES.md#receiving-calls-from-the-network-provider-servers)).

`client-probe` listens on a free port of 127.0.0.1 (Connect over HTTP/1.1, gRPC over h2c), prints
`READY <base_url>` as its first line on stdout and runs until it is killed. It logs one line per
request on stderr: `PASS <case>`, or `FAIL <case>: <reason>`, `<case>` being the first segment of the
request's path. Every SDK test fails on a `FAIL` line and shows it. After each call it looks for a
`FAIL` line of that case. Once the probe has stopped, it looks for any `FAIL` line of the run, because
a call that ends on its own deadline (such as a per-call timeout of 0 sent anyway) can end before the
probe logs its request. The Go column of the client cases is `clientprobe_test.go` here, with the Go
SDK's own client.

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
