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
| S5 | Private key: an optional `0x`/`0X`, exactly 64 hex characters, a value in [1, n-1]. Errors: "private key must not be null or empty", "private key must be 32 bytes (64 hex characters)", "private key must be in range [1, n-1]". | `private_key_parsing` |
| S6 | The SDK's own signer signs a 32-byte digest and returns 65 bytes r ‖ s ‖ v (v is 0 or 1, s is low) and the 65-byte uncompressed public key. Any other digest length fails with "digest must be 32 bytes". The public key of a private key: Go `crypto.PublicKeyFromPrivateKey`, Node `publicKeyFromPrivateKey`, Python `public_key_from_private_key`, Java `Signer.publicKeyFromPrivateKey`, C# `Signer.PublicKeyFromPrivateKey`. | `signer_cases` |
| S7 | A custom signer is a function from a 32-byte digest to a signature and a 65-byte uncompressed public key: Go `crypto.SignFn`, Node `SignerFunction` (async), Python `SignFn`, Java `DigestSigner` (a functional interface), C# the `SignFn` delegate. The signer-from-hex factory (Go `crypto.NewSignerFromHex`, Node `newSignerFromHex`, Python `new_signer_from_hex`, Java `Signer.fromHex`, C# `Signer.FromHex`) returns one, and the client takes it as its signer argument as is. Before sending, the client checks the output: the signature is 64 or 65 bytes ("signature must be 64 or 65 bytes"; its last byte is not checked), then the key is 65 bytes uncompressed ("public key must be 65 bytes, uncompressed"). A signer that returns nothing fails the signature check. The signature is sent exactly as returned. A signer that fails, or returns output that fails a check, fails the call with Internal "signing the request failed: <cause>", and nothing is sent. A missing signer is "signer must not be null". Go and Python, where a key and a signer are separate arguments, refuse both together: "a private key and a signer must not both be given". | `client_cases` (`factory-signer`, `custom-signer*`) |

## Calling the network (clients)

| # | Rule | Checked by |
|---|---|---|
| C1 | Unary calls have a 15 s deadline and streams 5 min, sent to the server. A deadline set by the caller replaces the default. A configured timeout must be greater than zero and at most 2147483647 ms: "timeout must be a positive duration of at most 2147483647 ms", "stream timeout must be a positive duration of at most 2147483647 ms". A per-call timeout of zero or less fails the call at once with DeadlineExceeded, and nothing is sent. | `constants` (`default_timeout_ms`, `default_stream_timeout_ms`, `max_timeout_ms`); `client_cases` (`*-timeout*`) |
| C2 | Base URL: the default is `https://api.t-0.network`; empty is "base URL is not set"; a value without `://` is read as `https://` + value; then the language's standard URL parser, plus: no whitespace or control character anywhere (U+0000–U+0020, U+007F and the other Unicode White_Space characters, such as U+00A0), scheme http or https, a host, no user info, no query, no fragment, a port (if any) in 1..65535. Anything else is "base URL is not valid". A path in the base URL prefixes every call. | `constants` (`default_base_url`); `base_url_parsing` |
| C3 | Redirects are not followed. | `client_cases` (`redirect-not-followed`) |

`client_cases` in [`cross_test/test_vectors.json`](../cross_test/test_vectors.json) checks C1, C3 and S7 on every SDK's client. `go_helper client-probe` serves each case and checks the signature headers and the deadline header of every request it gets. It refuses a request that should not have been sent, and it requires a custom signer's signature to arrive byte for byte. Tests: `cross_test/go_helper/clientprobe_test.go` (Go), `node/sdk/test/client_probe.test.ts`, `python/tests/cross_test/test_client_probe.py`, `java/.../integration/ClientProbeTest.java`, `csharp/.../CrossTest/ClientProbeTests.cs`.

## Receiving calls from the network (provider servers)

A provider server receives unary calls only. Streaming is client-side: the network is always the server of a stream. The Go SDK is the exception: its server also verifies client- and server-streaming calls (V9), because a Go server built with `provider.Handler` may serve streams, and the Go helper's streaming service runs on it.

`server_cases` in [`cross_test/test_vectors.json`](../cross_test/test_vectors.json) checks the rules below on every SDK's server. `go_helper probe` sends each case to a running server, over Connect and over gRPC where the server speaks both, and compares the code and the message of the answer:

