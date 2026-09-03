"""Cross-language server-to-server interoperability tests (sync/WSGI variant).

Tests real ConnectRPC health check communication between Go and Python (WSGI).

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import os
import socket
import subprocess
import threading
import time
from pathlib import Path

import pytest
import waitress
from connectrpc.request import RequestContext
from grpc_health.v1 import health_pb2
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import (
    ProviderServiceWSGIApplication,
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
from t0_provider_sdk.network.client import new_service_client_sync
from t0_provider_sdk.provider.handler import handler_sync, new_wsgi_app
from t0_provider_sdk.provider.health import HealthClientSync

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


if not _go_available() and os.environ.get("CI"):
    raise RuntimeError(f"Go helper binary required in CI but not found at {GO_HELPER}")

pytestmark = pytest.mark.skipif(
    not _go_available(),
    reason=f"Go helper binary not found at {GO_HELPER}. Build with: cd cross_test/go_helper && go build -o go_helper .",
)


# -- Minimal sync ProviderService for testing --
class _ProviderServiceSync:
    """Minimal sync ProviderService that records calls and returns empty responses."""

    def pay_out(self, request: PayoutRequest, ctx: RequestContext) -> PayoutResponse:
        return PayoutResponse()

    def update_payment(self, request: UpdatePaymentRequest, ctx: RequestContext) -> UpdatePaymentResponse:
        return UpdatePaymentResponse()

    def update_limit(self, request: UpdateLimitRequest, ctx: RequestContext) -> UpdateLimitResponse:
        return UpdateLimitResponse()

    def append_ledger_entries(
        self, request: AppendLedgerEntriesRequest, ctx: RequestContext
    ) -> AppendLedgerEntriesResponse:
        return AppendLedgerEntriesResponse()

    def approve_payment_quotes(
        self, request: ApprovePaymentQuoteRequest, ctx: RequestContext
    ) -> ApprovePaymentQuoteResponse:
        return ApprovePaymentQuoteResponse()


class TestGoClientPythonWSGIServerHealth:
    """Go health check client -> Python WSGI server."""

    def test_health_check_from_go_client(self):
        """Go calls health check on a Python WSGI server."""
        port = _find_free_port()

        service = _ProviderServiceSync()
        app = new_wsgi_app(
            CLIENT_PUBLIC_KEY,
            handler_sync(ProviderServiceWSGIApplication, service),
        )

        server = waitress.create_server(app, host="127.0.0.1", port=port)
        server_thread = threading.Thread(target=server.run, daemon=True)
        server_thread.start()

        try:
            _wait_for_port(port)

            result = subprocess.run(
                [
                    str(GO_HELPER),
                    "call-health",
                    f"http://127.0.0.1:{port}",
                    CLIENT_PRIVATE_KEY,
                ],
                capture_output=True,
                timeout=15,
            )

            assert result.returncode == 0, (
                f"Go health check failed: stdout={result.stdout.decode()}, stderr={result.stderr.decode()}"
            )
            assert "status=serving" in result.stdout.decode().lower()

        finally:
            server.close()


class TestPythonClientGoServerHealthSync:
    """Python sync health check client -> Go server."""

    def test_health_check_to_go_server(self):
        """Python sync client calls health check on a Go server."""
        port = _find_free_port()

        proc = subprocess.Popen(
            [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        try:
            _wait_for_port(port)

            client = new_service_client_sync(
                CLIENT_PRIVATE_KEY,
                HealthClientSync,
                base_url=f"http://127.0.0.1:{port}",
            )
            response = client.check(health_pb2.HealthCheckRequest())
            assert response.status == health_pb2.HealthCheckResponse.SERVING
        finally:
            proc.terminate()
            proc.wait(timeout=5)
