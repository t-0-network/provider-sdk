# T-0 Provider Python SDK -- Streaming RPCs

How the Python SDK's client signs, sends and times out client- and server-streaming calls. The signature protocol itself (digest, headers, tolerance) is in [ARCHITECTURE.md §2.1](ARCHITECTURE.md#21-signature-protocol); the rule shared by every SDK is in the root [`CLAUDE.md`](../../CLAUDE.md) ("Streaming RPCs -- only the first message is signed").

Code: `python/sdk/src/t0_provider_sdk/network/signing.py` (`SigningClient`, `SigningSyncClient`) and `network/client.py` (`new_service_client()`, `new_service_client_sync()`).

---

## 1. What is signed

The request's **content type** decides, never the form of the body (bytes or iterator). Only the media type counts: parameters dropped, trimmed, lower-cased; no headers means no content type.

| Media type | Requests | Signed bytes |
|------------|----------|--------------|
| `application/connect+*` | Connect client/server streaming, any codec (`+proto`, `+json`) | First envelope, exactly as sent |
| `application/grpc`, `application/grpc+*` | gRPC, unary included | First envelope, exactly as sent |
| anything else | Connect unary (`application/proto`, `application/json`), GET (no body), gRPC-Web (`application/grpc-web*`) | Whole body |

- An envelope is `flags (1 byte) || uint32be(length) || payload`. With flag bit 0 set the payload is compressed, and the compressed bytes are what is signed. connectrpc gzips request messages by default.
- Later messages are sent unsigned.
- A gRPC unary body is one envelope, so for it "first envelope" is the whole body.
- gRPC-Web is signed whole: that is what the network verifies for it.
- The codec does not matter. A Connect JSON stream (`application/connect+json`) is enveloped like a binary one and signed over its first envelope as sent; the cross tests check this against the Go helper.

The digest is unchanged: `Keccak256(signed_bytes || uint64le(timestamp_ms))`.

## 2. How connectrpc hands bodies to the transport

ConnectRPC calls exactly three methods on its HTTP client:

| Method | Used for | `content` | Signed over |
|--------|----------|-----------|-------------|
| `get()` | Connect unary over GET | none | `b""` |
| `post()` | Connect unary | bytes | whole body |
| `stream()` | Connect streaming; every gRPC and gRPC-Web call, unary included | (async) iterator yielding **one complete envelope per message** | per §1 |

`stream()` also takes bytes (or nothing) for other callers: enveloped bytes are signed over the first envelope, cut from them by its length prefix (`b""` for empty bytes); other bytes over the whole body.

One envelope per chunk is how connectrpc happens to frame bodies, not a contract, so it is **checked, not trusted**: a first chunk that is not exactly one envelope (5 + the length in its prefix) fails the call with `ConnectError(Code.INTERNAL)`, the iterator is closed and nothing is sent. Enveloped bytes that end inside their first envelope fail the same way. A change in that framing therefore fails calls; it can never produce a signature over the wrong bytes.

Treating `stream()`'s content as bytes is a known pitfall: see [PITFALLS.md §10](PITFALLS.md#10-pyqwestclient--wrapper-not-subclass).

## 3. Sending: at once, never buffered

The network checks the timestamp when the headers arrive, before it reads the body. So the client signs as soon as it has the first message and sends the request at once; it never buffers a stream to sign it.

For an enveloped iterator, `stream()` returns a context manager that, **when entered**, reads the first chunk, checks and signs it, and enters pyqwest's `stream()` with a body that yields that chunk and then forwards the rest of the iterator as it comes. connectrpc enters the context inside its call timeout, so the wait for the first message counts against the call's deadline (§4).

For callers this means:

- A client-streaming request goes out only once its first message is available. **Send a message (or close the stream) before waiting for a response.**
- A client stream closed before its first message is signed over `b""` and sent; the network rejects it (`UNAUTHENTICATED`). Every SDK does the same.

For any other iterator (gRPC-Web), the context manager reads the iterator to its end, closes it, signs the joined bytes and sends them as bytes.

Closing:

- pyqwest closes only the body it is given, so the wrapper's body (`_AsyncChain` / `_SyncChain`) forwards `aclose()` / `close()` to connectrpc's iterator.
- The sync body is written on pyqwest's own thread and closed from the caller's. pyqwest skips closing a generator that is running, since that cannot be done reliably; it cannot see the generator behind the wrapper's body, so the wrapper skips it too.
- If reading or checking the first message fails, the iterator is closed and nothing is sent.

## 4. Timeouts

A ConnectRPC client has one `timeout_ms` for every call, and a stream's timeout covers the whole stream, so a single 15-second default would cut off long uploads and downloads. The factories take two defaults instead:

| Parameter | Applies to | Default |
|-----------|------------|---------|
| `timeout` | Unary calls, Connect and gRPC | 15 s (`DEFAULT_TIMEOUT`); must be > 0, else `ValueError` |
| `stream_timeout` | Client- and server-streaming calls, from waiting for the first request message to reading the end of the response | `None`: no timeout. `0` also means none; negative raises `ValueError` |

```python
network_client = new_service_client(
    private_key,
    NetworkServiceClient,
    timeout=15.0,          # unary calls, seconds
    stream_timeout=300.0,  # streaming calls, seconds; None (default) = no timeout
)
```

How it works:

