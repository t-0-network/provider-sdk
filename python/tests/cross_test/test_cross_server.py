"""Cross-language server-to-server interoperability tests.

Tests real ConnectRPC communication between Go and Python:
1. Python client → Go server (ProviderService.PayOut)
2. Go client → Python server (ProviderService.PayOut)

Requires the Go helper binary to be built:
    cd tests/cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import asyncio
import os
import socket
import subprocess
import time
from pathlib import Path

import pytest
import uvicorn

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import (
    ProviderServiceASGIApplication,
    ProviderServiceClient,
)
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import (
    AppendLedgerEntriesRequest,
    AppendLedgerEntriesResponse,
    ApprovePaymentQuoteRequest,
    ApprovePaymentQuoteResponse,
    PayoutRequest,
    PayoutResponse,
    UpdateLimitRequest,
    UpdateLimitResponse,
    UpdatePaymentRequest,
    UpdatePaymentResponse,
)
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import handler, new_asgi_app

GO_HELPER = Path(__file__).parent / "go_helper" / "go_helper"

# Key pair used by the "network" side (the one making requests)
CLIENT_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
CLIENT_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"


def _go_available() -> bool:
    return GO_HELPER.exists() and os.access(GO_HELPER, os.X_OK)


def _find_free_port() -> int:
    """Find a free TCP port."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("", 0))
        return s.getsockname()[1]


def _wait_for_port(port: int, timeout: float = 10.0) -> None:
    """Wait until a port is accepting connections."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise TimeoutError(f"Port {port} not ready after {timeout}s")


async def _async_wait_for_port(port: int, timeout: float = 10.0) -> None:
    """Async version: wait until a port is accepting connections."""
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        try:
            _, writer = await asyncio.wait_for(
                asyncio.open_connection("127.0.0.1", port), timeout=0.5
            )
            writer.close()
            await writer.wait_closed()
            return
        except (OSError, asyncio.TimeoutError):
            await asyncio.sleep(0.1)
    raise TimeoutError(f"Port {port} not ready after {timeout}s")


pytestmark = pytest.mark.skipif(
    not _go_available(),
    reason=f"Go helper binary not found at {GO_HELPER}. Build with: cd tests/cross_test/go_helper && go build -o go_helper .",
)


# -- Minimal ProviderService for testing --
# Named _ProviderService to avoid pytest collection warning
class _ProviderService:
    """Minimal ProviderService that records calls and returns empty responses."""

    def __init__(self) -> None:
        self.pay_out_calls: list[PayoutRequest] = []

    async def pay_out(self, request: PayoutRequest, ctx: RequestContext) -> PayoutResponse:
        self.pay_out_calls.append(request)
        return PayoutResponse()

    async def update_payment(self, request: UpdatePaymentRequest, ctx: RequestContext) -> UpdatePaymentResponse:
        return UpdatePaymentResponse()

    async def update_limit(self, request: UpdateLimitRequest, ctx: RequestContext) -> UpdateLimitResponse:
        return UpdateLimitResponse()

    async def append_ledger_entries(self, request: AppendLedgerEntriesRequest, ctx: RequestContext) -> AppendLedgerEntriesResponse:
        return AppendLedgerEntriesResponse()

    async def approve_payment_quotes(self, request: ApprovePaymentQuoteRequest, ctx: RequestContext) -> ApprovePaymentQuoteResponse:
        return ApprovePaymentQuoteResponse()


@pytest.mark.asyncio
class TestPythonClientGoServer:
    """Python client signs a request → Go server verifies and handles it."""

    async def test_pay_out_to_go_server(self):
        """Python ProviderServiceClient calls PayOut on a Go ProviderService server."""
        port = _find_free_port()

        # Start Go server process
        proc = subprocess.Popen(
            [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        try:
            # Wait for Go server to be ready
            _wait_for_port(port)

            # Create Python client with signing transport
            client = new_service_client(
                CLIENT_PRIVATE_KEY,
                ProviderServiceClient,
                base_url=f"http://127.0.0.1:{port}",
            )

            # Make a signed PayOut call
            response = await client.pay_out(
                PayoutRequest(
                    payment_id=42,
                    payout_id=1,
                    currency="EUR",
                    amount=Decimal(unscaled=100, exponent=0),
                ),
            )

            # Should succeed (Go server verified our Python-signed request)
            assert response is not None

        finally:
            proc.terminate()
            proc.wait(timeout=5)


@pytest.mark.asyncio
class TestGoClientPythonServer:
    """Go client signs a request → Python server verifies and handles it."""

    async def test_pay_out_from_go_client(self):
        """Go ProviderServiceClient calls PayOut on a Python ProviderService server."""
        port = _find_free_port()

        service = _ProviderService()
        app = new_asgi_app(
            CLIENT_PUBLIC_KEY,
            handler(ProviderServiceASGIApplication, service),
        )

        # Start Python ASGI server in background
        config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
        server = uvicorn.Server(config)

        server_task = asyncio.create_task(server.serve())
        try:
            # Wait for Python server to be ready (async to not block event loop)
            await _async_wait_for_port(port, timeout=5.0)

            # Run Go client as async subprocess so event loop isn't blocked
            proc = await asyncio.create_subprocess_exec(
                str(GO_HELPER),
                "call-pay-out",
                f"http://127.0.0.1:{port}",
                CLIENT_PRIVATE_KEY,
                CLIENT_PUBLIC_KEY,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=15)

            assert proc.returncode == 0, f"Go client failed: stdout={stdout.decode()}, stderr={stderr.decode()}"
            assert "OK" in stdout.decode()

            # Verify the Python server actually received the call
            assert len(service.pay_out_calls) == 1
            assert service.pay_out_calls[0].payment_id == 42
            assert service.pay_out_calls[0].currency == "EUR"

        finally:
            server.should_exit = True
            await server_task
