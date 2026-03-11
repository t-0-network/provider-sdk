# Python Provider SDK + Starter - Implementation Plan

## Context

The T-0 Network needs a Python provider SDK and starter, matching the existing Go SDK (golden standard) and Java SDK. The Python SDK will use ConnectRPC protocol (like Go, NOT gRPC), live in a single repository with both SDK and starter, and provide one-line project initialization for Python developers joining the T-0 network.

**Goal:** Outstanding architecture, no compromises. Go SDK is the reference. Both sync and async support.

## Key Dependencies (Verified)

| Package | PyPI name | Import as | Version | Purpose |
|---------|-----------|-----------|---------|---------|
| ConnectRPC runtime | `connect-python` | `connectrpc` | 0.8.1 | Server + client runtime |
| ConnectRPC codegen | `protoc-gen-connect-python` | CLI tool | 0.8.1 | Proto code generation |
| HTTP backend | `pyqwest` | `pyqwest` | >=0.3.0 | HTTP client (transitive dep of connect-python, also used directly for signing wrapper) |
| Protobuf | `protobuf` | `google.protobuf` | >=5.28 | Message serialization |
| Crypto | `coincurve` | `coincurve` | >=21.0 | secp256k1 ECDSA |
| Keccak256 | `pycryptodome` | `Crypto.Hash.keccak` | >=3.23 | LegacyKeccak256 hash |
| CLI framework | `click` | `click` | >=8.1 | Starter CLI |
| Env loading | `python-dotenv` | `dotenv` | >=1.0 | Template .env loading |

