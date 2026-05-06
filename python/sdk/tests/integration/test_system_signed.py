"""End-to-end integration tests for the auto-registered SystemService.

Mirrors `go/provider/system_test.go`:
- a uvicorn-served ASGI app built via `new_asgi_app`, with a stub customer
  ProviderService registered alongside the auto-registered SystemService;
- a signed Health() call returns BOTH the customer FQN and SystemService's
  own FQN, plus a fresh `current_time`, the runtime SDK version, and the
  Python ecosystem identifier;
- an unsigned Health() call is rejected with INVALID_ARGUMENT, proving the
  signature middleware also covers the auto-registered service.
"""

from __future__ import annotations

import asyncio
import socket
import time

import pytest
import uvicorn
from coincurve import PrivateKey
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from t0_provider_sdk._version import __version__
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import handler, new_asgi_app
from tzero.v1.payment import provider_pb2 as payment_pb2
from tzero.v1.payment.provider_connect import ProviderServiceASGIApplication
from tzero.v1.system import system_pb2
from tzero.v1.system.system_connect import SystemServiceClient

PROVIDER_SERVICE_FQN = "tzero.v1.payment.ProviderService"
SYSTEM_SERVICE_FQN = "tzero.v1.system.SystemService"


def _find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("", 0))
        return s.getsockname()[1]


async def _wait_for_port(port: int, timeout: float = 5.0) -> None:
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        try:
            _, writer = await asyncio.wait_for(
                asyncio.open_connection("127.0.0.1", port), timeout=0.5
            )
            writer.close()
            await writer.wait_closed()
            return
        except (TimeoutError, OSError):
            await asyncio.sleep(0.1)
    raise TimeoutError(f"port {port} not ready")


def _new_keypair() -> tuple[str, str]:
    priv = PrivateKey()
    return (
        "0x" + priv.secret.hex(),
        "0x" + priv.public_key.format(compressed=False).hex(),
    )


class _StubProviderService:
    """Minimal async ProviderService — methods are never invoked, only the
    registration matters so the FQN appears in the Health services list."""

    async def pay_out(self, request, ctx):
        return payment_pb2.PayoutResponse()

    async def update_payment(self, request, ctx):
        return payment_pb2.UpdatePaymentResponse()

    async def update_limit(self, request, ctx):
        return payment_pb2.UpdateLimitResponse()

    async def append_ledger_entries(self, request, ctx):
        return payment_pb2.AppendLedgerEntriesResponse()

    async def approve_payment_quotes(self, request, ctx):
        return payment_pb2.ApprovePaymentQuoteResponse()


@pytest.mark.asyncio
async def test_system_service_auto_registered_signed_e2e():
    private_key_hex, public_key_hex = _new_keypair()
    port = _find_free_port()

    # Customer ProviderService stub registered alongside the auto-registered SystemService.
    app = new_asgi_app(
        public_key_hex,
        handler(ProviderServiceASGIApplication, _StubProviderService()),
    )

    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
    server = uvicorn.Server(config)
    server_task = asyncio.create_task(server.serve())
    try:
        await _wait_for_port(port)

        client = new_service_client(
            private_key_hex,
            SystemServiceClient,
            base_url=f"http://127.0.0.1:{port}",
        )

        response = await client.health(system_pb2.HealthRequest())

        assert PROVIDER_SERVICE_FQN in response.services, (
            f"services missing {PROVIDER_SERVICE_FQN}: {list(response.services)}"
        )
        assert SYSTEM_SERVICE_FQN in response.services, (
            f"services missing {SYSTEM_SERVICE_FQN}: {list(response.services)}"
        )
        assert response.sdk_version == __version__
        assert response.sdk_ecosystem == system_pb2.SDK_ECOSYSTEM_PYTHON
        assert response.current_time is not None
        skew = abs(time.time() - response.current_time.seconds)
        assert skew < 5, f"currentTime skew = {skew}s"
    finally:
        server.should_exit = True
        await server_task


@pytest.mark.asyncio
async def test_system_service_rejects_unsigned_request():
    """Auto-registered SystemService inherits the signature middleware:
    an unsigned request is rejected with INVALID_ARGUMENT (Go parity:
    connect.CodeInvalidArgument)."""
    _, public_key_hex = _new_keypair()
    port = _find_free_port()

    # Customer service not needed — the unsigned request never reaches a service handler.
    app = new_asgi_app(public_key_hex)

    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
    server = uvicorn.Server(config)
    server_task = asyncio.create_task(server.serve())
    try:
        await _wait_for_port(port)

        # Plain (no signing transport) client — missing X-Public-Key header.
        plain_client = SystemServiceClient(f"http://127.0.0.1:{port}")

        with pytest.raises(ConnectError) as exc_info:
            await plain_client.health(system_pb2.HealthRequest())

        assert exc_info.value.code == Code.INVALID_ARGUMENT
    finally:
        server.should_exit = True
        await server_task
