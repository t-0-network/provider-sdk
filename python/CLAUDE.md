# CLAUDE.md - Project Context & Requirements

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output. The ASGI/WSGI middleware intercepts raw body bytes BEFORE ConnectRPC deserializes them.

```python
# WRONG — re-encoded bytes will differ
msg = SomeMessage()
msg.ParseFromString(body)
verify_signature(pub_key, keccak256(msg.SerializeToString() + ts), sig)

# CORRECT — use original wire bytes (middleware does this automatically)
verify_signature(pub_key, keccak256(raw_body_bytes + ts), sig)
```

## Build Commands

```bash
uv sync --all-packages       # Install all dependencies
uv run pytest -v              # Run all tests (SDK + integration + cross)
uv run pytest sdk/tests -v    # Run SDK unit tests only
uv run pytest tests/cross_test -v  # Run Go cross-tests (requires Go helper binary)
uv run ruff check .           # Lint
```

## Project Structure

```
python/
├── pyproject.toml              # uv workspace root
├── sdk/                        # t0-provider-sdk package
│   ├── pyproject.toml
│   ├── buf.yaml + buf.gen.yaml # Proto code generation config
│   └── src/t0_provider_sdk/
│       ├── api/                # Generated ConnectRPC code (committed)
│       ├── proto/              # Proto definitions (source of truth)
│       ├── crypto/             # hash, keys, signer, verifier
│       ├── common/             # headers
│       ├── network/            # signing transport, client factory
│       └── provider/           # middleware, interceptor, handler
├── starter/                    # t0-provider-starter CLI package
│   ├── pyproject.toml
│   └── src/t0_provider_starter/
│       ├── cli.py              # Click-based CLI entry point
│       ├── keygen.py           # secp256k1 keypair generation
│       └── template/           # Embedded project template
└── tests/
    └── cross_test/             # Go interop tests (one-time validation)
        ├── go_helper/          # Small Go binary for cross-testing
        ├── test_cross_signature.py   # Crypto interop (hash, sign, verify)
        ├── test_cross_server.py      # ASGI server-to-server
        └── test_cross_server_sync.py # WSGI server-to-server
```

## Key Dependencies

| Package | PyPI | Import | Purpose |
|---------|------|--------|---------|
| connect-python | `connect-python>=0.8` | `connectrpc` | ConnectRPC runtime |
| pyqwest | (transitive) | `pyqwest` | HTTP client (Rust-backed) |
| protobuf | `protobuf>=7.34` | `google.protobuf` | Message serialization |
| coincurve | `coincurve>=21.0` | `coincurve` | secp256k1 ECDSA |
| pycryptodome | `pycryptodome>=3.23` | `Crypto.Hash.keccak` | Keccak256 |

**DO NOT use:** `pysha3` (incompatible with Python 3.13), `hashlib.sha3_256()` (different padding from Keccak256), `connectrpc` from PyPI (different package v0.0.1 by Gaudiy).

## ConnectRPC Python Specifics

- `Interceptor` is a **Union type**, not a base class. Implement `UnaryInterceptor` Protocol with `intercept_unary(self, call_next, request, ctx)`.
- Generated code imports as `tzero.v1.payment.provider_pb2` (not `t0_provider_sdk.api.tzero...`). The `api/` directory is on `sys.path` via package layout.
- Client constructors accept `http_client: pyqwest.Client | None` — we wrap pyqwest with `SigningClient` (not subclass).
- ConnectRPC calls exactly 3 methods on the client: `get()`, `post()`, `stream()`.

## Proto Code Generation

```bash
cd sdk
buf dep update           # Fetch proto dependencies
buf generate             # Generate Python + ConnectRPC stubs into src/t0_provider_sdk/api/
```

Generated code is committed to the repository.

## Starter CLI

```bash
uvx t0-provider-starter my_provider       # Create new project
uvx t0-provider-starter my_provider -d .  # Create in current directory
```

The CLI generates a complete project with `.env` (private key auto-generated), `pyproject.toml`, `Dockerfile`, and provider service stubs.

## Cross-Tests with Go SDK

```bash
cd tests/cross_test/go_helper && go build -o go_helper . && cd ../../..
uv run pytest tests/cross_test/ -v
```

