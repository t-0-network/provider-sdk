# {{PROJECT_NAME}}

T-0 Network provider implementation generated from the official Python starter.

## Quick Start

```bash
uv sync
uv run python -m provider.main
```

Share the provider public key (printed by the initializer; also visible at the top of `.env`) with the T-0 team so requests from the network can be signed against it.

## Generated Project Structure

```
{{PROJECT_NAME}}/
├── src/provider/
│   ├── main.py                              # Entry point: wires clients, server, background tasks
│   ├── config.py                            # Loads .env into a typed Config
│   ├── handler/
│   │   ├── payment.py                       # Phase 2: ProviderService handlers (async)
│   │   ├── payment_sync.py                  # Phase 2: ProviderService handlers (sync / WSGI)
│   │   ├── payment_intent_pay_in.py         # Phase 3A: PayInProviderService handler (async)
│   │   ├── payment_intent_pay_in_sync.py    # Phase 3A: PayInProviderService handler (sync)
│   │   ├── payment_intent_beneficiary.py    # Phase 3B: BeneficiaryService handler (async)
│   │   └── payment_intent_beneficiary_sync.py # Phase 3B: BeneficiaryService handler (sync)
│   ├── publish_quotes.py                    # Phase 1: payout quote publishing
│   ├── get_quote.py                         # Phase 1: quote retrieval
│   ├── publish_payment_intent_quotes.py     # Phase 3A: pay-in quote publishing
│   ├── get_payment_intent_quote.py          # Phase 3B: indicative quote retrieval
│   ├── create_payment_intent.py             # Phase 3B: create a payment intent
│   └── confirm_funds_received.py            # Phase 3A: confirm funds received
├── Dockerfile                               # Docker configuration
├── .env                                     # Environment variables (with generated keys)
├── .env.example                             # Example environment file
└── pyproject.toml                           # Project dependencies
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `src/provider/handler/payment.py` | Implement your payment processing logic. Look for `TODO` comments. |
| `src/provider/publish_quotes.py` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | No | `8080` | Server port |
| `QUOTE_PUBLISHING_INTERVAL` | No | -- | Quote publishing frequency in milliseconds |

## Getting Started

### Phase 1: Quoting

1. Open `.env` and find your generated public key (marked as "Step 1.2"). Share it with the T-0 team to register your provider.
2. Implement your quote publishing logic in `src/provider/publish_quotes.py`.
3. Start the dev server (`uv run python -m provider.main`) and verify quotes are published.
4. Confirm quote retrieval works by checking the `get_quote` task output.

### Phase 2: Payments

1. Implement `update_payment` in `src/provider/handler/payment.py`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `pay_out` in `src/provider/handler/payment.py`.
4. Wrap responses with `validate()` (see "Returning a validated response" below) so protovalidate failures surface in your own code.
5. Coordinate with the T-0 team to test end-to-end payment flows.

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

If you only play one role, remove the unused handler registration from `create_provider_app` in `src/provider/main.py`.

## Available Commands

```bash
uv sync                                   # Install / update dependencies
uv run python -m provider.main            # Run the ASGI server (uvicorn)
uv run ruff check .                       # Lint
uv run ruff format .                      # Format
```

## Returning a validated response

The SDK exports a generic `validate()` helper. Wrap responses with it to surface protovalidate failures in your own code rather than as opaque `Code.INTERNAL` errors on the wire:

```python
from t0_provider_sdk import validate
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import PayoutResponse

async def pay_out(self, request, ctx):
    return validate(PayoutResponse(...))
```

On success `validate()` returns the same instance. On failure it raises `ConnectError(Code.INTERNAL, "response validation failed: <details>")` — the exact shape the SDK's safety-net interceptor would emit, so propagating the error keeps wire behavior identical. Catch it locally if you want to log context or convert to a domain-level error.

## Configuring logging

The SDK emits structured `error`-level log lines for events that would otherwise be silent to your code:

1. **Response validation failures** — when a handler returns a message that fails its `buf.validate` rules, the SDK's safety-net interceptor still produces a `Code.INTERNAL` wire response, but first writes a log record with `rpc_method`, `response_type`, `violations`, and `sdk_version` in the `extra` fields. Call `validate(resp)` inside the handler (see above) if you want the failure raised on your own stack frame instead.

### Default logger

If you don't pass `logger=` to `new_asgi_app` / `new_wsgi_app`, the SDK uses `logging.getLogger("t0_provider_sdk")`. Once a handler is attached to that logger (or to root), Python's `logging` module writes the line to stderr by default. The generated `main.py` calls `logging.basicConfig(...)` near the top, which attaches a default StreamHandler to the root logger, so out of the box you'll see SDK errors on stderr.

### Custom logger (stdlib)

```python
import logging
import sys

sdk_logger = logging.getLogger("provider.sdk")
sdk_logger.setLevel(logging.INFO)
handler = logging.StreamHandler(sys.stderr)
handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
sdk_logger.addHandler(handler)

app = new_asgi_app(
    config.network_public_key,
    handler(ProviderServiceASGIApplication, provider_service),
    logger=sdk_logger,
)
```

### Plug in structlog (or any other logger)

`structlog`'s stdlib adapter is a drop-in replacement — pass the wrapped logger to `new_asgi_app`:

```python
import logging
import structlog

structlog.configure(
    processors=[
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.JSONRenderer(),
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    logger_factory=structlog.stdlib.LoggerFactory(),
)

sdk_logger = structlog.get_logger("provider.sdk")

app = new_asgi_app(
    config.network_public_key,
    handler(ProviderServiceASGIApplication, provider_service),
    logger=sdk_logger,
)
```

The structured fields in the SDK's log call (`extra={...}`) become first-class keys in your JSON output, so log scrapers can match on `rpc_method` / `response_type` etc.

## Deployment

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

## SDK Reference

For direct SDK usage (without the starter), see the [Python SDK documentation](https://github.com/t-0-network/provider-sdk/tree/master/python/sdk).

## Troubleshooting

**`uv: command not found`** -- Install uv (`curl -LsSf https://astral.sh/uv/install.sh | sh` on macOS/Linux, see [astral.sh/uv](https://astral.sh/uv) for other platforms).

**`uv sync` fails** -- Ensure Python >= 3.13 is available: `python3 --version`. uv will fetch a managed interpreter if needed.

**`ImportError: No module named 'provider'`** -- Run from the project root so `src/provider/` is on the import path, or use `uv run python -m provider.main`.

**Port already in use** -- Change `PORT` in `.env` or stop the conflicting process.
