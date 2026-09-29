# C# SDK — Streaming, Signing and Deadlines

How the C# client signs gRPC requests (unary and streaming), sets deadlines, and refuses
bidirectional streams. Cross-SDK rules: the root [`CLAUDE.md`](../../CLAUDE.md) ("Streaming RPCs").

Code: `csharp/sdk/T0.ProviderSdk/Network/` — `SigningDelegatingHandler`, `FirstFrameThenPipeContent`,
`DefaultDeadlineInterceptor`, `NetworkClient`, `NetworkClientOptions`.

## What is signed

```
digest = Keccak256(signed_bytes || LE_uint64(timestamp_ms))
```

`SigningDelegatingHandler` picks `signed_bytes` from the request's content type:

| Content type | `signed_bytes` |
|---|---|
| `application/grpc`, `application/grpc+*` | The first request frame exactly as sent: `flags(1) \|\| uint32be(length) \|\| payload`. With flag bit 0 set, the compressed payload as sent. |
| Anything else (`application/proto`, `application/json`, `application/connect+*`, `application/grpc-web*`) | The whole body. |

- The handler sits **below the gRPC framer** in the `HttpClient` pipeline, so it sees framed bytes. That is
  why it signs the envelope with its 5-byte prefix, not the bare message (the Java SDK signs above the framer
  and covers the payload only; the network accepts both over gRPC).
- Unary and server-streaming requests are a single frame, so for them the first frame is the whole body.
- A client stream completed before its first message is signed over **empty bytes** and sent. The network
  rejects it.
- gRPC-Web is excluded on purpose (`application/grpc-web` is not `application/grpc+*`): it is signed whole.
- The server checks the timestamp when the headers arrive, before it reads the body, and verifies over the
  first message only. A signer that buffers the whole stream would sign too late and over the wrong bytes.

## Sending: `FirstFrameThenPipeContent`

