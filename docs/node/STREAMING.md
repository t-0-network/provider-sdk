# Node SDK: the signing client transport

How `createClient` (`node/sdk/src/common/client/`) builds, signs and sends requests, unary and streaming. The protocol itself (digest, headers, which bytes are signed) is in the root [`CLAUDE.md`](../../CLAUDE.md#signature-protocol).

## Transport

- `createClient` uses connect-es's `createTransport` from `@connectrpc/connect/protocol-connect`, with `httpClient: createSigningFetchClient(signer)` (`signing-http-client.ts`). Every call goes through it: there is one signing path.
- Two transport instances are built from the same `transportOptions`, differing only in their default timeout (unary vs. stream).
- No interceptors, no compression (`acceptCompression: []`, `sendCompression: null`), default read/write limits.
- Binary by default: unary `application/proto`, streams `application/connect+proto`. `ClientOptions.useBinaryFormat: false` switches to Connect JSON (`application/json`, `application/connect+json`). The signature covers the bytes as sent in either format.
- Why not connect-es's own HTTP transports: connect-web's fetch transport cannot send a streaming request body, and connect-node gives no hook to the raw bytes to sign.

## Which bytes are signed

`isEnveloped` looks at the media type of `Content-Type` (lowercased, parameters dropped). The rule is the same in every SDK:

| Content type | Signed bytes |
|---|---|
| `application/connect+*`, `application/grpc`, `application/grpc+*` | the first envelope exactly as sent: `flags (1) \|\| uint32be(length) \|\| payload`, the payload compressed if flag bit 0 is set (this client never compresses) |
| anything else (`application/proto`, `application/json`, `application/grpc-web+*`, …) | the whole body |

`sign.ts` computes `Keccak256(bytes || uint64le(Date.now()))`; the signing client sets `X-Signature`, `X-Public-Key` and `X-Signature-Timestamp` on the request, replacing any the caller set.

### Unary

The body is read whole (`readAll`; connect-es sends it as one chunk, but any number are joined), signed, and passed to fetch as one `Uint8Array`, so fetch sets `Content-Length` and no `duplex` is used. A request without a body is signed over empty bytes and sent without one.

### Client and server streams

1. The client waits for the first body chunk. For a client stream this is the caller's first message, so the wait is raced against the call's signal (`untilAborted`): the stream timeout or an abort ends it, with the signal's reason (connect-es aborts with a `ConnectError`, `deadline_exceeded` on timeout). Nothing has been sent at that point.
2. connect-es yields one complete envelope per chunk (`transformJoinEnvelopes`), so the first chunk is the first envelope. `requireOneEnvelope` checks it rather than trusting it: a chunk whose length is not `5 + uint32be(length)` fails the call with `internal`, and nothing is sent. A change in connect-es's framing must never lead to signing other bytes.
3. The first envelope is signed and fetch is called at once, with a `ReadableStream` body and `duplex: "half"`. The stream enqueues the first envelope, then pulls later chunks from connect-es one at a time as the connection takes them. Nothing is buffered; later messages are not covered by the signature.

A server stream's single request message is its first envelope. A client stream closed before its first message is signed over empty bytes and sent with an empty body; the network rejects it (same rule in every SDK). If anything fails before the request is sent, the body iterator is closed.

### Bidirectional streams

`createClient` rejects a `bidi_streaming` method with `ConnectError` `unimplemented` before anything is sent. The network does not accept bidi streams (#370), which every SDK enforces; in Node it would not work anyway, because fetch with `duplex: "half"` sends the whole request before the response can be read, so the two directions could not interleave.

## fetch details

- `redirect: "error"` on every request, as connect-web set it: a redirect fails the call instead of re-sending a signed request elsewhere.
- `fetchFn` (tests only) defaults to `globalThis.fetch` looked up at each call.
- Responses: fetch sends `Accept-Encoding: gzip` itself and returns the body decoded, but keeps the encoded response's `Content-Encoding` and `Content-Length`. connect-es would take that `Content-Encoding` for a unary Connect compression it never negotiated and fail the call (`unsupported response encoding "gzip"`), and connect-go gzips unary responses by default. `decodedResponse` drops both headers, so connect-es reads the body as fetch decoded it.

## Timeouts

`ClientOptions.unaryTimeoutMs` and `streamTimeoutMs` are the transports' `defaultTimeoutMs`: each call's deadline, sent as `Connect-Timeout-Ms` and enforced locally. A call's own `timeoutMs` overrides them. Both default to none. The stream timeout runs from the start of the call, so it also covers the wait for the first message.

## Dependencies

`@connectrpc/connect` and `@connectrpc/connect-node` are pinned to one exact version in `node/sdk/package.json` (no `^`) and bumped together (connect-node peer-depends on connect's exact version). They are on the signing path: the client signs the bodies and envelopes connect-es builds, and `transportOptions` fills its `@private` `CommonTransportOptions`, which does not follow semver. Every field is spelled out, so an upstream change to that type fails `tsc`. A bump is a Tier 3 update ([dependency-update skill](../../.claude/skills/dependency-update/SKILL.md)): re-run `streaming.test.ts` (with its `transportOptions` guard), `unary_wire.test.ts`, `cross_stream.test.ts` and the `crypto.test.ts` vectors.

## Tests (`node/sdk/test/`)

- `streaming.test.ts`: a local server for `test.v1.StreamTest` whose verifier works like the network's: it reads a stream's first envelope from the raw body, verifies the signature over it (a unary body over all of it), answers 401 on failure, and only then hands the body to the handler. Covers client and server streams signed over the first envelope, no buffering (the caller's stream yields message n+1 only after the server has read message n), a 64 KiB first message, an empty stream, unsigned and whole-body-signed streams (both rejected), unary vs. stream content types, Connect JSON, per-transport timeouts and none by default, the stream timeout ending the wait for a first message, bidi refused with nothing sent, and the `transportOptions` guard.
- `cross_stream.test.ts`: client and server streams against `cross_test/go_helper serve`, checked through the verdict the helper logs to stderr (`<path> verified over the first envelope`, `<path> rejected: <reason>`). No-buffering: m2 is produced only after the helper logged m1 as verified. Also a 256 KiB first message, Connect JSON, and rejections (unsigned, signed over the whole body, stale timestamp, empty stream). Fails instead of skipping in CI when the helper binary is missing.
- `unary_wire.test.ts`: the golden unary request. Before connect-web was removed, the request it sent for a health check (Connect JSON, `{"service":"grpc.health.v1.Health"}`, `connect-protocol-version`, `connect-timeout-ms`, a call header, no `User-Agent`) was recorded. The SDK now sends the same Connect request in binary (`application/proto`, plus a `connect-es/…` `User-Agent`), signed over the body as sent, as one buffer, with `redirect: "error"`; with `useBinaryFormat: false` it sends connect-web's request exactly. Also: which content types are signed whole and which are treated as enveloped (a first chunk of two envelopes fails the call), a request without a body, and gzip-encoded responses and errors from a real server.
- `crypto.test.ts`: the `stream_signing_cases` vectors from `cross_test/test_vectors.json`: signed bytes, digest and signature for each; for the `first_envelope` cases, the signing HTTP client given one envelope per chunk sends the vector signature and body with `duplex: "half"`; a first chunk that is not exactly one envelope fails with `internal` and sends nothing.
- `stream_helpers.ts`: `StreamTest` described by hand from `cross_test/stream_test.proto`, plus `Unary` and `Bidi` methods that only the Node tests use, and `bufferingFetchClient`, which signs a stream over its whole body (or not at all) to produce requests the verifiers must reject.