- The client is built with `timeout_ms=None`, and the instance's `execute_unary`, `execute_client_stream` and `execute_server_stream` are wrapped to fill in their call type's default. gRPC unary goes out through `stream()` but still enters through `execute_unary`, so it gets `timeout`.
- A call's own `timeout_ms` wins. As in connectrpc, a falsy `timeout_ms` falls back to the default.
- Defaults are rounded to whole milliseconds, at least 1 ms: connectrpc reads `0` as "no timeout".
- The timeout is the call's deadline, so connectrpc also sends it to the server (`connect-timeout-ms` / `grpc-timeout`). When it elapses the call fails with `ConnectError(Code.DEADLINE_EXCEEDED)` (sync: not before the source yields, see below), and if that happens before the first message, nothing is sent.

Async and sync apply the deadline differently:

- **Async:** connectrpc enters `stream()`, and runs the whole call, inside `asyncio.timeout`, so the wait for the first message is covered.
- **Sync:** connectrpc passes the remaining time to pyqwest as `timeout`, which only starts when pyqwest sends. `SigningSyncClient` deducts the time the source took to yield its first message (or, for a whole-body iterator, the whole body) from the `timeout` it passes on, but it does not interrupt a source that blocks: if no time is left once the source yields, nothing is sent, the source is closed and `TimeoutError` is raised, which connectrpc reports as `DEADLINE_EXCEEDED`. Bounding the time of each read is up to the source.

## 5. Bidirectional streams

Not supported. The clients built by `new_service_client()` / `new_service_client_sync()` replace the instance's `execute_bidi_stream` with a function that raises `ConnectError(Code.UNIMPLEMENTED, "bidirectional streams are not supported")` at the call, before the request iterator is read or anything is sent. For the async client too: its `execute_bidi_stream` is a plain `def` that returns the response iterator, so the call itself raises.

This is a **policy, not a limit of the signing**: the network does not accept bidirectional streams (#370). It also avoids a hang: the transport sends a stream only once its first message is signed, so a bidi caller that waited for a response before sending would block.

The rejection lives in the factory-built clients. A ConnectRPC client built by hand on `SigningClient` / `SigningSyncClient` does not have it and must not make bidirectional calls.

## 6. Tests

### Unit tests (`python/sdk/tests/`)

| File | Covers |
|------|--------|
| `network/test_stream_signing.py` | Both wrappers against a fake pyqwest client that reads and closes the body as pyqwest does. Content-type rule (case, parameters, JSON codec, gRPC-Web); enveloped iterators and bytes signed over the first envelope; other iterators signed whole and sent as bytes; request sent before message 2; first message read on enter; empty stream; first chunks that are not one envelope and truncated enveloped bytes refused with nothing sent; close forwarding, including a running sync generator; sync timeout: the time the source took is deducted, and a first message or body yielded after the timeout is not sent (raised no earlier than the source yielded). |
| `network/test_client_timeouts.py` | Real ConnectRPC clients (Connect and gRPC) down to a recording fake: unary default (gRPC unary too), stream default or none, per-call `timeout_ms`, the timeout header and the sync pyqwest timeout, validation. |
| `network/test_client_bidi.py` | Bidirectional calls raise `UNIMPLEMENTED` before anything is read or sent (async and sync, Connect and gRPC). |
| `crypto/test_cross_vectors.py` | `stream_signing_cases` in `cross_test/test_vectors.json`: signed bytes, digest and signature of every case, and the `first_envelope` cases driven through both wrappers with the vector's timestamp. `first_payload` cases (the first message without its 5-byte prefix, what a signer above the gRPC framer covers) are checked for bytes, digest and signature only, since this SDK never produces them. |

### Cross tests (`python/tests/cross_test/test_cross_stream.py`)

Against `go_helper serve` (Connect over HTTP/1.1, gRPC over h2c), which serves `test.v1.StreamTest` (`cross_test/stream_test.proto`) behind a verifier that checks a streaming request's signature as the network does -- over the first envelope (over gRPC also over its payload alone), once the headers and that envelope have arrived -- and answers HTTP 401 otherwise. It logs every verdict to stderr: `<path> verified over the first envelope|payload` or `<path> rejected: <reason>`. The tests assert on that log, and expect `envelope`: that is what this SDK signs.

- One helper process per module; a daemon thread reads its stderr (another drains stdout so the pipe never blocks the server).
- Each test calls `mark()` before its requests and looks only at lines logged after the mark. `mark()` sends a request of its own (to a `MarkN` path, which the verifier rejects) and waits for its line: the server wrote every earlier line before it, so a late line from an earlier test cannot land after the mark.
- Over Connect (factory clients) and gRPC (the wrappers on an h2c pyqwest transport), async and sync: client streaming with a gzip-compressed and an uncompressed first envelope (the signed bytes checked to be one envelope with that flag), server streaming, a 256 KiB first message, and a unary health check. Each stream is logged as verified over the first envelope.
- No buffering: the request generator holds message 2 until the helper has logged message 1 as verified; a client that buffered the stream would time out.
- Refusals, each `UNAUTHENTICATED` with the helper's reason: unknown key, unsigned stream, stream signed over its whole body, stale timestamp, empty client stream.
- A stream timeout that elapses before the first message: `DEADLINE_EXCEEDED` (sync: once the late message is yielded) and nothing logged.
- Connect JSON streams (async and sync): signed over the JSON first envelope and verified by the helper.

### Running

```bash
cd cross_test/go_helper && go build -o go_helper .   # once
cd python && uv run pytest sdk/tests/network sdk/tests/crypto -v
cd python && uv run pytest tests/cross_test/test_cross_stream.py -v
```

In CI (`CI` set) the cross tests fail rather than skip when the helper binary is missing.
