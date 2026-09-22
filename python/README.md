# T-0 Provider SDK -- Python

Python SDK for building T-0 Network payment provider integrations. The SDK provides ConnectRPC communication, secp256k1 cryptographic signing/verification, and ASGI/WSGI middleware for signature validation.

## Prerequisites

- Python >= 3.13
- [uv](https://docs.astral.sh/uv/) -- dependency management and runner
- Docker (optional, for containerized deployment)

## Quick Start

Scaffold a new provider project:

```bash
t0-init init --lang=python my_provider
```

Installation and options: [cli/README.md](../cli/README.md). What `init` creates: [starter template README](starter/template/README.md).

## Installation

To use the SDK directly without the starter CLI:

```bash
uv add t0-provider-sdk
```

Or with pip:

```bash
pip install t0-provider-sdk
```

## WSGI Alternative

The default generated project uses async ASGI (uvicorn). If you prefer a synchronous WSGI server (e.g. gunicorn, waitress), replace the ASGI setup in `src/provider/main.py`:

```python
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import ProviderServiceWSGIApplication
from t0_provider_sdk.provider import handler_sync, new_wsgi_app
from provider.handler.payment_sync import ProviderServiceSyncImplementation


def create_provider_app(config, network_client_sync):
    service = ProviderServiceSyncImplementation(network_client_sync)
    return new_wsgi_app(
        config.network_public_key,
        handler_sync(ProviderServiceWSGIApplication, service),
    )
```

Expose the application at module level and run with a WSGI server (`pip install gunicorn` or add it to `pyproject.toml`):

```python
# src/provider/wsgi.py
from provider.config import load_config
from provider.main import create_provider_app
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClientSync
from t0_provider_sdk.network.client import new_service_client_sync

config = load_config()
network_client_sync = new_service_client_sync(
    config.provider_private_key,
    NetworkServiceClientSync,
    base_url=config.tzero_endpoint,
)
app = create_provider_app(config, network_client_sync)
```

```bash
gunicorn provider.wsgi:app --bind 0.0.0.0:8080
```

The sync variant uses `payment_sync.py` -- implement the same RPC methods as regular `def` functions instead of `async def`.

## Available Commands

```bash
uv run python -m provider.main    # Start the provider server
uv run pytest                     # Run tests
uv run ruff check .               # Lint
```

## Deployment

```bash
docker build -t my-provider .
docker run --env-file .env -p 8080:8080 my-provider
```

## Troubleshooting

**`PROVIDER_PRIVATE_KEY is not set in .env`** -- `.env` is generated with a fresh key next to `pyproject.toml`; run commands from that directory. To generate a new key, run `t0-init keygen` and set `PROVIDER_PRIVATE_KEY` to the private key it prints (see [`cli/README.md`](../cli/README.md)).

**`ModuleNotFoundError: No module named 'provider'`** -- Run `uv sync` in the generated project directory. The project uses a `src/` layout that requires installation.

**Signature verification failures** -- Ensure the server clock is synchronized (NTP). Timestamps outside +/- 60 seconds are rejected. Verify that `NETWORK_PUBLIC_KEY` matches the key provided by the T-0 team.

**ConnectRPC PyPI package** -- The package is `connectrpc` (renamed from `connect-python` at v0.10.0). The SDK's `pyproject.toml` pins `connectrpc>=0.10.0`, which resolves to the official runtime. Earlier docs warned about a squatted v0.0.1 on the same PyPI name; that release pre-dates 0.9.0 and pinning `>=0.10.0` skips it.