Validates: Keccak256 hash, public key derivation, bidirectional signature verification, and end-to-end server-to-server communication (both ASGI and WSGI).

## Architecture (Go SDK Mapping)

| Go SDK | Python SDK |
|--------|-----------|
| `crypto/hash.go` | `crypto/hash.py` |
| `crypto/sign.go` | `crypto/signer.py` |
| `crypto/verify_signature.go` | `crypto/verifier.py` |
| `crypto/helper.go` | `crypto/keys.py` |
| `common/header.go` | `common/headers.py` |
| `network/signing_transport.go` | `network/signing.py` |
| `network/client.go` | `network/client.py` |
| `provider/verify_signature.go` | `provider/middleware.py` (ASGI), `provider/middleware_wsgi.py` (WSGI) |
| `provider/signature_error.go` | `provider/interceptor.py` |
| `provider/handler.go` | `provider/handler.py` |

## Architectural Invariants

- **Raw bytes:** Signature verification and signing always use original wire bytes, never re-serialized protobuf (see critical requirement above)
- **Two-phase verification:** ASGI/WSGI middleware (raw bytes) → `contextvars.ContextVar` → ConnectRPC interceptor (error codes). Do not collapse into a single layer.
- **Wrapper pattern:** `SigningClient` wraps `pyqwest.Client` via delegation (not subclass). pyqwest is Rust-backed FFI — subclassing is undefined.
- **Proto-agnostic:** `handler()`/`handler_sync()` and `new_service_client()`/`new_service_client_sync()` accept any generated ConnectRPC class. Do not add service-specific logic to these functions.

## Signature Protocol Quick Reference

```
digest  = Keccak256(body_bytes || struct.pack("<Q", timestamp_ms))
sig, pk = sign_fn(digest)   # 65-byte sig (r+s+v), 65-byte uncompressed pubkey
headers = { X-Public-Key: "0x"+pk.hex(), X-Signature: "0x"+sig.hex(), X-Signature-Timestamp: str(ms) }
```

Timestamp tolerance: ±60 seconds. Max body: 4 MB default.

## Error Hierarchy

`SignatureVerificationError` (base) with 6 subclasses:
- → `INVALID_ARGUMENT`: `MissingRequiredHeaderError`, `InvalidHeaderEncodingError`, `TimestampOutOfRangeError`, `BodyTooLargeError`
- → `UNAUTHENTICATED`: `UnknownPublicKeyError`, `SignatureFailedError`

## Public API Surface

```python
# crypto/
legacy_keccak256, new_signer, new_signer_from_hex, SignFn,
private_key_from_hex, public_key_from_hex, public_key_from_bytes, public_key_to_bytes,
verify_signature

# common/
PUBLIC_KEY_HEADER, SIGNATURE_HEADER, SIGNATURE_TIMESTAMP_HEADER

# network/
SigningClient, SigningSyncClient, new_service_client, new_service_client_sync,
DEFAULT_BASE_URL, DEFAULT_TIMEOUT

# provider/
handler, handler_sync, new_asgi_app, new_wsgi_app, HandlerOption, BuildHandler, BuildHandlerSync,
SignatureVerificationError, MissingRequiredHeaderError, InvalidHeaderEncodingError,
TimestampOutOfRangeError, UnknownPublicKeyError, SignatureFailedError
```

## Starter Template System

- Template files in `starter/src/t0_provider_starter/template/`
- `{{PROJECT_NAME}}` placeholder replaced during generation
- Files with `.template` suffix have the suffix stripped (e.g., `pyproject.toml.template` → `pyproject.toml`)
- `.env` created from `.env.example` with auto-generated private key
- Template directory included in wheel via `artifacts = ["template/**"]` in hatch config

## Documentation

Docs live in the top-level [`docs/python/`](../../docs/python/) directory:
- [`ARCHITECTURE.md`](../../docs/python/ARCHITECTURE.md) — comprehensive architecture guide
- [`PITFALLS.md`](../../docs/python/PITFALLS.md) — critical gotchas and lessons learned
- [`PLAN.md`](../../docs/python/PLAN.md) — detailed implementation plan

## Git Workflow

- NEVER commit or push without explicit user request
- Run tests locally before suggesting commits
