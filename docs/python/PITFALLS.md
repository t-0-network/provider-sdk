# Pitfalls & Wrong Directions

Lessons from the initial implementation session. Saves future debugging time.

---

## 1. Keccak256 ≠ SHA3-256

| Approach | Result |
|----------|--------|
| `hashlib.sha3_256()` | **WRONG** — SHA3-256 uses different padding (NIST) than Keccak256 (pre-NIST). Produces different hashes. Go SDK uses pre-NIST Keccak256. |
| `pysha3` | **BROKEN** — incompatible with Python 3.13, fails to compile. |
| `pycryptodome` `Crypto.Hash.keccak` | **CORRECT** — `keccak.new(digest_bits=256)` produces Ethereum-compatible Keccak256. |

```python
# WRONG
import hashlib
hashlib.sha3_256(data).digest()  # Different padding, different output

# CORRECT
from Crypto.Hash import keccak
keccak.new(digest_bits=256, data=data).digest()
```

---

## 2. `connectrpc` PyPI Package Trap

| Package | PyPI name | Version | Status |
|---------|-----------|---------|--------|
| ConnectRPC official | `connect-python` | 0.8.x | **CORRECT** — imports as `connectrpc` |
| Gaudiy's package | `connectrpc` | 0.0.1 | **WRONG** — different, unmaintained package |

Always `pip install connect-python`, never `pip install connectrpc`.

---

## 3. `buf.validate` Dependency — 3 Failed Approaches

Generated protobuf code imports `from buf.validate import validate_pb2`. Resolving this was the hardest dependency issue.

### Attempt 1: `protovalidate` PyPI package
```bash
uv add --package t0-provider-sdk protovalidate
```
**Failed** — `protovalidate` itself needs `buf.validate` stubs, doesn't ship them.

### Attempt 2: BSR (Buf Schema Registry) generated SDK
```bash
uv pip install --extra-index-url https://buf.build/gen/python \
  bufbuild-protovalidate-protocolbuffers-python
```
**Worked standalone** but couldn't be added to `pyproject.toml` reproducibly.

### Attempt 3: BSR as `uv` index
```toml
# pyproject.toml
[[tool.uv.index]]
name = "buf"
url = "https://buf.build/gen/python"
```
**Failed** — `uv sync` hangs indefinitely. BSR's package index is apparently not fully compatible with PEP 503 / uv's resolver.

