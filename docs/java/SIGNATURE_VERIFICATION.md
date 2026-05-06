# Signature Verification — Dual-Path Design

## TL;DR

`SignatureVerificationInterceptor.verifySignature` accepts a request if the signature validates against **either** of two payload framings:

1. **Unframed** — raw protobuf bytes: `Keccak256(messageBytes || ts_le_u64)`
2. **gRPC-framed** — 5-byte gRPC frame prefix + protobuf bytes: `Keccak256(frame_prefix || messageBytes || ts_le_u64)`

**This is not defensive code. Both paths are exercised by real callers in production.** Do not remove either path.

## Why two paths?

The signed payload depends on **where the signing interceptor sits in the stack**, not on which protocol is in use. Two equally valid wirings exist:

- **Signer above the gRPC framer.** The interceptor signs the protobuf message bytes; the gRPC framer then prepends the 5-byte frame on the wire. The signed payload is **unframed** protobuf. This is how the Java SDK's `NetworkClient` is wired: it pulls bytes from the request marshaller, signs those bytes, and forwards them so the gRPC layer cannot re-serialize and produce different bytes.
- **Signer below the gRPC framer.** The interceptor sits at the HTTP transport layer and sees a body onto which the framer has already prepended the 5-byte frame. The signed payload therefore covers **the frame**.

Both wirings produce a deterministic, reproducible signed payload. Neither is wrong — they cover different bytes because the interceptor sits at different layers.

## What the T-0 Network does

The T-0 Network is a signing party that calls providers. The bytes the network signs depend on which transport the network has been configured to use for a given provider:

| Network → provider transport | Signed payload |
|---|---|
| Connect protocol with proto binary | unframed protobuf bytes |
| Connect protocol with proto JSON | unframed JSON bytes |
| **gRPC protocol** | **gRPC-framed body: 5-byte frame prefix (1 byte compressed flag = 0, then 4 bytes big-endian length) followed by the protobuf message bytes** |

The signature itself is constructed identically in every case:

```
ts_le_u64 = uint64(timestamp_ms) encoded little-endian, 8 bytes
digest    = Keccak256(payload_bytes || ts_le_u64)
signature = secp256k1_sign(network_private_key, digest)
```

with `payload_bytes` being whichever of the framings above corresponds to the configured transport. Headers sent on the request:

- `X-Public-Key` — uncompressed secp256k1 public key, hex-encoded with `0x` prefix (65 bytes raw, 0x04 prefix on the key itself)
- `X-Signature` — signature, hex-encoded with `0x` prefix (64 or 65 bytes raw)
- `X-Signature-Timestamp` — millisecond Unix timestamp as a decimal string

A Java provider therefore receives:
- **gRPC-framed** signatures whenever the network is configured to call it via gRPC protocol.
- **Unframed** signatures whenever the network is configured to call it via Connect protocol, **and** from the Java SDK's own `NetworkClient` (which signs above the framer).

Both must validate. Hence the dual-path.

## What the verifier does

```java
// Path 1: unframed (signer was above the framer, or no framing exists).
byte[] digestRaw = Keccak256.hash(bodyBytes, timestampBytes);
if (SignatureVerifier.verify(publicKey, digestRaw, signature)) return true;

// Path 2: gRPC-framed (signer was below the framer).
byte[] framedBytes = reconstructGrpcFrame(bodyBytes);
byte[] digestFramed = Keccak256.hash(framedBytes, timestampBytes);
if (SignatureVerifier.verify(publicKey, digestFramed, signature)) return true;

return false;
```

`bodyBytes` is the raw protobuf message bytes as delivered by `ServerInterceptors.useInputStreamMessages` — the gRPC server has already stripped the 5-byte frame before the interceptor sees the bytes. Reconstruction in path 2 is straightforward: 1 byte compressed flag (0) + 4 bytes big-endian length of `bodyBytes`, prepended.

The network's verification logic on its own ingress mirrors this dual-path with the symmetric convention: it tries the on-wire body first, and for gRPC requests retries with the 5-byte frame stripped. The two systems together accept any caller whose signing wiring is internally consistent (signs and sends the exact same bytes at the same layer), regardless of whether that layer is above or below the framer.

## CRITICAL: do not remove either path

Removing path 1 silently breaks every caller whose signing wiring is above the framer — including the Java SDK's own `NetworkClient`, and the network when calling Java providers configured for Connect protocol. Removing path 2 silently breaks every caller whose signing wiring is below the framer — including the network when calling Java providers configured for gRPC protocol.

In both cases the failure surfaces only as `UNAUTHENTICATED` responses for one class of caller, not the other — the kind of stealth breakage that is hard to attribute and easy to misdiagnose.

GitHub issue [#89](https://github.com/t-0-network/provider-sdk/issues/89) raised concern that the framed path looked like dead code because the SDK clients shipped in this repo all sign unframed. The audit was incomplete — it considered only same-repo SDK clients and missed the network's gRPC-protocol path, which signs framed bodies. Both framings are required.

## Conditions under which simplification would be safe

A single canonical framing could be enforced only if **all** signing parties that talk to a Java provider can be confirmed to sign at the same layer. That confirmation must include:

- All four SDK clients in this repo (currently all sign unframed).
- The network for every transport configuration it can be set to use against the provider.
- Any third-party client built directly on a gRPC framework, where the integrator chose where to wire signing.

Until that confirmation is gathered for a sustained window with metrics, the dual-path stays.

## References

- `java/sdk/src/main/java/network/t0/sdk/provider/SignatureVerificationInterceptor.java` — `verifySignature`, `reconstructGrpcFrame`
- `java/sdk/src/main/java/network/t0/sdk/network/NetworkClient.java` — `SigningClientInterceptor`, where the Java SDK signs above the framer
- GitHub issue (closed as no-op after investigation): https://github.com/t-0-network/provider-sdk/issues/89