| SDK | Server under the shared cases | Test |
|---|---|---|
| Go | `provider.StartServer` with `provider.NewHttpHandler` | `cross_test/go_helper/probe_test.go` |
| Node | `createHandler` behind `node:http` (Connect) and `node:http2` (gRPC) | `node/sdk/test/probe.test.ts` |
| Python | `new_asgi_app` under hypercorn (Connect and gRPC), `new_wsgi_app` under waitress (Connect) | `python/tests/cross_test/test_probe.py` |
| Java | `ProviderServer` (gRPC) | `java/sdk/src/test/java/network/t0/sdk/integration/ProbeTest.java` |
| C# | `T0ProviderServer` (gRPC) | `csharp/sdk/T0.ProviderSdk.Tests/CrossTest/ProbeTests.cs` |

`constants` in the same file holds the values every SDK defines (the body limit, the timestamp window, the header names, the client defaults). `messages` holds every error text an SDK raises itself, one name each; `messages_scope` names the SDKs that raise a text only some SDKs can raise (an option or an input that exists in those SDKs only), and every other text is raised by all five. `max_body_size_cases` pins how a configured body limit is read. Each SDK has a test that compares its named constants and messages with the file, checks the exact set that applies to it, and fails when the file names one the SDK lacks: `go/provider/contract_test.go`, `node/sdk/test/contract.test.ts`, `python/sdk/tests/contract/test_constants.py`, `java/.../provider/ContractConstantsTest.java`, `csharp/.../ContractConstantsTests.cs`.

