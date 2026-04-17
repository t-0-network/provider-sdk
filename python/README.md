# T-0 Provider SDK -- Python

Python SDK and starter CLI for building T-0 Network payment provider integrations. The SDK provides ConnectRPC communication, secp256k1 cryptographic signing/verification, and ASGI/WSGI middleware for signature validation.

## Prerequisites

- Python >= 3.13
- [uv](https://docs.astral.sh/uv/) -- dependency management and runner
- Docker (optional, for containerized deployment)

## Quick Start

```bash
uvx t0-provider-starter my_provider
```

This creates a ready-to-run project with a secp256k1 keypair, environment config, provider service stubs (async ASGI), and a Dockerfile.

### CLI Options

```
t0-provider-starter <project_name> [-d <directory>]
```

| Argument / Option | Required | Default | Description |
|---|---|---|---|
| `project_name` | Yes | -- | Name for `pyproject.toml` and the project directory |
| `-d`, `--directory` | No | `./<project_name>` | Target directory for the generated project |

## Generated Project Structure

```
my_provider/
├── pyproject.toml                              # Project metadata, depends on t0-provider-sdk
├── Dockerfile                                  # Multi-stage build with python:3.13-slim
├── .env.example                                # Template environment file
├── .env                                        # Generated with your private key (git-ignored)
└── src/provider/
    ├── __init__.py
    ├── main.py                                 # Entry point: server, quote tasks, handlers
    ├── config.py                               # Environment variable loading and validation
    ├── publish_quotes.py                       # Phase 1: payout quote publishing
    ├── get_quote.py                            # Phase 1: quote retrieval
    ├── publish_payment_intent_quotes.py        # Phase 3A: pay-in quote publishing
    ├── get_payment_intent_quote.py             # Phase 3B: indicative quote retrieval
    ├── create_payment_intent.py                # Phase 3B: create a payment intent
    ├── confirm_funds_received.py               # Phase 3A: confirm funds received
    └── handler/
        ├── __init__.py
        ├── payment.py                          # Phase 2: ProviderService (async)
        ├── payment_sync.py                     # Phase 2: ProviderService (sync/WSGI)
        ├── payment_intent_pay_in.py            # Phase 3A: PayInProviderService (async)
        ├── payment_intent_pay_in_sync.py       # Phase 3A: PayInProviderService (sync/WSGI)
        ├── payment_intent_beneficiary.py       # Phase 3B: BeneficiaryService (async)
        └── payment_intent_beneficiary_sync.py  # Phase 3B: BeneficiaryService (sync/WSGI)
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `src/provider/handler/payment.py` | Implement your payment processing logic. Look for `TODO` comments. |
| `src/provider/publish_quotes.py` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | secp256k1 private key (hex, 0x-prefixed) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for verifying inbound request signatures |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API URL |
| `PORT` | No | `8080` | Server listen port |
| `QUOTE_PUBLISHING_INTERVAL` | No | `5000` | Interval in milliseconds between quote publications |

## Getting Started

### Phase 1: Quoting

1. Install dependencies: `cd my_provider && uv sync`
2. Share the generated public key (printed during project initialization) with the T-0 team.
3. Replace the sample quote publishing logic in `src/provider/publish_quotes.py`.
4. Start the server: `uv run python -m provider.main`
5. Verify that quotes are successfully received by the network.

### Phase 2: Payments

1. Implement `update_payment` in `src/provider/handler/payment.py`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `pay_out` in `src/provider/handler/payment.py`.
4. Coordinate with the T-0 team to test end-to-end payment flows.

Additional optional methods in `src/provider/handler/payment.py`:
- `update_limit` -- handle notifications about limit changes
- `append_ledger_entries` -- handle notifications about ledger transactions
- `approve_payment_quotes` -- approve quotes after AML check

### Phase 3: Payment Intent Flow

The payment intent flow is independent of Phase 2. It is an asynchronous pay-in flow where an end-user pays a pay-in provider in fiat (bank transfer, mobile money, etc.) and a beneficiary provider receives settlement on the crypto side. Quotes are indicative until funds are received, settlement happens periodically, and a confirmation code links the end-user's payment back to a specific payment intent.

Implement **one** of the two sub-phases below depending on your role. If you participate on both sides, implement both.

**Phase 3A -- Pay-In Provider role** (skip if you're a beneficiary):

1. **Step 3A.1** Replace the sample pay-in quote publishing in `src/provider/publish_payment_intent_quotes.py` with your own.
2. **Step 3A.2** Implement `get_payment_details` in `src/provider/handler/payment_intent_pay_in.py` -- return bank account / mobile money details plus a payment reference the end-user will include in their transfer.
3. **Step 3A.3** When you detect the end-user's fiat payment, call `confirm_funds_received` (see `src/provider/confirm_funds_received.py`).

**Phase 3B -- Beneficiary Provider role** (skip if you're pay-in):

1. **Step 3B.1** Verify indicative quotes are returned (`src/provider/get_payment_intent_quote.py`).
2. **Step 3B.2** Create payment intents for your end-users via `create_payment_intent` (see `src/provider/create_payment_intent.py`).
3. **Step 3B.3** Implement `payment_intent_update` in `src/provider/handler/payment_intent_beneficiary.py` to receive notifications when funds are received.

If you only play one role, delete the files for the other role and remove the corresponding `handler(...)` registration in `src/provider/main.py`. Sync (WSGI) variants of both handlers are provided (`*_sync.py`) for use with the WSGI setup described below.

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

Then run with a WSGI server:

```bash
gunicorn provider.main:app --bind 0.0.0.0:8080
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

**`PROVIDER_PRIVATE_KEY is not set in .env`** -- Copy `.env.example` to `.env` and set the key. The CLI generates this automatically.

**`ModuleNotFoundError: No module named 'provider'`** -- Run `uv sync` in the generated project directory. The project uses a `src/` layout that requires installation.

**Signature verification failures** -- Ensure the server clock is synchronized (NTP). Timestamps outside +/- 60 seconds are rejected. Verify that `NETWORK_PUBLIC_KEY` matches the key provided by the T-0 team.

**`pip install connectrpc` installs the wrong package** -- The correct PyPI package is `connect-python` (which imports as `connectrpc`). The `connectrpc` package on PyPI is a different, unmaintained package. The SDK's `pyproject.toml` already depends on the correct package.
