# Streaming calls

These rules hold for client and server streaming calls in the network client of every SDK in this
repo. How the Go SDK's server verifies them: [Server side](#server-side-go-sdk). The signature scheme itself is in the root [`CLAUDE.md`](../CLAUDE.md#signature-protocol), and
the shared test vectors are in [`cross_test/README.md`](../cross_test/README.md).

## What is signed

A streaming call is signed over its first request envelope, taken exactly as sent:
`flags (1) || uint32be(length) || payload`. Later messages are sent unsigned.

- The wire format does not change the rule. `application/connect+json` is signed like
  `application/connect+proto`.
- A client that signs above the gRPC framer covers the first payload without its 5-byte prefix. The
  network accepts both forms over gRPC.

## When the request is sent

The network checks the timestamp when the headers arrive, before it reads the body. So the client
signs as soon as it has the first message and sends the request at once. The rest of the stream is
sent as the caller produces it and is never buffered.

- A client stream is sent with its first message. Send a message, or close the stream, before
  waiting for anything from the server.
- A client stream closed before its first message is signed over empty bytes and sent. The network
  rejects it.
- A call that is cancelled or times out before its first message sends nothing. Java starts the
  call with its first message, so it reports a deadline that passed before then only when the first
  message or the end of the stream comes.
- If the caller's message source fails, or the caller cancels, after the first message was sent,
  the call fails for the caller and the request is aborted. The server never sees a normal end of
  the stream, so it never handles a partial upload as complete.
- A signature header that the caller set is replaced, never added to.
- A first message whose length prefix promises more bytes than arrive fails with `invalid argument`
  and the message "streaming request ends inside its first message". Other read errors keep their
  own code.

## Server side (Go SDK)

Only the Go SDK verifies streaming calls on the server (rule V9 in
[`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md)). A handler built with `provider.Handler` gets the same
check the network makes:

- The headers are checked before the body is read. Then the middleware reads the first envelope
  only and verifies the signature over it, or over gRPC also over its payload without the prefix.
- The rest of the stream reaches the handler as the caller sends it. Nothing is buffered, and the
  stream as a whole has no size limit. Each message is limited by `WithMaxBodySize`, the first one
  before its signature is verified.
- A rejected stream fails before its handler runs, under the factory condition in rule V8, with the
  codes of [`CROSS_SDK_RULES.md`](CROSS_SDK_RULES.md#error-codes). A stream without a whole first
  message is `unauthenticated` ("no first message", "truncated first message").
- `provider.SignatureVerification(ctx)` tells a handler what was signed: `envelope` or `payload`.

## Bidirectional streams

Bidirectional streams fail with `unimplemented` and the message "bidirectional streams are not
supported", before anything is sent. The network does not accept them.

## Stream timeout

Client and server streaming calls use the stream timeout, 5 minutes by default, in place of the
unary timeout.

- It is the deadline of the whole call, including the wait for the first message, and it is sent to
  the server. A stream that runs longer ends with `deadline exceeded` unless the caller passes a
  longer stream timeout. In Java the call starts with its first message, so a client stream that
  never sends one is not ended by the deadline.
- A deadline that the caller sets on a call replaces it, whether it is shorter or longer.
- A synchronous client (Python's sync client) cannot interrupt a request source that blocks before
  its first message: the call waits for it and, if the deadline has passed by then, sends nothing and
  fails with `deadline exceeded`. After the first message the deadline ends the call on time.

| | Go | Node | Python | Java | C# |
|---|---|---|---|---|---|
| Stream timeout | `WithStreamTimeout` | `streamTimeoutMs` | `stream_timeout` (seconds) | `streamTimeout` | `StreamTimeout` |
| Deadline of one call | context deadline | `timeoutMs` in call options | `timeout_ms` | `stub(timeout, unit)` or a `Context` deadline | `deadline` in call options |
