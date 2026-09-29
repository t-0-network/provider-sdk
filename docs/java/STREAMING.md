# Streaming Calls and Deadlines (Java client)

How the Java SDK's `NetworkClient` signs client- and server-streaming calls and which deadlines its calls get. The code is `NetworkClient.SigningClientInterceptor` and `NetworkClient.DefaultDeadlineInterceptor` in [`NetworkClient.java`](../../java/sdk/src/main/java/network/t0/sdk/network/NetworkClient.java). The cross-SDK protocol is in the root [`CLAUDE.md`](../../CLAUDE.md) ("Streaming RPCs — only the first message is signed").

## What is signed

| Call type | Signed bytes |
|-----------|--------------|
| Unary | the request (unchanged) |
| Server streaming | the single request message |
| Client streaming | the first message only; later messages are sent unsigned |
| Client stream closed before any message | empty bytes; the call is still sent and the network rejects it (`no first message`) |
| Bidirectional streaming | not supported, see [below](#bidirectional-streams) |

```
digest = Keccak256(first_message_bytes || timestamp_le_u64)
```

- **Unframed.** The interceptor sits above the gRPC framer, so `first_message_bytes` is the marshalled message **without** its 5-byte gRPC prefix. The protocol's canonical form is the first envelope; over gRPC the network also accepts the bare first payload, the same fallback that makes Java's unary calls work. See [`SIGNATURE_VERIFICATION.md`](SIGNATURE_VERIFICATION.md).
- **Serialized once.** Each message is marshalled once and those exact bytes are sent (`ByteArrayMarshaller`), so the signed bytes are the sent bytes.
- **Headers once.** `X-Signature`, `X-Public-Key` and `X-Signature-Timestamp` are set once, before the call starts, replacing any value the caller's `Metadata` already had (`Metadata.put` appends). Later messages never touch the headers: the `Metadata` handed to `start` belongs to the transport, which may serialize it later.
- **Provider side unchanged.** `SignatureVerificationInterceptor` verifies every inbound message; providers serve no streams.

## Deferred start

`start()` only stores the listener and headers. The underlying call starts on the first `sendMessage()` (or on `halfClose()` for an empty stream), because the signature headers must be complete before the call starts and they depend on the first message.

- The first message goes out as soon as it is sent. The stream is never buffered to sign it; the server checks the timestamp when the headers arrive.
- The underlying call starts exactly once. `cancel()` may come from another thread while the first `sendMessage()` or `halfClose()` is starting the call, so the start is claimed under a lock: the first of them to find the call unstarted claims it (`starting`), signs the headers in that same critical section if it carries the first message, and calls the underlying `start()` outside the lock. The others wait on the lock until the call has started, then go to the started call; they neither start it again nor touch its headers. An interrupt while waiting restores the flag and fails with `CANCELLED`.
- `request(n)` before the start is buffered and passed on when the call starts. `request()` may come from any thread and never waits: while the call is starting it is buffered too.
- If the marshaller throws on the first message, the exception reaches the caller and nothing is started or signed; a stub then cancels the call (see [ending before the first message](#ending-before-the-first-message)).

## isReady and onReady

- Before the first message `isReady()` returns `true`. The call cannot become ready before it starts, and it cannot start without the first message, so readiness-gated senders (`BlockingClientCall.write`, `while (requestStream.isReady())` loops) must be let through to send it. The underlying call's `isReady()` is not asked before start (grpc-java's `ClientCallImpl` throws there).
- After the start `isReady()` reflects the underlying call.
- `start()` gives the listener one `onReady()` before the first message, on the call's executor (the `CallOptions` executor, else a shared daemon pool, `t0-network-client-callback`), unless the first message was sent by then. Without it, a sender driven only by `onReady` (grpc-java's `ClientCall` example, `setOnReadyHandler` loops) would wait for a callback that only its own first message can trigger. Later `onReady()` calls come from the started call.
- The listener's callbacks run one at a time: this first `onReady()` and the started call's callbacks go through one `SynchronizationContext` per call, so a callback of the started call that arrives while that `onReady()` still runs (it has just sent the first message) is delivered after it returns. A callback that throws cancels the call with that cause, as `ClientCallImpl` does.

## Ending before the first message

An unstarted grpc-java call never notifies its listener, and grpc enforces a call's deadline and its context only from the start. So each of these starts the underlying call, with nothing signed, when it comes before the first message:

- `cancel()` after `start()`: the call is started and cancelled at once; the listener gets `onClose(CANCELLED)` on the call's executor, as for any cancelled call.
- The call's deadline (from `CallOptions`, e.g. the stream timeout): a timer armed in `start()` (one shared daemon thread, `t0-network-client-deadline`) starts the call when the deadline passes. grpc-java fails a call started after its deadline with `DEADLINE_EXCEEDED` without opening a stream, so nothing is sent.
- The context the call was created in being cancelled (its deadline included): a listener added in `start()` starts the call, and grpc-java closes it with the context's status (`CANCELLED`, or `DEADLINE_EXCEEDED` for a context deadline), again without a stream.

Once the call has started, the timer is cancelled and the context listener removed: from then on grpc enforces both. `cancel()` before `start()` has no listener to notify; the cancellation just reaches the unstarted underlying call, which the `ClientCall` contract allows.

## Bidirectional streams

Refused locally: `interceptCall` hands out a call that closes its listener with `UNIMPLEMENTED` ("bidirectional streams are not supported") on `start()`, as grpc-java calls that fail to start do. No underlying call is created and nothing is sent; its other methods are no-ops and `isReady()` is `false`.

This is a policy, because the network does not accept bidi streams (#370), not a limit of the signing. It also fails fast: with the deferred start, a bidi caller that waits for a response before sending would hang. Every SDK's client refuses bidi with `unimplemented` before anything is sent.

## Deadlines

`DefaultDeadlineInterceptor` gives each call that has no deadline one when the call is created (per call, never once per stored stub). It applies to every call on the client's channel: stub calls and calls made on `getChannel()`.

| Call type | Default deadline | Set with |
|-----------|------------------|----------|
| Unary | 15 s (`DEFAULT_TIMEOUT_SECONDS`) | `create(..., int timeoutSeconds)` or `create(..., Duration unaryTimeout, Duration streamTimeout)` |
| Client, server, bidi streaming, `UNKNOWN` | none | `create(..., Duration unaryTimeout, Duration streamTimeout)` |

- A stream can run as long as an upload or a download takes, so streams get no deadline unless one is set. `streamTimeout` `null` or `Duration.ZERO` means none.
- A deadline the call already has is kept: `stub(timeout, unit)`, `withDeadlineAfter(...)` on a stub, or one set in `CallOptions`.
- The deadline is sent to the server as `grpc-timeout`.
- Validation: `unaryTimeout` must be positive (so `create(..., 0)` throws `IllegalArgumentException`) and `streamTimeout` must not be negative. It runs before the channel is built, so a bad value leaves nothing to shut down.
- A `Duration` too large for nanoseconds saturates to `Long.MAX_VALUE`; grpc-java's `Deadline` clamps it further.
- Order: `ClientInterceptors.intercept(channel, signing, deadlines)`. The last listed interceptor runs first, so the deadline is in the `CallOptions` before the signing interceptor creates the underlying call.

```java
// 15 s for unary calls, 10 min for streaming calls
AsyncNetworkClient.create(endpoint, signer, NetworkServiceGrpc::newStub,
        Duration.ofSeconds(15), Duration.ofMinutes(10));

// One stub with its own deadline, for unary and streaming calls alike
client.stub(2, TimeUnit.MINUTES).someCall(request);
```

## How it is tested

Local (no Go helper), in `java/sdk/src/test/java/network/t0/sdk/`:

- `network/SigningClientInterceptorStreamingTest`: the interceptor over a recording fake `ClientCall` that behaves like `ClientCallImpl` where it matters (throws on `isReady()` before start; only a started call reports a cancellation). It checks the signed bytes (the first message, not a later one, not their concatenation, not the framed message), the order of events reaching the transport, headers set once, the empty stream, bidi refusal, `isReady()`/`cancel()` before start, a failing marshaller, unary unchanged, and that the client emits the `grpc-client-stream-unframed` vector's signature. The first `onReady()` (a readiness-driven sender's first message starts the call; none once the first message was sent; a callback of the started call during it waits for it; a throwing callback cancels the call). A deadline or a context cancellation before the first message starts the call unsigned, and neither starts it again after the first message. Concurrent start: the fake's `start()` blocks on a latch and refuses a second call, as `ClientCallImpl` does; a `cancel()` racing the first `sendMessage()` (and the reverse) leaves one start, signed only when the message started it, and the other method reaches the fake only after it.
- `network/DefaultDeadlineInterceptorTest`: the deadline per call type, an existing deadline kept, per-call computation, validation; and through the clients against an in-process Netty server: the `grpc-timeout` the server sees (15 s for unary, none for a server stream), and `DEADLINE_EXCEEDED` from the unary and the stream timeout.
- `crypto/CrossVectorTest.streamSigningCases_shouldMatchVectorBytes`: signed bytes, digest and signature of every `stream_signing_cases` vector in `cross_test/test_vectors.json`.

Cross-language, `integration/CrossServerTests` (Java client → `go_helper serve`, gRPC over h2c):

- The helper serves `test.v1.StreamTest` ([`cross_test/stream_test.proto`](../../cross_test/stream_test.proto)) behind a verifier that checks the signature over the first message only and answers `UNAUTHENTICATED` otherwise. The methods are hand-built `MethodDescriptor`s called on `client.getChannel()`, so the SDK's interceptors apply.
- The client only sees `UNAUTHENTICATED`, whatever the reason, so the tests read the helper's log (stdout and stderr merged, drained by a daemon thread) for its verdict: `<path> verified over the first payload` (what Java signs; `... envelope` for the framed form) or `<path> rejected: <reason>`.
- Cases: a client stream of three messages; no buffering (message 2 is sent only after the helper logged message 1 as verified); a 256 KiB random first message (above HTTP/2's 64 KiB initial window, incompressible); `BlockingClientCall.write`; a sender that sends only on `onReady`, its first message included; a server stream; and refusals of an unknown key, an empty stream (`no first message`), an unsigned stream, a signature over the whole stream (hand-built headers over the first message pass as the control), and a stale timestamp (signer clock two minutes behind). A stream timeout of 50 ms on a client stream that sends nothing ends the call with `DEADLINE_EXCEEDED`, and the helper logs nothing for it.
- `SigningInterceptors.withClock` (test sources, `network.t0.sdk.network`) hands the package-private interceptor with a chosen clock to tests in other packages.
- In CI the tests fail, not skip, if the helper binary is missing.

```bash
cd cross_test/go_helper && go build -o go_helper .
cd java && ./gradlew test --tests "*.CrossServerTests"
```