**IMPORTANT:** Do NOT use `pysha3` (incompatible with Python 3.13). Do NOT use `connectrpc` from PyPI (that's a different package, v0.0.1 by Gaudiy).

---

## Repository Structure

```
provider-python/
├── pyproject.toml                          # uv workspace root
├── uv.lock
├── CLAUDE.md
├── .mcp.json
├── docs/PLAN.md
├── sdk/                                    # t0-provider-sdk package
│   ├── pyproject.toml
│   ├── buf.yaml
│   ├── buf.gen.yaml
│   └── src/t0_provider_sdk/
│       ├── __init__.py
│       ├── api/                            # Generated ConnectRPC Python code (committed)
│       │   └── tzero/v1/...
│       ├── proto/                          # Proto definitions (source of truth)
│       │   └── tzero/v1/...
│       ├── provider/                       # Server-side SDK
│       │   ├── __init__.py
│       │   ├── handler.py                  # Generic handler registration
│       │   ├── middleware.py               # ASGI signature verification
│       │   ├── interceptor.py              # ConnectRPC error interceptor
│       │   └── errors.py
│       ├── network/                        # Client-side SDK
│       │   ├── __init__.py
│       │   ├── client.py                   # Generic client factory
│       │   ├── signing.py                  # Signing HTTP transport
│       │   └── options.py
│       ├── crypto/                         # Cryptographic utilities
│       │   ├── __init__.py
│       │   ├── signer.py                   # ECDSA signing (coincurve)
│       │   ├── verifier.py                 # Signature verification
│       │   ├── keys.py                     # Key conversion
│       │   └── hash.py                     # Keccak256
│       └── common/
│           ├── __init__.py
│           └── headers.py
│   └── tests/
│       ├── crypto/
│       │   ├── test_hash.py
│       │   ├── test_keys.py
│       │   ├── test_signer.py
│       │   └── test_verifier.py
│       ├── provider/
│       │   ├── test_middleware.py
│       │   └── test_handler.py
│       ├── network/
│       │   ├── test_signing.py
│       │   └── test_client.py
│       └── integration/
│           └── test_signature_verification.py
├── starter/                                # t0-provider-starter package (CLI)
│   ├── pyproject.toml
│   └── src/t0_provider_starter/
│       ├── __init__.py
│       ├── cli.py
│       ├── keygen.py
│       └── template/                       # Embedded starter template
│           ├── pyproject.toml.template
│           ├── .env.example
│           ├── Dockerfile
│           └── src/provider/
│               ├── __init__.py
│               ├── main.py
│               ├── config.py
│               ├── publish_quotes.py
│               ├── get_quote.py
│               └── handler/
│                   ├── __init__.py
│                   └── payment.py
├── tests/                                  # Cross-tests (one-time Go<->Python)
│   └── cross_test/
│       ├── README.md
│       ├── test_cross_signature.py
│       └── go_helper/
│           ├── main.go
│           └── go.mod
└── .github/workflows/
    ├── test.yaml
    └── publish.yaml
```

---

## Step 1: Repository Scaffolding

### 1.1 Root `pyproject.toml` (uv workspace)
```toml
[project]
name = "provider-python"
version = "0.0.0"
requires-python = ">=3.13"

[tool.uv.workspace]
members = ["sdk", "starter"]

[tool.pytest.ini_options]
testpaths = ["sdk/tests", "starter/tests", "tests"]
asyncio_mode = "auto"

[tool.ruff]
target-version = "py313"
line-length = 120

[tool.mypy]
python_version = "3.13"
strict = true
```

### 1.2 SDK `sdk/pyproject.toml`
```toml
[project]
name = "t0-provider-sdk"
version = "0.1.0"
requires-python = ">=3.13"
dependencies = [
    "connect-python>=0.8",     # ConnectRPC runtime (imports as `connectrpc`)
    "protobuf>=5.28",
    "coincurve>=21.0",         # secp256k1 ECDSA
    "pycryptodome>=3.23",      # Keccak256 (NOT pysha3 - incompatible with 3.13)
]
# NOTE: connect-python brings pyqwest as transitive dependency (used directly for signing wrapper)

[project.optional-dependencies]
dev = ["pytest>=8.0", "pytest-asyncio>=0.24", "ruff>=0.8", "mypy>=1.13"]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/t0_provider_sdk"]
```

### 1.3 Starter `starter/pyproject.toml`
```toml
[project]
name = "t0-provider-starter"
version = "0.1.0"
requires-python = ">=3.13"
dependencies = ["coincurve>=21.0", "click>=8.1"]

[project.scripts]
t0-provider-starter = "t0_provider_starter.cli:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/t0_provider_starter"]
force-include = {"src/t0_provider_starter/template" = "t0_provider_starter/template"}
```

### 1.4 `.gitignore`, `CLAUDE.md`, `.mcp.json`

---

## Step 2: Proto Setup & Code Generation

### 2.1 Copy protos from Go SDK
Source: `/Users/stepan_romankov/t-0/provider-sdk-go/proto/`
Target: `sdk/src/t0_provider_sdk/proto/`

Files to copy:
- `tzero/v1/payment/provider.proto`
- `tzero/v1/payment/network.proto`
- `tzero/v1/common/common.proto`
- `tzero/v1/common/payment_method.proto`
- `tzero/v1/common/payment_receipt.proto`
- `tzero/v1/payment_intent/provider/provider.proto` (if exists)
- `tzero/v1/payment_intent/recipient/recipient.proto` (if exists)
- `ivms101/v1/ivms/ivms101.proto`
- `ivms101/v1/ivms/enum.proto` (if exists)

### 2.2 `sdk/buf.yaml`
```yaml
version: v2
modules:
  - path: src/t0_provider_sdk/proto
deps:
  - buf.build/bufbuild/protovalidate
```

### 2.3 `sdk/buf.gen.yaml`
```yaml
version: v2
managed:
  enabled: true
plugins:
  - protoc_builtin: python
    out: src/t0_provider_sdk/api
  - protoc_builtin: pyi
    out: src/t0_provider_sdk/api
  - local: protoc-gen-connect-python
    out: src/t0_provider_sdk/api
```

### 2.4 Run `buf generate` and commit generated code

Generated files will include:
- `*_pb2.py` - Protobuf message classes
- `*_pb2.pyi` - Type stubs
- `*_connect.py` - ConnectRPC service base, ASGI/WSGI app, client classes

---

## Step 3: Crypto Module

**Go reference:** `/Users/stepan_romankov/t-0/provider-sdk-go/crypto/`

### 3.1 `hash.py` - Keccak256

**Go equivalent:** `crypto/hash.go` → `LegacyKeccak256(data)`

```python
from Crypto.Hash import keccak  # pycryptodome

def legacy_keccak256(data: bytes) -> bytes:
    h = keccak.new(digest_bits=256)
    h.update(data)
    return h.digest()  # 32 bytes
```

**CRITICAL:** Must use `pycryptodome`'s `Crypto.Hash.keccak`, NOT `hashlib.sha3_256()` (different padding). `pysha3` is incompatible with Python 3.13.

### 3.2 `keys.py` - Key Conversion

**Go equivalent:** `crypto/helper.go`

| Go function | Python function |
|---|---|
| `GetPrivateKeyFromHex(hex)` | `private_key_from_hex(hex) -> PrivateKey` |
| `GetPublicKeyFromHex(hex)` | `public_key_from_hex(hex) -> PublicKey` |
| `GetPublicKeyBytes(pubKey)` | `public_key_to_bytes(key) -> bytes` (65 bytes uncompressed) |
| `GetPublicKeyFromBytes(data)` | `public_key_from_bytes(data) -> PublicKey` |

Uses `coincurve.PrivateKey` and `coincurve.PublicKey`. All hex strings support `0x` prefix.

### 3.3 `signer.py` - ECDSA Signing

**Go equivalent:** `crypto/sign.go` → `SignFn`, `NewSigner`, `sign(digest, privateKey)`

**Protocol type:**
```python
class SignFn(Protocol):
    def __call__(self, digest: bytes) -> tuple[bytes, bytes]: ...
    # Returns (signature_65_bytes, public_key_65_bytes)
```

**Implementation:**
```python
def new_signer(private_key: PrivateKey) -> SignFn:
    pub_key_bytes = private_key.public_key.format(compressed=False)  # 65 bytes

    def sign(digest: bytes) -> tuple[bytes, bytes]:
        sig = private_key.sign_recoverable(digest, hasher=None)
        # coincurve returns: r(32) + s(32) + recovery_id(1) = 65 bytes
        # This IS the Ethereum format (same as Go's output after rearranging SignCompact)
        return sig, pub_key_bytes

    return sign
```

**Key details:**
- `hasher=None` → no internal hashing (we pre-hash with Keccak256)
- coincurve's `sign_recoverable` returns `r(32) || s(32) || v(1)` = 65 bytes
- Go's `sign()` rearranges SignCompact's `[v+27][r][s]` → `[r][s][v-27]` = same format

### 3.4 `verifier.py` - Signature Verification

**Go equivalent:** `crypto/verify_signature.go` → `VerifySignature(pubKey, digest, signature[:64])`

**Two approaches available with coincurve:**

**Approach A (Recovery-based, preferred):**
```python
def verify_signature(public_key: PublicKey, digest: bytes, signature: bytes) -> bool:
    if len(digest) != 32 or len(signature) not in (64, 65):
        return False

    if len(signature) == 64:
        # Try both recovery IDs (0 and 1) since we don't have v
        for v in (0, 1):
            recoverable_sig = signature + bytes([v])
            try:
                recovered = PublicKey.from_signature_and_message(
                    recoverable_sig, digest, hasher=None
                )
                if recovered.format(compressed=False) == public_key.format(compressed=False):
                    return True
            except Exception:
                continue
        return False
    else:
        # 65 bytes: use directly
        try:
            recovered = PublicKey.from_signature_and_message(
                signature, digest, hasher=None
            )
            return recovered.format(compressed=False) == public_key.format(compressed=False)
        except Exception:
            return False
```

**Approach B (DER conversion via coincurve internals):**
```python
from coincurve.ecdsa import deserialize_compact, cdata_to_der

def verify_signature(public_key: PublicKey, digest: bytes, signature: bytes) -> bool:
    if len(digest) != 32 or len(signature) not in (64, 65):
        return False
    sig_compact = signature[:64]
    cdata = deserialize_compact(sig_compact)
    der_sig = cdata_to_der(cdata)
    return public_key.verify(der_sig, digest, hasher=None)
```

**Decision:** Use Approach A (recovery-based). It uses only public API methods and is more robust. Go's verification also extracts r+s and verifies, but the recovery-based approach is equivalent and more Pythonic.

**CRITICAL user note about recovery byte:** Go SDK's `VerifySignature()` only uses `signature[:64]` (strips v). Python must do the same when receiving 65-byte signatures.

---

## Step 4: Common Module

### 4.1 `headers.py`

**Go equivalent:** `common/header.go`

```python
SIGNATURE_HEADER = "X-Signature"
SIGNATURE_TIMESTAMP_HEADER = "X-Signature-Timestamp"
PUBLIC_KEY_HEADER = "X-Public-Key"
```

---

## Step 5: Network Module (Client-side)

**Go reference:** `/Users/stepan_romankov/t-0/provider-sdk-go/network/`

### 5.1 `signing.py` - Signing HTTP Transport

**Go equivalent:** `network/signing_transport.go` → `SigningTransport.RoundTrip(req)`

**Architecture:** ConnectRPC Python uses `pyqwest.Client` (async) and `pyqwest.SyncClient` (sync) as HTTP backends. The generated client constructors accept `http_client` parameter:
- `ConnectClient.__init__(..., http_client: pyqwest.Client | None = None)`
- `ConnectClientSync.__init__(..., http_client: pyqwest.SyncClient | None = None)`

We create wrapper classes that delegate to real pyqwest clients after adding signature headers.

**pyqwest API (verified from https://pyqwest.dev/api/):**
```python
# Async Client
class Client:
    def __init__(self, transport: Transport | None = None): ...
    async def get(self, url, headers=None) -> Response: ...
    async def post(self, url, headers=None, content=None) -> Response: ...
    async def stream(self, method, url, headers=None, content=None) -> AsyncContextManager[Response]: ...

# Sync Client
class SyncClient:
    def __init__(self, transport: SyncTransport | None = None): ...
    def get(self, url, headers=None, timeout=None) -> Response: ...
    def post(self, url, headers=None, content=None, timeout=None) -> Response: ...
    def stream(self, method, url, headers=None, content=None, timeout=None) -> ContextManager[Response]: ...

# Headers
class Headers:
    def __init__(self, items=None): ...  # mapping or iterable of tuples
    headers[key] = value  # set/replace
    headers.add(key, value)  # add preserving existing
```

**ConnectRPC uses exactly 3 methods on client:** `post()`, `get()`, `stream()` (verified from source).

**Implementation:**
```python
class SigningClient:
    """Async signing wrapper for pyqwest.Client. Passed to ConnectRPC as http_client."""

    def __init__(self, sign_fn: SignFn, *, transport: Transport | None = None):
        self._inner = pyqwest.Client(transport=transport)
        self._sign_fn = sign_fn

    async def get(self, url, headers=None):
        headers = self._sign(b"", headers)
        return await self._inner.get(url, headers=headers)

    async def post(self, url, headers=None, content=None):
        body = content or b""
        headers = self._sign(body, headers)
        return await self._inner.post(url, headers=headers, content=content)

    def stream(self, method, url, headers=None, content=None):
        body = content or b""
        headers = self._sign(body, headers)
        return self._inner.stream(method, url, headers=headers, content=content)

    def _sign(self, body: bytes, headers: Headers | None) -> Headers:
        timestamp_ms = int(time.time() * 1000)
        timestamp_bytes = struct.pack("<Q", timestamp_ms)
        digest = legacy_keccak256(body + timestamp_bytes)
        signature, pub_key = self._sign_fn(digest)
        if headers is None:
            headers = pyqwest.Headers()
        headers[PUBLIC_KEY_HEADER] = f"0x{pub_key.hex()}"
        headers[SIGNATURE_HEADER] = f"0x{signature.hex()}"
        headers[SIGNATURE_TIMESTAMP_HEADER] = str(timestamp_ms)
        return headers
```

`SigningSyncClient` follows the same pattern wrapping `pyqwest.SyncClient`, with identical `_sign()` logic.

**Key design decisions:**
- Wrapper pattern (not subclass) — pyqwest.Client is Rust-backed, subclassing is fragile
- Same pattern as Go's `SigningTransport` wrapping `http.RoundTripper`
- `_sign()` is shared logic, extracted as method (DRY between get/post/stream)
- `stream()` content is signed synchronously before stream starts (body is known upfront for ConnectRPC)
- No httpx dependency needed — pyqwest API is clean and sufficient

### 5.2 `client.py` - Generic Client Factory

**Go equivalent:** `network/client.go` → `NewServiceClient[T]`

```python
T = TypeVar("T")

def new_service_client(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
) -> T:
    """Create a ConnectRPC client with signing transport. Works with both async and sync clients."""
    sign_fn = new_signer_from_hex(private_key)
    # Detect async vs sync client and use appropriate signing wrapper
    # ConnectClient uses pyqwest.Client, ConnectClientSync uses pyqwest.SyncClient
    signing_client = SigningClient(sign_fn)       # for async
    # OR: SigningSyncClient(sign_fn)              # for sync
    return client_class(base_url, http_client=signing_client, timeout_ms=int(timeout * 1000))
```

**Two variants for async/sync:**
```python
def new_service_client(private_key, client_class, *, base_url=..., timeout=...) -> T:
    """For async ConnectClient subclasses. Returns client with SigningClient."""

def new_service_client_sync(private_key, client_class, *, base_url=..., timeout=...) -> T:
    """For sync ConnectClientSync subclasses. Returns client with SigningSyncClient."""
```

**Proto-agnostic:** Works with ANY generated ConnectRPC client class. The `client_class` parameter is the generated `NetworkServiceClient` (async) or `NetworkServiceClientSync` (sync).

**Verified ConnectRPC client constructor signatures:**
- Async: `ConnectClient(address, *, http_client: pyqwest.Client | None = None, timeout_ms: int | None = None, interceptors: Iterable[Interceptor] = (), ...)`
- Sync: `ConnectClientSync(address, *, http_client: pyqwest.SyncClient | None = None, timeout_ms: int | None = None, interceptors: Iterable[InterceptorSync] = (), ...)`

### 5.3 `options.py`
```python
DEFAULT_BASE_URL = "https://api.t-0.network"
DEFAULT_TIMEOUT = 15.0
```

---

## Step 6: Provider Module (Server-side)

**Go reference:** `/Users/stepan_romankov/t-0/provider-sdk-go/provider/`

### 6.1 `middleware.py` - ASGI Signature Verification Middleware

**Go equivalent:** `provider/verify_signature.go` → `newSignatureVerifierMiddleware()`

This is the most critical component. Must read RAW body bytes BEFORE ConnectRPC deserializes them.

**Architecture:**
```
ASGI Request → SignatureVerificationMiddleware → ConnectRPC ASGI App
                      ↓                                ↓
               Read raw body                    SignatureErrorInterceptor
               Verify signature                 (converts stored errors to
               Store error in contextvars       ConnectRPC error codes)
               Replay body to downstream
```

**Implementation pattern:**
1. Intercept ASGI `receive` callable to buffer entire body
2. Parse headers: `X-Signature`, `X-Public-Key`, `X-Signature-Timestamp`
3. Validate timestamp within ±60 seconds
4. Verify public key matches network key
5. Compute `Keccak256(body + timestamp_LE_8bytes)`
6. Verify signature against digest
7. On error: store in `contextvars.ContextVar` (Python equivalent of Go's `context.WithValue`)
8. Create synthetic `receive` that replays buffered body
9. Pass through to downstream ConnectRPC app

**Key Go patterns replicated:**
- `readBodyWithCap(req, maxBodySizeOpt)` → `_read_body(receive)` with size check
- `parseRequiredHexedHeader(name, headers)` → `_parse_hex_header(headers, name)` (strips `0x`, hex-decodes)
- `parseTimestamp(headers)` → `_parse_timestamp(headers)` (returns `(ms_int, le_8bytes)`)
- `timesWithinDelta(t1, t2, delta)` → `abs(now_ms - ts_ms) <= 60_000`
- `context.WithValue(ctx, key, error)` → `signature_error_var.set(error_dict)`

### 6.2 `interceptor.py` - ConnectRPC Error Interceptor

**Go equivalent:** `provider/signature_error.go`

Reads the error stored by middleware via `contextvars.ContextVar` and raises `ConnectError` with appropriate code:
- `"invalid_argument"` → `Code.INVALID_ARGUMENT`
- `"unauthenticated"` → `Code.UNAUTHENTICATED`

### 6.3 `handler.py` - Generic Handler Registration

**Go equivalent:** `provider/handler.go` → `Handler[T]` + `NewHttpHandler()`

Two main functions:

```python
def handler(
    asgi_app_factory: Callable,  # e.g. ProviderServiceASGIApplication
    service_impl: T,             # User's service implementation
    *options: HandlerOption,
) -> BuildHandler:
    """Register a service handler. Proto-agnostic via Callable type."""

def new_asgi_app(
    network_public_key: str,
    *build_handlers: BuildHandler,
) -> ASGIApp:
    """Create ASGI app with signature verification. Framework-agnostic."""
```

**Go → Python mapping:**
| Go | Python |
|---|---|
| `Handler[T any](factory, impl, opts...)` | `handler(asgi_app_factory, service_impl, *opts)` |
| `NewHttpHandler(pubkey, handlers...)` | `new_asgi_app(pubkey, *handlers)` |
| `http.NewServeMux()` | ASGI app composition via ConnectRPC's `.path` property |
| `providerHandlerOptions` | `_HandlerOptions` dataclass |

**Multiple services:** Use ConnectRPC's generated `.path` property + Starlette `Mount` or custom ASGI router.

**Generated ConnectRPC Python code pattern (verified):**
```python
# Generated from provider.proto:
class ProviderService(Protocol):  # Service base class to implement
    async def pay_out(self, request: PayoutRequest, ctx: RequestContext) -> PayoutResponse: ...
    async def update_payment(self, request: UpdatePaymentRequest, ctx: RequestContext) -> UpdatePaymentResponse: ...
    # etc.

class ProviderServiceASGIApplication(ConnectASGIApplication[ProviderService]):
    def __init__(self, service, *, interceptors=(), read_max_bytes=None, compressions=None): ...
    @property
    def path(self) -> str: ...  # e.g. "/tzero.v1.payment.ProviderService"

class NetworkServiceClient(ConnectClient):
    async def update_quote(self, request, *, headers=None, timeout_ms=None) -> ...: ...
    # etc.

class NetworkServiceClientSync(ConnectClientSync):
    def update_quote(self, request, *, headers=None, timeout_ms=None) -> ...: ...
```

### 6.4 `errors.py`
Error hierarchy: `SignatureVerificationError` base, with `MissingRequiredHeaderError`, `InvalidHeaderEncodingError`, `UnknownPublicKeyError`, `SignatureFailedError`, `TimestampOutOfRangeError`.

---

## Step 7: Starter CLI

**Go reference:** `/Users/stepan_romankov/t-0/provider-starter-go/main.go`

### 7.1 `cli.py` - CLI Entry Point

**Invocation:** `uvx t0-provider-starter my_provider`

Workflow:
1. Parse `project_name` argument (via `click`)
2. Validate target directory (empty or non-existent)
3. Generate secp256k1 keypair via `coincurve.PrivateKey()`
4. Copy template directory to target
5. Process `.template` files (replace `{{PROJECT_NAME}}`)
6. Write `.env` with generated keys from `.env.example`
7. Print public key + next steps

### 7.2 `keygen.py`
```python
def generate_keypair() -> tuple[str, str]:
    key = PrivateKey()
    return ("0x" + key.secret.hex(), "0x" + key.public_key.format(compressed=False).hex())
```

---

## Step 8: Starter Template

**Go reference:** `/Users/stepan_romankov/t-0/provider-starter-go/template/`

### 8.1 `template/src/provider/main.py`

Mirrors Go's `template/cmd/main.go` initialization flow:

```python
async def main():
    config = load_config()                              # loadConfig()
    network_client = new_service_client(                # initNetworkClient()
        config.provider_private_key,
        NetworkServiceClient,
        base_url=config.tzero_endpoint,
    )
    service = ProviderServiceImplementation(network_client)
    app = new_asgi_app(                                 # startProviderServer()
        config.network_public_key,
        handler(ProviderServiceASGIApplication, service),
    )
    # Start quote publishing
    asyncio.create_task(publish_quotes(network_client))
    # Run ASGI server
    uvicorn.run(app, host="0.0.0.0", port=config.port)
```

### 8.2 `template/src/provider/config.py`
Loads from `.env` via `python-dotenv`: `NETWORK_PUBLIC_KEY`, `PROVIDER_PRIVATE_KEY`, `TZERO_ENDPOINT`, `PORT`.

### 8.3 `template/src/provider/handler/payment.py`
Implements `ProviderService` (generated base class). All methods return empty responses with TODO comments matching Go's numbered steps.

### 8.4 `template/src/provider/publish_quotes.py`
Publishes sample quotes every 5 seconds via `network_client.update_quote()`.

### 8.5 `template/.env.example`
```
PROVIDER_PRIVATE_KEY=your_private_key_here
PORT=8080
TZERO_ENDPOINT=https://api-sandbox.t-0.network
NETWORK_PUBLIC_KEY=0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b
```

### 8.6 `template/Dockerfile`
Multi-stage build with `python:3.13-slim`, uv for dependencies, `uvicorn` as entrypoint.

---

## Step 9: Testing

### 9.1 Crypto Tests (with Go test vectors)

**Go test vectors to reuse:**
- Private key: `0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8`
- Expected public key: `0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0`
- Second key pair from `helper_test.go`: private=`0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92`, public=`0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7`

Tests:
- `test_hash.py`: Keccak256 against known vectors, verify NOT equal to SHA3-256
- `test_keys.py`: Key from hex, round-trip, public key derivation matches Go vectors
- `test_signer.py`: Sign + verify round-trip, signature format (65 bytes), v byte in {0,1}
- `test_verifier.py`: Verify with known Go-produced signatures, wrong key fails, wrong digest fails, 64-byte and 65-byte signatures both work

### 9.2 Provider Tests

**Mirror Go's `verify_signature_test.go` cases:**
- Valid request with all headers → success
- Missing X-Public-Key → INVALID_ARGUMENT
- Missing X-Signature → INVALID_ARGUMENT
- Missing X-Signature-Timestamp → INVALID_ARGUMENT
- Invalid hex encoding → INVALID_ARGUMENT
- Header too short → INVALID_ARGUMENT
- Timestamp too old (>60s) → INVALID_ARGUMENT
- Timestamp too new (>60s) → INVALID_ARGUMENT
- Wrong public key → UNAUTHENTICATED
- Invalid signature → UNAUTHENTICATED
- Empty body → success
- Body size exceeds max → INVALID_ARGUMENT

### 9.3 Network Tests
- Signing transport adds correct headers
- Timestamp format (milliseconds, decimal string)
- Signature is verifiable
- Body bytes unchanged after signing

### 9.4 Integration Test
Full end-to-end: Create ASGI app with middleware → send signed request via signing transport → verify it succeeds. Also test with wrong key → fails.

### 9.5 Cross-Test with Go (one-time)

Located in `tests/cross_test/`. NOT part of regular test suite.

**Pattern:**
1. Hardcode shared test vectors (private key, message, expected Keccak256 hash)
2. Python signs → small Go program verifies (via subprocess)
3. Go signs → Python verifies (hardcoded Go-produced signature)
4. Both produce same Keccak256 hash for same input

After validation, Python relies on its own test suite with shared vectors.

---

## Step 10: CI/CD & Publishing

### 10.1 `.github/workflows/test.yaml`
- Trigger on push to main and PRs
- `uv sync --all-packages`
- `uv run ruff check .`
- `uv run mypy sdk/src starter/src`
- `uv run pytest -v`

### 10.2 `.github/workflows/publish.yaml`
- Trigger on release published
- Build both packages: `uv build --package t0-provider-sdk` + `uv build --package t0-provider-starter`
- Publish to PyPI + GitHub Packages

---

## Implementation Order

| # | Task | Dependencies | Key Files |
|---|------|-------------|-----------|
| 1 | Repository scaffolding | None | `pyproject.toml`, `.gitignore`, `CLAUDE.md` |
| 2 | Proto setup + code generation | Step 1 | `buf.yaml`, `buf.gen.yaml`, `api/` |
| 3 | Crypto module + tests | Step 1 | `crypto/*.py`, `tests/crypto/` |
| 4 | Common module | Step 1 | `common/headers.py` |
| 5 | Network module + tests | Steps 3, 4 | `network/*.py`, `tests/network/` |
| 6 | Provider module + tests | Steps 3, 4 | `provider/*.py`, `tests/provider/` |
| 7 | SDK public API | Steps 2-6 | `sdk/__init__.py` |
| 8 | Integration tests | Steps 5, 6 | `tests/integration/` |
| 9 | Starter CLI + tests | Step 3 | `starter/src/`, `starter/tests/` |
| 10 | Starter template | Steps 2-7 | `starter/src/.../template/` |
| 11 | Cross-tests with Go | Steps 3-8 | `tests/cross_test/` |
| 12 | CI/CD + publishing | All | `.github/workflows/` |
| 13 | CLAUDE.md + docs/PLAN.md | All | Project root |

---

## Verification Plan

### Unit Tests
```bash
uv sync --all-packages
uv run pytest sdk/tests -v
uv run pytest starter/tests -v
```

### Type Checking
```bash
uv run mypy sdk/src starter/src --strict
```

### Linting
```bash
uv run ruff check .
```

### Integration Test (end-to-end)
```bash
uv run pytest sdk/tests/integration/ -v
```

### Cross-Test with Go
```bash
# From provider-python directory
cd tests/cross_test/go_helper && go build -o go_helper . && cd ../../..
uv run pytest tests/cross_test/ -v
```

### Starter CLI Test
```bash
# Test project generation
uvx --from ./starter t0-provider-starter test_project
cd test_project && uv sync && uv run python -m provider.main
```

### Manual Verification
1. Start Go SDK server on port 8081
2. Start Python SDK server on port 8080
3. Python client → Go server (signed request should succeed)
4. Go client → Python server (signed request should succeed)

---

## Critical Reference Files

| Purpose | Go file | Python equivalent |
|---------|---------|-------------------|
| Generic handler | `provider-sdk-go/provider/handler.go` | `sdk/.../provider/handler.py` |
| Signature middleware | `provider-sdk-go/provider/verify_signature.go` | `sdk/.../provider/middleware.py` |
| Signing transport | `provider-sdk-go/network/signing_transport.go` | `sdk/.../network/signing.py` |
| Generic client | `provider-sdk-go/network/client.go` | `sdk/.../network/client.py` |
| ECDSA signing | `provider-sdk-go/crypto/sign.go` | `sdk/.../crypto/signer.py` |
| Signature verify | `provider-sdk-go/crypto/verify_signature.go` | `sdk/.../crypto/verifier.py` |
| Keccak256 | `provider-sdk-go/crypto/hash.go` | `sdk/.../crypto/hash.py` |
| Header constants | `provider-sdk-go/common/header.go` | `sdk/.../common/headers.py` |
| Starter main | `provider-starter-go/main.go` | `starter/.../cli.py` |
| Template entry | `provider-starter-go/template/cmd/main.go` | `starter/.../template/.../main.py` |
| Proto definitions | `provider-sdk-go/proto/` | `sdk/.../proto/` |
| Buf config | `provider-sdk-go/buf.gen.yaml` | `sdk/buf.gen.yaml` |
