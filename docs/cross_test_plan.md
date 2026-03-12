# Plan: Add cross-test vector tests to Go, Python, and Java

## Context
Only Node loads `cross_test/test_vectors.json`. Go, Python, and Java hardcode equivalent values. Adding vector-file-based tests to all platforms ensures crypto compatibility can be verified per-language before release, without running cross-language integration tests.

## What the vectors test
The vectors file contains: Keccak256 hashes, key derivation (private → public), and request hash (body + timestamp → digest). It does **not** contain expected signatures (ECDSA is non-deterministic without RFC 6979), so signing tests will do sign + verify round-trips.

## Changes

### 1. Go — `go/crypto/cross_test.go` (new file)
- Load `../../cross_test/test_vectors.json` via `os.ReadFile`
- Test Keccak256 hashes match vectors
- Test `NewSignerFromHex` derives correct public key
- Test request hash computation (body + LE timestamp)
- Test sign + verify round-trip using vector key + hash
- Use `testing` + `testify/require` (existing pattern)

### 2. Python — `python/sdk/tests/crypto/test_cross_vectors.py` (new file)
- Load `../../../../cross_test/test_vectors.json` via `json.load`
- Test `legacy_keccak256` matches vector hashes
- Test `new_signer_from_hex` derives correct public key
- Test request hash computation
- Test sign + verify round-trip
- Use `pytest` (existing pattern)

### 3. Java — `java/sdk/src/test/java/network/t0/sdk/crypto/CrossVectorTest.java` (new file)
- Load vectors from classpath or relative path via `getClass().getResourceAsStream()` or `Paths.get()`
- Test `Keccak256.hash()` matches vector hashes
- Test `Signer.fromHex()` derives correct public key
- Test request hash (body bytes + LE 8-byte timestamp)
- Test sign + verify round-trip
- Use JUnit 5 + AssertJ (existing pattern)

### 4. Node — no changes needed (already uses vectors)

## Verification
```bash
cd go && go test ./crypto/... -run Cross
cd python && uv run pytest python/sdk/tests/crypto/test_cross_vectors.py -v
cd java && ./gradlew test --tests "*CrossVector*"
cd node/sdk && npm test
```
