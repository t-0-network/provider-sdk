# Cross-language test vectors

`test_vectors.json` is the shared fixture for the request signature. Every SDK reads it, so
a change here is a change every language has to agree with:

| SDK | Test |
|---|---|
| Go | `go/crypto/cross_test.go` |
| Node | `node/sdk/test/crypto.test.ts` |
| Java | `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java` |
| Python | `python/sdk/tests/crypto/test_cross_vectors.py` |
| C# | `csharp/sdk/T0.ProviderSdk.Tests/Crypto/CrossTestVectors.cs` |

Hex values carry no `0x` prefix. Signatures are 64 bytes, `r || s`, unless a case says
otherwise; the recovery byte is appended by some SDKs and ignored by every verifier.

## The scheme

```
digest    = keccak256(body || uint64le(timestamp_ms))
signature = secp256k1 ECDSA over that 32-byte digest — RFC 6979 deterministic k
            (HMAC-SHA256), low-S
```

Deterministic `k` is what lets a fixture name exact signature bytes: the same key and
digest give the same `r` and `s` in every library, so a vector can be an equality
assertion rather than a sign-then-verify round trip.

## What is in the file

| Key | Contents |
|---|---|
| `keys` | the key the signing cases use and the verification cases are checked against |
| `impostor_keys` | a second key, for cases that must fail |
| `keccak256` | text input → hash |
| `request_signing` | one signing case with a text `body` |
| `request_signing_cases` | signing cases with a `body_hex`, so a body can be binary, framed or empty |
| `signature_verification` | a presented request → does it verify |

`body_hex` is the exact preimage: whatever the transport put in the body, before the
timestamp is appended and before anything decodes it. `grpc-framed-body` carries the gRPC
frame (`0x00` + `uint32be(length)` + message) because that is what the network signs when
it calls a provider over gRPC, while Connect callers sign the message alone — the Java
provider verifies against both.

`signature_verification` answers one question: does this signature verify against this
public key for this body and timestamp. It stops there on purpose. Whether a request is
*accepted* also depends on the timestamp window, which every provider measures against a
clock only it can see, so that belongs in each SDK's own middleware tests, not in a shared
fixture.

## Adding a case

Sign it with any one SDK and run the other four. A 65-byte signature has to carry the
right recovery byte — Go, Java, Node and C# ignore it, but Python verifies by recovering
the key from it, so a wrong `v` fails there and nowhere else.