### Solution: `buf generate --include-imports`
```bash
cd sdk && buf generate --include-imports
```
Generates `buf/validate/validate_pb2.py` locally inside `api/`. Then:
1. Create `api/buf/__init__.py` and `api/buf/validate/__init__.py`
2. Add `api/` dir to `sys.path` in SDK `__init__.py` (see pitfall #5)

**No external dependency needed.** Remove `protovalidate` from dependencies.

### buf.gen.yaml — managed.disable
Add to prevent buf from managing third-party proto options:
```yaml
managed:
  enabled: true
  disable:
    - module: buf.build/googleapis/googleapis
    - module: buf.build/bufbuild/protovalidate
```

---

## 4. Generated Proto Imports Need `sys.path` Hack

Generated code uses **absolute top-level imports**:
```python
from tzero.v1.common import common_pb2      # not t0_provider_sdk.api.tzero...
from buf.validate import validate_pb2        # not t0_provider_sdk.api.buf...
```

These packages live inside `sdk/src/t0_provider_sdk/api/` but Python doesn't know that.

**Fix** in `sdk/src/t0_provider_sdk/__init__.py`:
```python
import sys
from pathlib import Path

_api_dir = str(Path(__file__).parent / "api")
if _api_dir not in sys.path:
    sys.path.insert(0, _api_dir)
```

This must execute before any proto imports. Works for both editable installs and installed packages.

---

## 5. `subprocess.run()` Blocks asyncio Event Loop

In cross-tests, running a Go client via `subprocess.run()` while a Python uvicorn server is running in the same event loop **blocks the loop entirely**. The server never processes the Go client's request → timeout.

```python
# WRONG — blocks event loop, server can't respond
result = subprocess.run(["./go_helper", "call-pay-out", ...], capture_output=True, timeout=15)

# CORRECT — non-blocking subprocess
proc = await asyncio.create_subprocess_exec(
    "./go_helper", "call-pay-out", ...,
    stdout=asyncio.subprocess.PIPE,
    stderr=asyncio.subprocess.PIPE,
)
stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=15)
```

Similarly, port-readiness checks must be async:
```python
# WRONG
def _wait_for_port(port, timeout=10):
    while True:
        try:
            socket.create_connection(("127.0.0.1", port), timeout=1)
            return
        except OSError:
            time.sleep(0.1)  # blocks event loop

# CORRECT
async def _async_wait_for_port(port, timeout=10):
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        try:
            _, writer = await asyncio.open_connection("127.0.0.1", port)
            writer.close()
            await writer.wait_closed()
            return
        except OSError:
            await asyncio.sleep(0.1)
```

---

## 6. pytest Collects Classes with `Test` Prefix

Any class named `TestSomething` with an `__init__` causes:
```
PytestCollectionWarning: cannot collect test class 'TestProviderService'
because it has a __init__ constructor
```

**Fix**: prefix non-test classes with `_`:
```python
# WRONG
class TestProviderService:  # pytest tries to collect this
    def __init__(self, network_client): ...

# CORRECT
class _ProviderService:     # underscore prefix, pytest ignores
    def __init__(self, network_client): ...
```

---

## 7. `tests/__init__.py` Breaks pytest Import Resolution

Adding `__init__.py` to `tests/` and `tests/cross_test/` causes:
```
ModuleNotFoundError: No module named 'tests.cross_test'
```

pytest's default import mode (`importlib`) conflicts with treating `tests` as a regular package. The `tests` directory is a namespace, not a package.

**Fix**: Don't create `__init__.py` in `tests/` or `tests/cross_test/`. Only SDK's internal `sdk/tests/` has `__init__.py` files.

---

## 8. Protobuf Non-Canonical Encoding

**Critical for signatures.** Re-encoding a deserialized protobuf message produces **different bytes** from the original wire format. Field ordering, unknown fields, default values — all can change.

```python
# WRONG — signature verification will fail
msg = SomeMessage()
msg.ParseFromString(body)
digest = keccak256(msg.SerializeToString() + timestamp_bytes)  # different bytes!
verify_signature(pub_key, digest, sig)

# CORRECT — use original wire bytes
digest = keccak256(raw_body_bytes + timestamp_bytes)
verify_signature(pub_key, digest, sig)
```

The ASGI middleware intercepts raw body bytes from `receive()` and the WSGI middleware reads from `environ["wsgi.input"]` BEFORE ConnectRPC deserializes them. This is by design — never re-serialize for signature operations.

---

## 9. ConnectRPC Python: `Interceptor` Is a Union Type

```python
# WRONG — no base class to inherit from
class MyInterceptor(Interceptor):  # TypeError: cannot subclass Union
    ...

# CORRECT — implement the UnaryInterceptor Protocol
class MyInterceptor:
    async def intercept_unary(self, call_next, request, ctx):
        # ... pre-processing
        response = await call_next(request, ctx)
        # ... post-processing
        return response
```

`Interceptor = UnaryInterceptor | StreamInterceptor` — it's a type alias, not a class.

---

## 10. `pyqwest.Client` — Wrapper, Not Subclass

`pyqwest.Client` is Rust-backed (via PyO3). Subclassing fails or produces unpredictable behavior.

```python
# WRONG — Rust-backed class, subclassing is fragile
class SigningClient(pyqwest.Client):
    def post(self, url, headers=None, content=None):
        headers = self._sign(content, headers)
        return super().post(url, headers=headers, content=content)

# CORRECT — wrapper/delegation pattern
class SigningClient:
    def __init__(self, sign_fn):
        self._inner = pyqwest.Client()
        self._sign_fn = sign_fn

    async def post(self, url, headers=None, content=None):
        headers = self._sign(content or b"", headers)
        return await self._inner.post(url, headers=headers, content=content)
```

ConnectRPC calls exactly 3 methods: `get()`, `post()`, `stream()`. Only these need wrapping.

---

## 11. Signature Byte Format: coincurve vs Go

| SDK | Format | Layout |
|-----|--------|--------|
| Go (`btcec.SignCompact`) | `[v+27, r(32), s(32)]` → rearranged to `[r(32), s(32), v-27]` | 65 bytes |
| Python (`coincurve.sign_recoverable`) | `[r(32), s(32), v]` | 65 bytes |

Both produce the same final format: `r(32) || s(32) || v(1)` where `v ∈ {0, 1}`.

Go's `VerifySignature()` uses only `signature[:64]` (strips v). Python verification must handle both 64-byte (try v=0 and v=1) and 65-byte signatures.

---

## 12. `buf generate` Without `--include-imports`

Running `buf generate` without `--include-imports` does NOT generate stubs for dependencies like `buf.validate`. You get runtime `ModuleNotFoundError`.

```bash
# WRONG — missing buf/validate/validate_pb2.py
cd sdk && buf generate

# CORRECT — generates all transitive proto dependencies
cd sdk && buf generate --include-imports
```

Must also create `__init__.py` files for generated namespace packages (`api/buf/__init__.py`, `api/buf/validate/__init__.py`).

---

## 13. WSGI Body Replay: Must Replace `wsgi.input`

WSGI middleware that reads `environ["wsgi.input"]` consumes the stream — the downstream app gets an empty body. After reading, you must replace `wsgi.input` with a `BytesIO` and update `CONTENT_LENGTH`.

```python
# WRONG — downstream app gets empty body
body = environ["wsgi.input"].read()
# ... verify signature ...
return app(environ, start_response)  # app reads empty stream

# CORRECT — replay body via BytesIO
body = environ["wsgi.input"].read()
# ... verify signature ...
environ["wsgi.input"] = io.BytesIO(body)
environ["CONTENT_LENGTH"] = str(len(body))
return app(environ, start_response)
```

The WSGI middleware (`middleware_wsgi.py`) handles this automatically. This is the WSGI equivalent of the ASGI `_replay_receive()` pattern.

---

## 14. WSGI Headers: `HTTP_` Prefix in `environ`

WSGI stores HTTP headers with a `HTTP_` prefix and underscores instead of hyphens: `X-Public-Key` becomes `HTTP_X_PUBLIC_KEY`. `Content-Type` and `Content-Length` are special cases with no prefix.

```python
# WRONG — looking for the header directly
pub_key = environ.get("X-Public-Key")  # None

# CORRECT — WSGI format
pub_key = environ.get("HTTP_X_PUBLIC_KEY")
```

The `_parse_wsgi_headers()` function in `middleware_wsgi.py` converts all `HTTP_*` keys to lowercase-hyphenated format for compatibility with the shared `_verify_request()` logic.

---

## Quick Reference: Dependency Gotchas

| Want | Don't Use | Use Instead | Why |
|------|-----------|-------------|-----|
| Keccak256 | `pysha3`, `hashlib.sha3_256` | `pycryptodome` (`Crypto.Hash.keccak`) | py3.13 compat, correct padding |
| ConnectRPC | `connectrpc` (PyPI) | `connect-python` (PyPI) | Wrong package on PyPI |
| buf.validate stubs | `protovalidate`, BSR index | `buf generate --include-imports` | Self-contained, no external index |
| Async subprocess | `subprocess.run()` | `asyncio.create_subprocess_exec()` | Non-blocking in event loop |
