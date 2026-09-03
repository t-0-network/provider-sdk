"""Cross-language server-to-server interoperability tests.

Tests real ConnectRPC health check communication between Go and Python.

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
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
from grpc_health.v1 import health_pb2
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import (
    ProviderServiceASGIApplication,
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
from t0_provider_sdk.provider.health import HEALTH_SERVICE_FQN, HealthClient

GO_HELPER = Path(__file__).resolve().parents[3] / "cross_test" / "go_helper" / "go_helper"

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
            _, writer = await asyncio.wait_for(asyncio.open_connection("127.0.0.1", port), timeout=0.5)
            writer.close()
            await writer.wait_closed()
            return
        except (TimeoutError, OSError):
            await asyncio.sleep(0.1)
    raise TimeoutError(f"Port {port} not ready after {timeout}s")


if not _go_available() and os.environ.get("CI"):
    raise RuntimeError(f"Go helper binary required in CI but not found at {GO_HELPER}")

pytestmark = pytest.mark.skipif(
    not _go_available(),
    reason=f"Go helper binary not found at {GO_HELPER}. Build with: cd cross_test/go_helper && go build -o go_helper .",
)


# -- Minimal ProviderService for testing --
# Named _ProviderService to avoid pytest collection warning
class _ProviderService:
    """Minimal ProviderService that records calls and returns empty responses."""

    async def pay_out(self, request: PayoutRequest, ctx: RequestContext) -> PayoutResponse:
        return PayoutResponse()

    async def update_payment(self, request: UpdatePaymentRequest, ctx: RequestContext) -> UpdatePaymentResponse:
        return UpdatePaymentResponse()

    async def update_limit(self, request: UpdateLimitRequest, ctx: RequestContext) -> UpdateLimitResponse:
        return UpdateLimitResponse()

    async def append_ledger_entries(
        self, request: AppendLedgerEntriesRequest, ctx: RequestContext
    ) -> AppendLedgerEntriesResponse:
        return AppendLedgerEntriesResponse()

    async def approve_payment_quotes(
        self, request: ApprovePaymentQuoteRequest, ctx: RequestContext
    ) -> ApprovePaymentQuoteResponse:
        return ApprovePaymentQuoteResponse()


@pytest.mark.asyncio
class TestGoClientPythonServerHealth:
    """Go health check client -> Python server."""

    async def test_health_check_from_go_client(self):
        """Go calls health check on a Python server."""
        port = _find_free_port()

        service = _ProviderService()
        app = new_asgi_app(
            CLIENT_PUBLIC_KEY,
            handler(ProviderServiceASGIApplication, service),
        )

        config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
        server = uvicorn.Server(config)

        server_task = asyncio.create_task(server.serve())
        try:
            await _async_wait_for_port(port, timeout=5.0)

            proc = await asyncio.create_subprocess_exec(
                str(GO_HELPER),
                "call-health",
                f"http://127.0.0.1:{port}",
                CLIENT_PRIVATE_KEY,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=15)

            assert proc.returncode == 0, f"Go health check failed: stdout={stdout.decode()}, stderr={stderr.decode()}"
            assert "status=serving" in stdout.decode().lower()

        finally:
            server.should_exit = True
            await server_task


@pytest.mark.asyncio
class TestPythonClientGoServerHealth:
    """Python health check client -> Go server."""

    async def test_health_check_to_go_server(self):
        """Python calls health check on a Go server."""
        port = _find_free_port()

        proc = subprocess.Popen(
            [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        try:
            await _async_wait_for_port(port)

            client = new_service_client(
                CLIENT_PRIVATE_KEY,
                HealthClient,
                base_url=f"http://127.0.0.1:{port}",
            )
            response = await client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
            assert response.status == health_pb2.HealthCheckResponse.SERVING
        finally:
            proc.terminate()
            proc.wait(timeout=5)