1. `FirstFrameThenPipeContent.CreateAsync` starts the original content (grpc-dotnet's push-style content)
   writing into a `System.IO.Pipelines.Pipe` in the background and reads exactly one frame from it.
   Bytes after that frame stay in the pipe.
2. The handler signs `FirstFrame`, replaces `request.Content` and calls the inner handler at once.
3. `SerializeToStreamAsync` writes the first frame, flushes, then forwards the rest of the pipe chunk by
   chunk, flushing each, until the source completes. Nothing is buffered.

Details:

- **A client stream goes out only once its first message is written** (or the stream is completed). Write
  the first message before awaiting `ResponseHeadersAsync`, or the call waits until its deadline.
- **Large first frames**: bytes are consumed as they are copied into the frame buffer, so a frame larger
  than the pipe's pause threshold (64 KiB by default) does not stall the writer.
- **Headers**: copied from the original content except `Content-Length`, which `TryComputeLength` reports
  when the source knew it (e.g. `ByteArrayContent`); otherwise the length is unknown.
- **Re-send**: `SocketsHttpHandler` serializes content again when an HTTP/2 stream is refused. That works
  while nothing after the first frame has been forwarded. Once later frames were forwarded, a second send
  throws `InvalidOperationException`.

### Error paths

| Situation | Result |
|---|---|
| Source ends inside the first frame's prefix or payload | `InvalidOperationException` from `SendAsync`; nothing sent |
| First frame's length exceeds `Array.MaxLength - 5` (e.g. `0xFFFFFFFF`) | `InvalidOperationException`; nothing sent (fails instead of waiting for 4 GiB) |
| Source faults before the first frame | The fault surfaces from `SendAsync`; nothing sent |
| Source faults after the first frame | The fault surfaces from sending the body; the request is already signed and sent |
| Inner handler fails before reading the body (e.g. connection refused) | The handler calls `Abort`, which completes the pipe reader with the exception, so the source's pending and later writes fail instead of hanging on a full pipe |
| Transport fails while forwarding the rest | Same: the pipe reader is completed with that exception |
| Cancelled while waiting for the first frame | `OperationCanceledException`; the source's writes fail with it; nothing sent |
| Content disposed before sending the rest | `Abort` with `ObjectDisposedException` |

## Signature headers are replaced

`X-Public-Key`, `X-Signature` and `X-Signature-Timestamp` are set with `Remove` then
`TryAddWithoutValidation`. `TryAddWithoutValidation` appends: a value already on the request (e.g. added as
gRPC call metadata, with lower-case names) would become a second value and make the request unverifiable.

## Deadlines

- **`HttpClient.Timeout` is infinite.** `NetworkClient.Create` sets `Timeout.InfiniteTimeSpan`: that timeout
  only runs until the response headers, which for a client stream is the whole upload. Timeouts are gRPC
  deadlines instead.
- **`DefaultDeadlineInterceptor`** sets `CallOptions.Deadline` on a call that has none:

  | Call type | Option | Default |
  |---|---|---|
  | Unary (async and blocking) | `NetworkClientOptions.Timeout` | 15 s |
  | Client streaming, server streaming | `NetworkClientOptions.StreamTimeout` | `null` (none) |

  - A deadline set on the call is kept. `Timeout.InfiniteTimeSpan` means none. Zero or negative values throw
    `ArgumentOutOfRangeException` from the constructor, which reads the options once.
  - The deadline bounds the whole call, from the start of the request to the end of the response, and is
    sent to the server as `grpc-timeout`. For a client stream it also bounds the wait for the first message.
- **Where it applies**: `NetworkClient.CreateNetworkServiceClient` and
  `CreatePaymentIntentNetworkServiceClient` install it (with `RequestValidationInterceptor`). A raw
  `GrpcChannel` from `Create` / `CreateChannel` has no interceptor: set `CallOptions.Deadline` per call or
  wrap it with `channel.Intercept(new DefaultDeadlineInterceptor(options))`.

## Bidirectional streams are refused

`DefaultDeadlineInterceptor` throws `RpcException(StatusCode.Unimplemented, "bidirectional streams are not
supported")` for a duplex call before anything is sent. This is a **policy**, because the network does not
accept bidirectional streams (#370), not a limit of the signing: the handler would sign a duplex call's
first frame like any other. Every SDK client refuses them the same way. A raw channel refuses them only when
wrapped with the interceptor.

## Testing

All in `csharp/sdk/T0.ProviderSdk.Tests/`.

**`Network/SigningDelegatingHandlerStreamTests.cs`**: the handler over fakes from `Network/StreamingFakes.cs`:
`PushContent` (writes frames when the test says so, like grpc-dotnet), `RecordingHandler` / `RecordingStream`
(the inner transport; a test can wait for a body length), `FixedTimeProvider`. Covers:

- a client stream reaches the transport, signed over frame 1, before frame 2 is written; it cannot be re-sent
- a 100 000-byte first message; a first frame split inside its prefix and its payload; an empty stream
- re-send of a single-frame body; a known `Content-Length` kept; non-gRPC content types signed whole
- source faults before and after the first frame; truncated prefix or payload; an oversize length
- transport failure and cancellation make the source's pending writes fail
- signature headers already on the request are replaced

**`Network/DefaultDeadlineInterceptorTests.cs`**: the interceptor against a capturing `CallInvoker` (per call
type, call deadline kept, infinite and invalid values, duplex rejected before the invoker), the channel's
infinite `HttpClient.Timeout`, a client stream with no first message failing at the stream deadline, and a
Kestrel HTTP/2 server recording `grpc-timeout`: a helper-built client sends ~15 s, a raw channel none;
`StreamTimeout` is sent only when set.

**`Crypto/CrossTestVectors.cs`** (`StreamSigningCases_ShouldMatchVectorBytes`): `stream_signing_cases` from
`cross_test/test_vectors.json` — signed bytes, digest and signature. `first_envelope` includes the 5-byte
prefix, `first_payload` does not. The `first_envelope` cases also run through the handler; Connect cases are
sent as `application/grpc`, since a Connect envelope has the gRPC frame's layout.

**`CrossTest/CrossServerTests.cs`**: C# → Go against `go_helper serve`, which serves `test.v1.StreamTest`
(`cross_test/stream_test.proto`; the C# method descriptors are built by hand on `StringValue`). The helper
verifies each request over its first frame and logs `<path> verified over the first envelope` or
`<path> rejected: <reason>` to stderr. `GoStreamServer` reads the helper's output asynchronously, so tests
wait for a log line. Cases:

- client stream of three messages; server stream of three replies
- no buffering: message 2 is written only after the helper logged message 1 as verified
- a 256 KiB incompressible first message; a gzip-compressed first message (signed compressed)
- rejected with `Unauthenticated`: an empty stream, a stream signed over its whole body
  (`WholeBodySigningHandler`), an unsigned one, a stale timestamp (2 min old), an untrusted key

```bash
cd cross_test/go_helper && go build -o go_helper .   # the helper, once
cd csharp && dotnet test                              # everything
cd csharp && dotnet test --filter "CrossServerTests"  # C# <-> Go only
```

In CI the cross tests fail, not skip, when the helper binary is missing.
