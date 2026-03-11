# t-0 Network Provider SDK - Core Library

Low-level gRPC library with cryptographic signing and verification for the t-0 Network.

This document provides detailed technical documentation for developers who need to understand SDK internals, extend functionality, or debug integration issues.

## Table of Contents

- [Build and Test Commands](#build-and-test-commands)
- [Architecture Overview](#architecture-overview)
- [Critical: Raw Payload Bytes](#critical-raw-payload-bytes)
- [Signature Format and Headers](#signature-format-and-headers)
- [Protocol Support](#protocol-support)
- [Thread Safety](#thread-safety)
- [Usage Examples](#usage-examples)
- [Error Handling](#error-handling)
- [Dependencies](#dependencies)
- [JMH Benchmarks](#jmh-benchmarks)
- [Troubleshooting](#troubleshooting)

---

## Build and Test Commands

```bash
# Build the SDK
./gradlew :sdk:build

# Run unit tests
./gradlew :sdk:test

# Run JMH performance benchmarks
./gradlew :sdk:jmh

# Build without tests
./gradlew :sdk:build -x test
```

---

## Architecture Overview

The SDK is organized into three main subsystems:

### 1. Client Layer (`network.t0.sdk.network`)

Handles outbound requests to the t-0 Network with automatic request signing.

| Class | Description |
|-------|-------------|
| `NetworkClient` | Abstract base class managing gRPC channels and signing interceptor |
| `BlockingNetworkClient<S>` | Synchronous/blocking RPC calls |
| `AsyncNetworkClient<S>` | Asynchronous calls using `StreamObserver` callbacks |
| `FutureNetworkClient<S>` | Asynchronous calls returning `ListenableFuture` |

**Key Features:**
- Automatic request signing via `SigningClientInterceptor`
- Configurable timeouts (default: 30 seconds)
- Endpoint parsing (supports `https://host`, `http://host:port`, `host:port`)
- Graceful shutdown with 5-second timeout

### 2. Server Layer (`network.t0.sdk.provider`)

Handles inbound requests from the t-0 Network with automatic signature verification.

| Class | Description |
|-------|-------------|
| `ProviderServer` | gRPC server with built-in signature verification |
| `SignatureVerificationInterceptor` | Validates incoming request signatures |

**Key Features:**
- Builder pattern for configuration
- Configurable message sizes (default: 4MB inbound)
- Automatic rejection of invalid/expired signatures
- Support for both Connect and gRPC protocols

### 3. Cryptography Layer (`network.t0.sdk.crypto`)

Handles all cryptographic operations using secp256k1 curve (Ethereum-compatible).

| Class | Description |
|-------|-------------|
| `Signer` | ECDSA signing with private key |
| `SignatureVerifier` | Signature validation with public key |
| `Keccak256` | Ethereum-compatible Keccak-256 hashing |
| `SignResult` | Immutable signature result container |

**Key Features:**
- 65-byte signatures (r[32] + s[32] + v[1])
- 65-byte uncompressed public keys (0x04 + x[32] + y[32])
- Recovery ID (v) for public key recovery
- Thread-safe stateless utilities

### 4. Common Utilities (`network.t0.sdk.common`)

| Class | Description |
|-------|-------------|
| `Headers` | Constants for signature HTTP headers |
| `HexUtils` | Hex encoding/decoding utilities |

---

## Critical: Raw Payload Bytes

> **This is the most important implementation detail in the entire SDK.**

### The Problem

Protobuf serialization is **not deterministic**. Different library versions, field ordering, and encoding choices can produce different binary representations of the same logical message. If you deserialize a message and re-serialize it, the bytes may differ from the original.

**This breaks signature verification.**

### The Requirement

#### Client Side (Signing)
The SDK serializes the message **once** and sends the **exact same bytes** that were signed:

```java
// Inside SigningClientInterceptor
byte[] messageBytes = method.getRequestMarshaller()
    .stream(message)
    .readAllBytes();                    // Serialize ONCE

byte[] digest = Keccak256.hash(messageBytes, timestampBytes);
SignResult signature = signer.sign(digest);

rawCall.sendMessage(messageBytes);       // Send EXACT bytes we signed
```

The `ByteArrayMarshaller` class prevents gRPC from re-encoding the message.

#### Server Side (Verification)
The SDK verifies against the **original wire bytes**, never re-serialized data:

```java
// Inside SignatureVerificationInterceptor
// Server configured with useInputStreamMessages() to receive raw bytes
byte[] bodyBytes = inputStream.readAllBytes();  // Original bytes

byte[] digest = Keccak256.hash(bodyBytes, timestampBytes);
boolean valid = SignatureVerifier.verify(publicKey, digest, signature);
```

### Why This Matters

```java
// WRONG - This will fail verification
Message msg = parseFrom(receivedBytes);
byte[] toVerify = msg.toByteArray();     // Re-encoded - different bytes!
verifySignature(toVerify, signature);     // FAILS

// CORRECT - Use original bytes
byte[] originalBytes = getOriginalRequestBytes();
verifySignature(originalBytes, signature); // WORKS
```

This design ensures correct signature verification regardless of protobuf library versions.

---

## Signature Format and Headers

### HTTP Headers

| Header | Description | Format |
|--------|-------------|--------|
| `X-Signature` | ECDSA signature | `0x` + 130 hex chars (65 bytes) |
| `X-Public-Key` | Signer's public key | `0x` + 130 hex chars (65 bytes) |
| `X-Signature-Timestamp` | Request timestamp | Unix milliseconds (decimal string) |

### Digest Computation

The digest is computed as:

```
digest = Keccak256(payload_bytes || timestamp_bytes)
```

Where:
- `payload_bytes` = Raw protobuf message bytes (or framed bytes for gRPC)
- `timestamp_bytes` = 8-byte **little-endian** encoded Unix milliseconds

### Signature Format

65 bytes total:
- **r** (32 bytes): ECDSA r component, big-endian
- **s** (32 bytes): ECDSA s component, big-endian
- **v** (1 byte): Recovery ID (0 or 1)

### Timestamp Validation

- **Window**: 60 seconds (configurable via `Headers.TIMESTAMP_VALIDITY_WINDOW_MS`)
- Requests with timestamps outside this window are rejected with `INVALID_ARGUMENT`

---

## Protocol Support

The SDK supports both Connect and gRPC protocols:

### Connect Protocol
- HTTP body contains **raw protobuf bytes**
- Signature computed over: `Keccak256(protobuf_bytes || timestamp)`

### gRPC Protocol
- HTTP body contains **5-byte frame prefix + protobuf bytes**
- Frame format: `[0x00 (compression flag)] [4-byte length, big-endian]`
- Signature computed over: `Keccak256(frame + protobuf_bytes || timestamp)`

The `SignatureVerificationInterceptor` automatically tries both formats, enabling cross-protocol compatibility.

---

## Thread Safety

All SDK components are thread-safe for concurrent use:

| Component | Thread-Safe | Implementation |
|-----------|-------------|----------------|
| `Signer` | Yes | Creates new `ECDSASigner` per `sign()` call |
| `SignatureVerifier` | Yes | Stateless utility methods |
| `Keccak256` | Yes | Stateless utility methods |
| `HexUtils` | Yes | Stateless utility methods |
| `BlockingNetworkClient` | Yes | gRPC channel handles concurrency |
| `AsyncNetworkClient` | Yes | gRPC channel handles concurrency |
| `FutureNetworkClient` | Yes | gRPC channel handles concurrency |
| `ProviderServer` | Yes | gRPC server handles concurrency |
| `SigningClientInterceptor` | Yes | Per-call state isolation |
| `SignatureVerificationInterceptor` | Yes | Per-call state isolation |

---

## Usage Examples

### Creating a Blocking Client

```java
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;

Signer signer = Signer.fromHex(privateKeyHex);

try (var client = BlockingNetworkClient.create(
        "https://api.t-0.network",
        signer,
        NetworkServiceGrpc::newBlockingStub)) {

    var response = client.stub().updateQuote(request);
}
```

### Creating an Async Client

```java
import network.t0.sdk.network.AsyncNetworkClient;

try (var client = AsyncNetworkClient.create(
        "https://api.t-0.network",
        signer,
        NetworkServiceGrpc::newStub)) {

    client.stub().updateQuote(request, new StreamObserver<>() {
        @Override
        public void onNext(Response response) { /* handle response */ }
        @Override
        public void onError(Throwable t) { /* handle error */ }
        @Override
        public void onCompleted() { /* done */ }
    });
}
```

### Creating a Provider Server

```java
import network.t0.sdk.provider.ProviderServer;

// Using builder pattern
ProviderServer server = ProviderServer.create(8080, networkPublicKeyHex)
    .withService(new MyProviderService())
    .withMaxInboundMessageSize(8 * 1024 * 1024)  // 8MB
    .withMaxInboundMetadataSize(16 * 1024)       // 16KB
    .withHandshakeTimeout(60, TimeUnit.SECONDS)
    .start();

server.awaitTermination();

// Or using convenience method
ProviderServer server = ProviderServer.startWith(
    8080,
    networkPublicKeyHex,
    new MyProviderService()
);
```

### Manual Signing (Advanced)

```java
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.crypto.Keccak256;

Signer signer = Signer.fromHex(privateKeyHex);

// Compute digest
byte[] digest = Keccak256.hash(messageBytes, timestampBytes);

// Sign
SignResult result = signer.sign(digest);

// Get signature components
String signatureHex = result.getSignatureHexPrefixed();  // 0x...
String publicKeyHex = result.getPublicKeyHexPrefixed();  // 0x...
```

### Manual Verification (Advanced)

```java
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.common.HexUtils;

byte[] publicKey = HexUtils.hexToBytes(publicKeyHex);
byte[] signature = HexUtils.hexToBytes(signatureHex);
byte[] digest = Keccak256.hash(messageBytes, timestampBytes);

boolean valid = SignatureVerifier.verify(publicKey, digest, signature);
```

---

## Error Handling

### Server-Side gRPC Status Codes

| Condition | Status Code | Description |
|-----------|-------------|-------------|
| Missing headers | `INVALID_ARGUMENT` | Required signature headers not present |
| Malformed headers | `INVALID_ARGUMENT` | Headers present but invalid format |
| Timestamp expired | `INVALID_ARGUMENT` | Timestamp outside 60-second window |
| Wrong public key | `UNAUTHENTICATED` | Request signed by unexpected key |
| Invalid signature | `UNAUTHENTICATED` | Signature verification failed |
| Server error | `INTERNAL` | Server misconfiguration or unexpected error |

### Client-Side Exceptions

- `StatusRuntimeException` - gRPC call failed with status code
- `IllegalArgumentException` - Invalid configuration (endpoint, keys)
- `IOException` - Network connectivity issues

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| gRPC (netty-shaded) | 1.78.0 | Server transport |
| gRPC (okhttp) | 1.78.0 | Client transport |
| gRPC (protobuf) | 1.78.0 | Protobuf integration |
| Protobuf Java | 4.33.4 | Message serialization |
| BouncyCastle | 1.83 | Cryptography (secp256k1, Keccak-256) |
| SLF4J | 2.0.17 | Logging abstraction |

---

## JMH Benchmarks

The SDK includes JMH benchmarks to measure cryptographic operation throughput:

```bash
./gradlew :sdk:jmh
```

### Benchmarked Operations

| Benchmark | Description |
|-----------|-------------|
| `hashSmall` | Keccak-256 hashing of 100-byte message |
| `hashMedium` | Keccak-256 hashing of 1KB message |
| `hashLarge` | Keccak-256 hashing of 100KB message |
| `sign` | ECDSA signing throughput |
| `verify` | ECDSA verification throughput |
| `hexEncode` | Bytes to hex string |
| `hexDecode` | Hex string to bytes |
| `hashAndSign` | Combined hash + sign operation |
| `hashAndVerify` | Combined hash + verify operation |

Results are reported in operations per millisecond.

---

## Troubleshooting

### Signature Verification Fails

**Symptom**: Server returns `UNAUTHENTICATED` with "Invalid signature"

**Possible Causes**:
1. **Re-serialization**: Message was deserialized and re-serialized before verification. Ensure you're using raw bytes.
2. **Timestamp mismatch**: Client and server clocks are out of sync. Check system time.
3. **Wrong public key**: Server is configured with a different public key than the client is using.
4. **Protocol mismatch**: Client using Connect but server expecting gRPC framing (or vice versa).

**Debug Steps**:
- Enable debug logging to see the exact bytes being signed/verified
- Verify the public key matches between client and server
- Check timestamp is within 60-second window

### Timestamp Rejected

**Symptom**: Server returns `INVALID_ARGUMENT` with timestamp error

**Solution**: Ensure client system clock is synchronized (NTP). The allowed window is 60 seconds.

### Connection Refused

**Symptom**: Client cannot connect to server

**Possible Causes**:
1. Server not running or wrong port
2. Firewall blocking connection
3. TLS/SSL certificate issues

**Debug Steps**:
- Verify server is listening: `netstat -an | grep <port>`
- Check firewall rules
- For HTTPS, verify certificate is valid

### Out of Memory During Signing

**Symptom**: `OutOfMemoryError` when signing large messages

**Solution**: The SDK streams message bytes. If you're seeing OOM, you may be loading the entire message into memory elsewhere. Check your protobuf message construction.

### gRPC Deadline Exceeded

**Symptom**: `DEADLINE_EXCEEDED` status

**Solution**: Increase timeout when creating client:
```java
BlockingNetworkClient.create(endpoint, signer, stubFactory, 60); // 60 seconds
```

Or per-call:
```java
client.stub(60, TimeUnit.SECONDS).someMethod(request);
```

### Hex Encoding Errors

**Symptom**: `IllegalArgumentException` when parsing keys

**Solution**: Ensure hex strings are:
- Even length (each byte = 2 hex chars)
- Valid hex characters (0-9, a-f, A-F)
- `0x` prefix is optional and handled automatically

---

## Contributing

- Java 17+ required
- Run `./gradlew build` to compile and test
- Proto files in `sdk/src/main/proto/`
- Generated code in `sdk/build/generated/source/proto/`

### Code Style

- Follow existing patterns in the codebase
- Ensure thread safety for all public APIs
- Add tests for new functionality
- Update this README for significant changes
