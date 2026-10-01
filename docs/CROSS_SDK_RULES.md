# Cross-SDK rules

The five SDKs (Go, Node, Python, Java, C#) must behave the same way. This page lists every rule they share, in one place, and what catches an SDK that drifts from it.

**How to change a rule.** Change it here, in the shared vectors or tests that check it, and in all five SDKs, in one pull request. A rule that has no shared vector or cross test is marked *per-SDK tests*: each SDK tests it on its own, so nothing catches drift automatically yet.

Shared vectors live in [`cross_test/test_vectors.json`](../cross_test/test_vectors.json) (format: [`cross_test/README.md`](../cross_test/README.md)). Cross tests against the Go helper are described in [`CROSS_LANGUAGE_TESTING.md`](CROSS_LANGUAGE_TESTING.md).

## Signing

| # | Rule | Checked by |
|---|---|---|
| S1 | `digest = Keccak256(body_bytes ‖ uint64le(timestamp_ms))`, Keccak-256 (legacy), not NIST SHA3-256. Headers: `X-Public-Key`, `X-Signature`, `X-Signature-Timestamp`. | `keccak256`, `request_signing`, `request_signing_cases` |
| S2 | Always sign the raw bytes as sent, never a re-encoded message. Protobuf encoding is not canonical. | `request_signing_cases` |
| S3 | A unary call signs its whole body. A client- or server-streaming call signs its first request envelope as sent (`flags ‖ uint32be(length) ‖ payload`); later messages are unsigned. Java signs the first message without its 5-byte prefix (above the gRPC framer). Details: [`STREAMING.md`](STREAMING.md). | `stream_signing_cases`; streaming cross tests (`go_helper serve`) |
| S4 | A stream is sent as soon as its first message is signed; the client never buffers the stream. Bidirectional streams are refused. | streaming cross tests |
| S5 | Private key: an optional `0x`/`0X`, exactly 64 hex characters, a value in [1, n-1]. Errors: "private key must not be null or empty", "private key must be 32 bytes (64 hex characters)", "private key must be in range [1, n-1]". | *per-SDK tests* |

## Calling the network (clients)

| # | Rule | Checked by |
|---|---|---|
| C1 | Unary calls have a 15 s deadline and streams 5 min, sent to the server. A deadline set by the caller replaces the default. A configured timeout must be greater than zero (Node: also at most 2147483647 ms, the limit of its timers); nothing else is checked. | *per-SDK tests* |
| C2 | Base URL: empty is "base URL is not set"; a value without `://` is read as `https://` + value; then the language's standard URL parser, plus: scheme http or https, a host, no user info, no query, no fragment, a port (if any) in 1..65535. Anything else is "base URL is not valid". A path in the base URL prefixes every call. | `base_url_parsing` |
| C3 | Redirects are not followed. | *per-SDK tests* |

## Receiving calls from the network (provider servers)

A provider server receives unary calls only. Streaming is client-side: the network is always the server of a stream. The Go SDK is the exception: its server also verifies client- and server-streaming calls (V9), because a Go server built with `provider.Handler` may serve streams, and the Go helper's streaming service runs on it.

| # | Rule | Checked by |
|---|---|---|
| V1 | The configured network public key is checked when the server is set up. Surrounding whitespace is trimmed. A missing or blank key fails with "network public key is not set", a malformed key with "invalid network public key: …". | `public_key_parsing`; per-SDK startup tests |
| V2 | A public key (configured or `X-Public-Key`) is an optional `0x`/`0X`, strict hex (no whitespace, no trailing junk), and a compressed (33 bytes) or uncompressed (65 bytes) point on secp256k1. Keys are compared as points, so both forms of the network key are accepted. | `public_key_parsing` |
| V3 | `X-Signature-Timestamp` is ASCII digits only (no sign, no spaces) and fits a signed 64-bit integer. | `timestamp_parsing` |
| V4 | The timestamp must be within ±60 s of the server's clock. This is not configurable; Node's `createRequestVerifier`/`createRequestDecoder` `toleranceMs` may only narrow it. | *per-SDK tests* |
| V5 | Signatures are 64 or 65 bytes (r ‖ s, optional recovery byte, which is ignored). | `signature_verification` |
| V6 | The signature is verified over the whole body. If that fails, and the request is `application/grpc*` with a body of exactly one uncompressed frame, it is verified again over the message without its 5-byte prefix. Java rebuilds the frame instead, with the same result. | *per-SDK tests*; Java ↔ Go cross tests |
| V7 | A body over the server's limit (10 MiB by default) is rejected without being read further. | *per-SDK tests* |
| V8 | There is no way to serve without signature verification. Go's `WithVerifySignatureFn` is a no-op; Python refuses a call its middleware did not verify (INTERNAL), and no handler option removes the check. | *per-SDK tests* |
| V9 | Go only: a request whose content type is `application/connect+*`, `application/grpc` or `application/grpc+*` is verified over its first envelope, prefix included, and over gRPC also over the uncompressed first payload alone (S3). Only that envelope is read before the verdict; its length is checked against the limit first. The rest of the body goes to the handler as it arrives, and a stream as a whole has no limit (each message has). A rejected call fails before its handler runs, streaming or not. For a unary gRPC body, which is exactly one frame, the result is the same as V6. | `go/provider` tests; streaming cross tests (`go_helper serve`) |

### Error codes

| Failure | Code |
|---|---|
| missing header; malformed `X-Signature` or `X-Signature-Timestamp`; timestamp outside the window | InvalidArgument |
| `X-Public-Key` present but not the network key (bad hex, not a key, or another key); signature does not verify | Unauthenticated |
| Go, enveloped body: no first message, or a first message cut short ("no first message", "truncated first message") | Unauthenticated |
| body over the limit; Go, enveloped body: first envelope over the limit | ResourceExhausted |

Messages may differ between SDKs; codes may not.

## Where each SDK implements the server rules

| SDK | Server verification |
|---|---|
| Go | `go/provider/verify_signature.go`, `go/provider/signature_error.go`, `go/provider/handler.go`, `go/internal/pubkey` (key parser), `go/internal/envelope` (enveloped content types) |
| Node | `node/sdk/src/common/service.ts`, `node/sdk/src/common/node.ts`, `node/sdk/src/common/crypto/{keys,request}.ts` |
| Python | `python/sdk/src/t0_provider_sdk/provider/{middleware,middleware_wsgi,interceptor,handler}.py`, `crypto/keys.py` |
| Java | `java/sdk/src/main/java/network/t0/sdk/provider/SignatureVerificationInterceptor.java`, `crypto/SignatureVerifier.java` |
| C# | `csharp/sdk/T0.ProviderSdk/Provider/SignatureVerificationMiddleware.cs` |