| # | Rule | Checked by |
|---|---|---|
| V1 | The configured network public key is checked when the server is set up. Surrounding whitespace is trimmed. A missing or blank key fails with "network public key is not set", a malformed key with "invalid network public key: " and the reason of V2. | `public_key_parsing`; per-SDK vector tests of the setup error |
| V2 | A public key (configured or `X-Public-Key`) is an optional `0x`/`0X`, strict hex (no whitespace, no trailing junk), and a compressed (33 bytes, `02` or `03`) or uncompressed (65 bytes, `04`) point on secp256k1. The hybrid forms (`06`, `07`) and the point at infinity (`00`) are refused. Keys are compared as points, so both forms of the network key are accepted. A refused key fails with "must be hex, with an optional 0x or 0X prefix" or "not a point on secp256k1". Every SDK's public signature-verification helper parses the key the same way. | `public_key_parsing`; `signature_verification` (`compressed-key`, `hybrid-key`); `server_cases` |
| V3 | `X-Signature-Timestamp` is ASCII digits only (no sign, no spaces) and fits a signed 64-bit integer: "invalid timestamp header: not a decimal number", "invalid timestamp header: value out of range". | `timestamp_parsing`; `server_cases` |
| V4 | The timestamp must be within ±60 s of the server's clock. It is fixed: no SDK has an option that changes it. | `constants` (`timestamp_window_ms`); `server_cases` |
| V5 | Signatures are 64 or 65 bytes (r ‖ s, optional recovery byte, which is ignored). r and s are in [1, n-1]; a high s verifies too. A signature of another length fails as one that does not verify. | `signature_verification`; `server_cases` |
| V6 | The signature is verified over the whole body. If that fails, and the request is `application/grpc*` (any case) with a body of exactly one uncompressed frame, it is verified again over the message without its 5-byte prefix. Java rebuilds the frame instead, with the same result (an approved exception). | `server_cases` (`valid-grpc-signed-without-prefix`); Java ↔ Go cross tests |
| V7 | The body of a unary call is the whole HTTP body, a gRPC call's 5-byte prefix included. A body over the server's limit (10 MiB by default) is refused without being read further: a declared `Content-Length` over the limit before anything is read, else as soon as the bytes read pass it. A configured limit of zero or less (Node: also `NaN`) keeps the default; nothing throws. | `constants` (`default_max_body_size`); `max_body_size_cases`; `server_cases` (at the limit, one byte over, 5 MiB, 11 MiB) |
| V8 | There is no way to serve without signature verification. Every server answers a rejected request itself, before the RPC library reads or decodes the request, so the caller gets the rejection whatever the body holds, and no handler runs: Go's middleware writes the error (`connect.NewErrorWriter`); Node's `createService` wraps every handler it registers; Python replays an empty request message, which its interceptor refuses; Java and C# refuse before the message is decoded. A call that bypassed the check is refused with Internal "no signature result in context". Go's `WithVerifySignatureFn` is a no-op, and Python's module-level middlewares take the `VerifySignatureFn` built by `new_verify_signature` or, as in 1.2.1, a caller's own `CustomVerifyFn`, which then decides the signature itself; the servers built by `new_asgi_app`/`new_wsgi_app` always use the network key. | `server_cases` (`order-*-before-decode`); per-SDK tests |
| V9 | Go only (an approved exception): a request whose content type is `application/connect+*`, `application/grpc` or `application/grpc+*` is verified over its first envelope, prefix included, and over gRPC also over the uncompressed first payload alone (S3). Only that envelope is read before the verdict; its length is checked against the limit first. The rest of the body goes to the handler as it arrives, and a stream as a whole has no limit (each message has). A rejected call fails before its handler runs, streaming or not. For a unary gRPC body, which is exactly one frame, the result is the same as V6. | `go/provider` tests; streaming cross tests (`go_helper serve`) |
| V10 | The checks run in one order, so a request with several faults gets the same answer everywhere: the HTTP method is POST (any other method is Unimplemented "GET requests are not supported": a call's message must be in its signed body; Java and C# serve gRPC only, where every call is a POST); `X-Public-Key` present; `X-Signature` present and hex; `X-Signature-Timestamp` present and well-formed; the window; `X-Public-Key` the network key; the signature length; then the body, its size first. | `server_cases` (`order-*`) |
| V11 | Health: a Check for an unknown service is NotFound "unknown service '<name>'". Every reply of the health service, NotFound included, carries `T0-Sdk-Ecosystem` (the SDK's name) and `T0-Sdk-Version` (the version set on the server, or the SDK's own when none or a blank one is set). | `server_cases` (`health-*`) |
| V12 | Every response is validated against its protovalidate rules before it is sent. An invalid one is Internal "response validation failed: <field path>: <message>", violations joined by "; ", and is logged with the RPC method and the SDK version. A rule that cannot be evaluated is Internal "response validation error: <cause>". A handler can run the same check itself: Go `provider.Validate`, Node `validate`, Python `validate`, Java `Validate.check`, C# `Validate.Check`. Predefined rules that the application declares in its own protos apply too. Go, Java, Python and C# find them in the application's generated code with no configuration; Node finds them in the `registry` option (see the approved exceptions). | `server_cases` (`response-invalid*`); `field_path_cases` (map keys quoted with JSON string escaping, non-ASCII kept); C# `PredefinedRuleTests` (an application's own predefined rule) |

### Error codes and messages

Every message an SDK raises itself is the same in every SDK, byte for byte. A message produced by the RPC library underneath may differ between SDKs; its code may not. This is an approved exception. The one such case on the server is a body over the limit in Java, where grpc-java refuses the message before the SDK sees it; `server_cases` marks it with `library_message`.

| Failure | Code | Message |
|---|---|---|
| a header missing or empty | InvalidArgument | "missing required header: X-Public-Key" (or X-Signature, X-Signature-Timestamp) |
| `X-Signature` not hex, or a prefix with no digits | InvalidArgument | "invalid header encoding: X-Signature" |
| `X-Signature-Timestamp` malformed | InvalidArgument | "invalid timestamp header: not a decimal number", "invalid timestamp header: value out of range" |
| timestamp outside the window | InvalidArgument | "timestamp is outside the allowed time window" |
| `X-Public-Key` present but not the network key (bad hex, not a key, or another key) | Unauthenticated | "request signed with unknown public key" |
| signature not 64 or 65 bytes, or does not verify | Unauthenticated | "signature verification failed" |
| Go, enveloped body: no first message, or a first message cut short | Unauthenticated | "no first message: …", "truncated first message: …" |
| body over the limit; Go, enveloped body: first envelope over the limit | ResourceExhausted | "max payload size of 10485760 bytes exceeded" (with the configured limit) |
| an HTTP method other than POST (Go, Node, Python) | Unimplemented | "GET requests are not supported" |
| a call that did not pass the check | Internal | "no signature result in context" |
| Go and Java: the request body cannot be read | Unauthenticated | "reading request body: …" |
| health Check for an unknown service | NotFound | "unknown service '<name>'" |
| a response that fails validation | Internal | "response validation failed: <field path>: <message>" |

## KYC files

`KycFileService` upload and download. The caller builds the client. The helper does not sign, retry, or check the file's size or type. How a helper cancels a partial upload is the abort rule in [`STREAMING.md`](STREAMING.md), not a separate rule.

| # | Rule | Checked by |
|---|---|---|
| K1 | Upload sends metadata, then chunks of at most `kyc_file_chunk_max_bytes`, never an empty chunk, and returns `file_id`. No retry. `upload_id` is unchanged. The client checks no size. A server error is the call's error, including mid-upload. | KYC file cross tests 1 and 4 |
| K2 | Download returns metadata and all bytes. A stream that is not one metadata followed by chunks cancels, fails with `kyc_download_shape` (`InvalidArgument`), and returns no bytes. A server error is the call's error. | KYC file cross tests 1, 2, and 3 |

## Approved exceptions

Differences the owner approved. A new one needs the same approval, and is added here.

- **Texts from the stack underneath.** A message produced by the RPC library or the language runtime (connect-go, connect-es, connectrpc-python, grpc-java, Grpc.AspNetCore, Kestrel, .NET's argument and format exceptions) may differ between SDKs; its code may not. Values that come from the stack and that the SDK does not set (Netty, Kestrel, `net/http`, `node:http`, the ASGI/WSGI server) are not differences.
- **Options and inputs that exist in some SDKs only.** Go's read, write, read-header and shutdown timeouts, TLS and HTTP/2 configuration; Java's metadata-size and handshake-timeout settings; the streaming internals of each client. They keep their documented behavior, and their texts are scoped in `messages_scope`.
- **V6:** Java rebuilds the gRPC frame instead of stripping it. **V9:** only the Go server verifies streaming requests.
- **Node-only helpers:** `createRequestVerifier`, `createRequestDecoder` and the `registry` option of `createService`, `createHandler` and `validate` (protobuf-es has no global registry, so custom rules need it). They raise the server's shared messages where one exists.
- **Header-constant names.** The three signature-header constants have the same values everywhere, but Go and Python define them as standalone constants (`SignatureHeader`, `SIGNATURE_HEADER`) while Node, Java and C# put them on one holder (`Headers.Signature`).
- **Deprecated, not removed:** C#'s `ISigner` stays as an obsolete compatibility path next to the `SignFn` delegate (S7): `Signer` implements it, the server registers it for dependency injection, and every factory has an obsolete `ISigner` overload. Java's `DigestSigner` stays and is now a functional interface.
- **Request validation.** Only the Go and Node servers validate incoming requests; tracked in [#401](https://github.com/t-0-network/provider-sdk/issues/401).
- **KYC download result.** Java returns `DownloadedFile` (the generated metadata and the bytes) because it has no tuples. Java also maps a checked `StatusException` to `StatusRuntimeException` with the same code and description, which is what the classic blocking stub throws.
- **Removed in this standardization (approved breaks):** Node's `toleranceMs` option and `DEFAULT_TOLERANCE_MS`; the timestamp-window constants Java `Headers.TIMESTAMP_VALIDITY_WINDOW_MS`, C# `Headers.TimestampValidityWindow`, Node `REQUEST_VALIDITY_MILLIS` and Python `TIMESTAMP_TOLERANCE_MS` (the window is `timestamp_window_ms` in every SDK); C#'s `T0Config.FromEnvironment` and `QuotePublisherService`, which moved to the C# starter.

## Where each SDK implements the server rules

| SDK | Server verification |
|---|---|
| Go | `go/provider/verify_signature.go`, `go/provider/signature_error.go`, `go/provider/handler.go`, `go/internal/pubkey` (key parser), `go/internal/envelope` (enveloped content types) |
| Node | `node/sdk/src/common/signature.ts`, `node/sdk/src/common/service.ts`, `node/sdk/src/common/crypto/{keys,request}.ts` |
| Python | `python/sdk/src/t0_provider_sdk/provider/{middleware,middleware_wsgi,interceptor,handler}.py`, `crypto/keys.py` |
| Java | `java/sdk/src/main/java/network/t0/sdk/provider/SignatureVerificationInterceptor.java`, `crypto/SignatureVerifier.java` |
| C# | `csharp/sdk/T0.ProviderSdk/Provider/SignatureVerificationMiddleware.cs` |
