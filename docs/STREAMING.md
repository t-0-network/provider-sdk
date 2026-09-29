# Signing, streaming calls and timeouts

These rules hold for the network client of every SDK in this repo. The signature scheme itself is in
the root [`CLAUDE.md`](../CLAUDE.md#signature-protocol), and the shared test vectors are in
[`cross_test/README.md`](../cross_test/README.md).

## What is signed

What a call is signed over depends on its kind, which the content type of the request shows.

| Content type | Calls | Signed bytes |
|---|---|---|
| `application/connect+*` | Connect client and server streams | The first envelope |
| `application/grpc`, `application/grpc+*` | Every gRPC call, unary included | The first envelope |
| anything else | Connect unary calls (`application/proto`, `application/json`) | The whole body |

- The first envelope is taken exactly as sent: `flags (1) || uint32be(length) || payload`. Later
  messages are sent unsigned.
- A gRPC unary body is a single envelope, so for it the first envelope is the whole body.
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
- A call that is cancelled or times out before its first message sends nothing.
- A signature header that the caller set is replaced, never added to.
- A first message whose length prefix promises more bytes than arrive fails with `invalid argument`
  and the message "streaming request ends inside its first message". Other read errors keep their
  own code.
- A length prefix alone never makes the client allocate more than 64 KiB. Beyond that the buffer
  grows with the bytes that actually arrive.

## Calls that are refused

- Bidirectional streams fail with `unimplemented` and the message "bidirectional streams are not
  supported", before anything is sent. The network does not accept them.
- GET requests fail with `unimplemented` and the message "GET requests are not supported", because
  a GET has no body to sign.
- A compressed first message is signed as sent. A client that signs above the gRPC framer cannot
  do that, so it refuses a call with a compressor set through call options, with `unimplemented`
  and the message "compressed requests are not supported", before anything is sent.

## Timeouts

Every client factory takes two timeouts.

| | Applies to | Default |
|---|---|---|
| `timeout` | Unary calls | 15 seconds |
| `streamTimeout` | Client and server streaming calls, for the whole call | 5 minutes |

- A call is unary or streaming by its RPC kind. A gRPC unary call is a unary call.
- The timeout is the call's deadline. It is sent to the server, and a call that runs out of time
  fails with `deadline exceeded`.
- The stream timeout covers the whole call, including the wait for the first message. A stream that
  runs longer than 5 minutes ends with `deadline exceeded` unless the caller passes a longer
  `streamTimeout`.
- A synchronous client (Python's sync client) cannot interrupt a request source that blocks: it
  checks the deadline each time the source yields a message, and sends nothing if the time is up.
  A source that blocks forever keeps the call waiting, so bound the blocking reads in such a source.
- A deadline that the caller sets on a call replaces the default, whether it is shorter or longer.
- A timeout must be a positive duration of at most 2147483647 ms. Zero, a negative value, a larger
  value, no value (`null`, `None`, `Infinity`, `InfiniteTimeSpan`) and a value that is not a number
  are refused with the language's invalid argument error and the message
  "`<option>` must be a positive duration of at most 2147483647 ms". A timeout cannot be turned off.
  For a longer limit, pass a larger value.

## Wire format and protocol

Go, Node and Python speak ConnectRPC. Java and C# speak gRPC.

- `wireFormat` (Go, Node, Python): `Binary` (the default) or `Json`.
- `protocol` (Go, Python): `Connect` (the default) or `Grpc`. `Grpc` on an `http://` base URL uses
  HTTP/2 without TLS.
- No client compresses a request by default, and there is no option for it.

## Other options

- The base URL defaults to `https://api.t-0.network`. An empty value is refused with "base URL is
  not set". Any other value is refused with "base URL is not valid" unless all of these hold:
  - it starts with `http://` or `https://` and has no user info;
  - the host is an IP address (IPv6 in brackets) or a name made of ASCII letters, digits, `-` and
    `.`, so a name with `_` is refused;
  - a port, if given, is from 1 to 65535;
  - there is no path, query or fragment, though a single `/` may end the value (`https://host/` is
    accepted, `https://host/v1`, `https://host?x` and `https://host#x` are refused).

  Every client sends a call to `<base URL>/<package.Service>/<Method>`. A missing scheme is not
  added.
- No client follows a redirect. A `3xx` answer fails the call, so a signed request is never sent
  again to another address.
- The signer is a hex private key or a signing function (in Java and C#, an interface that the
  `Signer` class implements). A `0x` or `0X` prefix is allowed. An empty key is refused with "private
  key must not be null or empty", and anything other than 64 hex characters with "private key must be
  32 bytes (64 hex characters)". A key of 0, or not below the secp256k1 group order, is refused with
  "private key must be in range [1, n-1]".
- The gRPC clients (Java and C#) send an HTTP/2 keepalive ping every 5 minutes while a call is open,
  with a 10 second timeout.

## Option names

| | Go | Node | Python | Java | C# |
|---|---|---|---|---|---|
| Unary timeout | `WithTimeout` | `timeoutMs` | `timeout` (seconds) | `timeout` | `Timeout` |
| Stream timeout | `WithStreamTimeout` | `streamTimeoutMs` | `stream_timeout` (seconds) | `streamTimeout` | `StreamTimeout` |
| Per call timeout | context deadline | `timeoutMs` in call options | `timeout_ms` | `stub(timeout, unit)` or a `Context` deadline | `deadline` in call options |
| Wire format | `WithWireFormat` | `wireFormat` | `wire_format` | | |
| Protocol | `WithProtocol` | | `protocol` | | |
| Signing function | `WithSignatureFunction` | a function in place of the key | `sign_fn` | `DigestSigner` | `ISigner` |
