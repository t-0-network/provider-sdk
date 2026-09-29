# Node SDK: the signing client transport

How `createClient` (`node/sdk/src/common/client/`) builds, signs and sends requests, unary and streaming. The protocol itself (digest, headers, which bytes are signed) is in the root [`CLAUDE.md`](../../CLAUDE.md#signature-protocol).

## Transport

- `createClient` uses connect-es's `createTransport` from `@connectrpc/connect/protocol-connect`, with `httpClient: createSigningHttpClient(signer)` (`signing-http-client.ts`). Every call goes through it: there is one signing path.
- `createSigningHttpClient` signs the request and hands it to connect-node's `createNodeHttpClient({httpVersion: "1.1"})`, which sends it over HTTP/1.1 with `node:http` / `node:https`.
- Two transport instances are built from the same `transportOptions`, differing only in their default timeout (unary vs. stream).
- No interceptors, no compression (`acceptCompression: []`, `sendCompression: null`), default read/write limits.
- Binary by default: unary `application/proto`, streams `application/connect+proto`. `ClientOptions.useBinaryFormat: false` switches to Connect JSON (`application/json`, `application/connect+json`). The signature covers the bytes as sent in either format.
- Why this split: an HTTP client (`UniversalClientFn`) gets the body as an async iterable of the raw bytes connect-es built, one envelope per chunk for a stream, so the signing client can take the first envelope, sign it, and pass it and the rest through untouched. `createNodeHttpClient` writes such a body chunk by chunk as it comes. connect-node's own `createConnectTransport` cannot be used: it always builds its own HTTP client, and its interceptors see messages, not bytes. `createNodeHttpClient` is `@private` connect-node API, hence the exact pin ([Dependencies](#dependencies)).

## Which bytes are signed

`isEnveloped` looks at the media type of `Content-Type` (lowercased, parameters dropped). The rule is the same in every SDK:

| Content type | Signed bytes |
|---|---|
| `application/connect+*`, `application/grpc`, `application/grpc+*` | the first envelope exactly as sent: `flags (1) \|\| uint32be(length) \|\| payload`, the payload compressed if flag bit 0 is set (this client never compresses) |
| anything else (`application/proto`, `application/json`, `application/grpc-web+*`, …) | the whole body |

`sign.ts` computes `Keccak256(bytes || uint64le(Date.now()))`; the signing client sets `X-Signature`, `X-Public-Key` and `X-Signature-Timestamp` on the request, replacing any the caller set.

### Unary

The body is read whole (`readAll`; connect-es sends it as one chunk, but any number are joined), signed, and passed on as one chunk with `Content-Length` set to its length. A request without a body is signed over empty bytes and sent without one.

### Client and server streams

1. The client waits for the first body chunk. For a client stream this is the caller's first message, so the wait is raced against the call's signal (`untilAborted`): the stream timeout or an abort ends it, with the signal's reason (connect-es aborts with a `ConnectError`, `deadline_exceeded` on timeout). Nothing has been sent at that point.
2. connect-es yields one complete envelope per chunk (`transformJoinEnvelopes`), so the first chunk is the first envelope. `requireOneEnvelope` checks it rather than trusting it: a chunk whose length is not `5 + uint32be(length)` fails the call with `internal`, and nothing is sent. A change in connect-es's framing must never lead to signing other bytes.
3. The first envelope is signed and the request is sent at once. Its body (`firstThenRest`) yields the first envelope, then pulls later chunks from connect-es's iterator one at a time as connect-node writes them (chunked transfer encoding). Nothing is buffered; later messages are not covered by the signature. `firstThenRest` is a plain iterator, not an async generator, so that connect-node's `return()` / `throw()` reach connect-es's iterator at once instead of queuing behind a pending `next()`.

A server stream's single request message is its first envelope. A client stream closed before its first message is signed over empty bytes and sent with an empty body; the network rejects it (same rule in every SDK). If anything fails before the request is sent, the body iterator is closed.

### Bidirectional streams

`createClient` rejects a `bidi_streaming` method with `ConnectError` `unimplemented` before anything is sent. That is a policy: the network does not accept bidi streams (#370), which every SDK enforces. Over HTTP/1.1 a Connect bidi stream could not interleave its two directions anyway.

## HTTP details

- No redirect is followed: a 3xx fails the call instead of re-sending a signed request elsewhere.
- No `Accept-Encoding` is sent, so connect-go does not compress unary responses and nothing is decoded on the way; Connect compression is off in both directions.
- A unary body has `Content-Length`; a streaming body is sent with `Transfer-Encoding: chunked`.
- `createSigningHttpClient(signer, httpClient)` takes the HTTP client as a second argument for tests; it defaults to `createNodeHttpClient({httpVersion: "1.1"})`.

## Timeouts

`ClientOptions.unaryTimeoutMs` and `streamTimeoutMs` are the transports' `defaultTimeoutMs`: each call's deadline, sent as `Connect-Timeout-Ms` and enforced locally. A call's own `timeoutMs` overrides them.

| Option | Default | `0` | Negative or `NaN` |
|---|---|---|---|
| `unaryTimeoutMs` | 15 000 ms (as in the other SDKs) | no timeout | `RangeError` from `createClient` |
| `streamTimeoutMs` | none: an upload or download takes as long as it takes | no timeout | `RangeError` from `createClient` |

The stream timeout runs from the start of the call, so it also covers the wait for the first message.

## Dependencies

`@connectrpc/connect` and `@connectrpc/connect-node` are pinned to one exact version in `node/sdk/package.json` (no `^`) and bumped together (connect-node peer-depends on connect's exact version). They are on the signing path: the client signs the bodies and envelopes connect-es builds, `transportOptions` fills its `@private` `CommonTransportOptions`, and the signed request is sent by connect-node's `@private` `createNodeHttpClient`; neither follows semver. Every field of `CommonTransportOptions` is spelled out, so an upstream change to that type fails `tsc`; a change in how `createNodeHttpClient` writes the body is caught by the no-buffering, wire and cross tests. A bump is a Tier 3 update ([dependency-update skill](../../.claude/skills/dependency-update/SKILL.md)): re-run `streaming.test.ts` (with its `transportOptions` guard), `unary_wire.test.ts`, `cross_stream.test.ts` and the `crypto.test.ts` vectors.

## Tests (`node/sdk/test/`)

- `streaming.test.ts`: a local server for `test.v1.StreamTest` whose verifier works like the network's: it reads a stream's first envelope from the raw body, verifies the signature over it (a unary body over all of it), answers 401 on failure, and only then hands the body to the handler. Covers client and server streams signed over the first envelope, no buffering (the caller's stream yields message n+1 only after the server has read message n), a 64 KiB first message, an empty stream, unsigned and whole-body-signed streams (both rejected), unary vs. stream content types, Connect JSON, per-transport timeouts, the defaults (15 s unary, none for streams), `0` as none, negative values refused, the stream timeout ending the wait for a first message, bidi refused with nothing sent, and the `transportOptions` guard.
- `cross_stream.test.ts`: client and server streams against `cross_test/go_helper serve`, checked through the verdict the helper logs to stderr (`<path> verified over the first envelope`, `<path> rejected: <reason>`). No-buffering: m2 is produced only after the helper logged m1 as verified. Also a 256 KiB first message, Connect JSON, and rejections (unsigned, signed over the whole body, stale timestamp, empty stream). Fails instead of skipping in CI when the helper binary is missing.
- `unary_wire.test.ts`: the golden unary request, recorded by an HTTP client injected into `createSigningHttpClient`. Before connect-web was removed, the request it passed to fetch for a health check (Connect JSON, `{"service":"grpc.health.v1.Health"}`, `connect-protocol-version`, `connect-timeout-ms`, a call header, no `User-Agent`) was recorded; fetch added `Content-Length` below it. The SDK now sends the same Connect request in binary (`application/proto`, plus a `connect-es/…` `User-Agent`), signed over the body as sent, as one chunk with its `Content-Length`; with `useBinaryFormat: false` it sends connect-web's request exactly, `Content-Length` included. Also: which content types are signed whole and which are treated as enveloped (a first chunk of two envelopes fails the call), a request without a body, `return()` / `throw()` on a stream body reaching connect-es's iterator, and, against a real `node:http` server, what arrives: a unary call with `Content-Length` and no `Transfer-Encoding`, a client stream chunked, neither with `Accept-Encoding`, and a redirect not followed.
- `crypto.test.ts`: the `stream_signing_cases` vectors from `cross_test/test_vectors.json`: signed bytes, digest and signature for each; for the `first_envelope` cases, the signing HTTP client given one envelope per chunk hands the injected HTTP client the vector signature and the same chunks, the first being the signed envelope; a first chunk that is not exactly one envelope fails with `internal` and sends nothing.
- `stream_helpers.ts`: `StreamTest` described by hand from `cross_test/stream_test.proto`, plus `Unary` and `Bidi` methods that only the Node tests use, and `bufferingHttpClient` (on `createNodeHttpClient`), which signs a stream over its whole body (or not at all) to produce requests the verifiers must reject.
