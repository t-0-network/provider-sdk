# Go SDK: signing and streaming calls

How the Go network client (`go/network`) signs requests, and what that means for client-streaming
(upload) and server-streaming (download) calls. The signature scheme itself is in the root
[`CLAUDE.md`](../../CLAUDE.md#signature-protocol); the shared vectors in
[`cross_test/README.md`](../../cross_test/README.md).

`NewServiceClient` builds an `http.Client` whose transport is a `SigningTransport` and adds two
connect-go interceptors, `rejectBidi` and `callTimeouts`. A client built by hand on
`SigningTransport` gets the signing but neither interceptor: no per-call timeouts, and nothing
stops a bidirectional call.

## What is signed

`SigningTransport` decides from the request's media type (the `Content-Type` without parameters,
trimmed and lower-cased; `isEnveloped` in `signing_transport.go`):

| Media type | Requests | Signed bytes |
|---|---|---|
| `application/connect+*` | Connect client and server streams | First envelope |
| `application/grpc`, `application/grpc+*` | Every gRPC call, unary included | First envelope |
| anything else | Connect unary (`application/proto`, `application/json`), gRPC-Web (`application/grpc-web*`) | Whole body |

- The first envelope is taken **exactly as sent**: `flags (1) || uint32be(length) || payload`, with
  the compressed payload when flag bit 0 is set (`connect.WithSendGzip()`). Later envelopes are
  not covered.
- A gRPC unary body is a single envelope, so for it "first envelope" is the whole body.
- The codec does not matter: `application/connect+json` (`connect.WithProtoJSON()`) is enveloped
  and signed exactly like `connect+proto`.
- The Go client signs below the gRPC framer, so over gRPC it covers the envelope with its 5-byte
  prefix, never the bare payload (vector `covers: first_envelope`).
- Digest: `Keccak256(signed || uint64le(timestamp_ms))`; headers `X-Public-Key`, `X-Signature`,
  `X-Signature-Timestamp`.

The caller's `*http.Request` is never modified, as `http.RoundTripper` requires: the transport
signs a clone.

- Whole body: the body is read to its end and closed; the clone gets those bytes, with
  `ContentLength` and a `GetBody` that replays them.
- First envelope: the clone's body is the envelope followed by the rest of the original body.
  `ContentLength` and `GetBody` are left as they were, since the bytes sent are unchanged.

A transport-level retry below `SigningTransport` replays the same bytes under the same signature
and timestamp; see `WithHTTPTransport`.

## When the request is sent

For an enveloped request the transport reads exactly one envelope, signs it, and sends the request
at once; the rest of the body is streamed as the caller produces it, never buffered.

- **Timestamp.** It is taken after the first envelope is read, just before sending. The network
  checks it when the headers arrive, before it reads the body.
- **Client streams.** connect-go feeds the request body through a pipe, so the request goes out on
  the first `Send`. Send a message (or call `CloseAndReceive`) before waiting on anything from the
  server.
- **Empty client stream.** A stream closed before its first message has no envelope: it is signed
  over empty bytes and sent, and the network rejects it. Same in every SDK.
- **Waiting.** The wait for the first message ends with the request's context (the caller's, or
  the `WithStreamTimeout` deadline): the body is closed, `RoundTrip` returns the context's error,
  and nothing is sent.
- **Large first message.** The buffer is preallocated from the length prefix up to 64 KiB
  (`maxPrealloc`) and grows as the rest arrives.

## Timeouts

| Option | Applies to | Default | Invalid |
|---|---|---|---|
| `WithTimeout` | Unary calls | 15 s | `<= 0` → `ErrInvalidTimeOut` |
| `WithStreamTimeout` | Client- and server-streaming calls | `0`, none | `< 0` → `ErrInvalidStreamTimeout` |

- Each timeout is the call's context deadline, set by the `callTimeouts` interceptor. connect-go
  enforces it and sends it to the server (`Connect-Timeout-Ms` or `grpc-timeout`). A deadline on
  the caller's context still applies; the earlier one wins.
- A stream's deadline covers the whole call, from the wait for the first message to the end of the
  response, and is released on `CloseResponse`. Streams have none by default because an upload or
  download takes as long as it takes: bound one with its context or with `WithStreamTimeout`.
- The client sets no `http.Client.Timeout`: it would cap every stream at the unary timeout.
- The timeouts live in an interceptor, not in the transport, because only connect-go knows a call's
  stream type: a gRPC unary request and a gRPC server-streaming request look the same on the wire.

## Bidirectional streams

`rejectBidi` replaces a bidirectional call's connection with one that is never opened: `Send` and
`Receive` fail with `connect.CodeUnimplemented`, `CloseRequest` and `CloseResponse` return nil,
and nothing reaches the server. Every SDK's client does the same.

This is a policy: the network does not accept bidirectional streams (#370). It is not a limit of
the signing, which would treat a bidi request like a client stream. If it is ever lifted, note that
the request goes out only with the first message, so a caller must send before it waits to receive.

## Errors

- **Truncated first envelope** (the body ends inside the prefix or the payload):
  `connect.CodeInvalidArgument`, "streaming request ends inside its first message". The error has
  a Connect code on purpose, because connect-go reports an uncoded `RoundTrip` error as
  `unavailable`, which looks retryable. It must not wrap `io.EOF`: connect-go replaces such an error
  with `io.ErrUnexpectedEOF` and the code is lost.
- **Other read errors** are wrapped with `%w`, so an error that already has a Connect code keeps it.
- **Signing errors** are returned as `signing request body: ...`.
- Whenever `RoundTrip` fails before sending, the request body is closed.

## Tests

`go/network/stream_test.go` runs the real client against an in-process `test.v1.StreamTest`
server over TLS + HTTP/2 whose verifier works like the network's: an enveloped request is checked
over its first envelope (for gRPC also the bare payload) before the handler reads on, anything else
over its body. Every case runs over `connect`, `connect-json`, `connect-gzip`, `grpc` and
`grpc-gzip`:

- client and server streams verify over the first envelope, compressed flag included;
- no buffering: the server receives message 1 and 2 before the stream is closed;
- a first message of 64 KiB of random data (larger than several HTTP/2 frames after gzip);
- an empty client stream is sent and rejected;
- unary calls on the same client are signed over the body (the single envelope for gRPC);
- timeouts: unary vs. stream, none for streams by default, a stream timeout that expires before
  the first message sends nothing, and the deadline headers that reach the server;
- bidirectional calls fail with `Unimplemented` and send nothing.

The same file unit-tests the transport: `isEnveloped`, empty bodies of every kind signed over empty
bytes, truncated envelopes, read errors keeping their code and closing the body, the caller's
request left unchanged, the context ending the wait, the timestamp taken after the first message,
and the `stream_signing_cases` vectors (the `first_envelope` ones; Go never produces
`first_payload`). `go/crypto/cross_test.go` checks every vector's `signed_hex`, hash and signature.

Against the shared helper, `cross_test/go_helper/stream_test.go` pins the verifier the other SDKs
test against and runs this client through it over Connect, Connect JSON and gRPC (h2c). Go CI also
runs `go_helper call-client-stream` / `call-server-stream` against `go_helper serve`. See
[`CROSS_LANGUAGE_TESTING.md`](../CROSS_LANGUAGE_TESTING.md).

```bash
cd go && go vet ./... && go test -race ./network/ ./crypto/
cd cross_test/go_helper && go test ./...
```
