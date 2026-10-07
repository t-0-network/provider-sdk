"""The Python column of the shared server behavior: every case of server_cases in
cross_test/test_vectors.json, sent by `go_helper probe` to the apps new_asgi_app and new_wsgi_app build.
The apps serve a ProviderService whose ApprovePaymentQuotes and PayOut return responses that fail
response validation (the response-invalid cases).

The ASGI app runs under hypercorn, which serves HTTP/1.1 (Connect) and h2c (gRPC) on one port; the
WSGI app under waitress, which serves HTTP/1.1 only (Connect).

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import asyncio
import json
import os
import socket
import subprocess
import threading
import time
from collections.abc import Iterator
from pathlib import Path

import pytest
import waitress
from hypercorn.asyncio import serve
from hypercorn.config import Config
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import (
    ProviderService,
    ProviderServiceASGIApplication,
    ProviderServiceSync,
    ProviderServiceWSGIApplication,
)
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import ApprovePaymentQuoteResponse, PayoutResponse
from t0_provider_sdk.provider.handler import handler, handler_sync, new_asgi_app, new_wsgi_app

CROSS_TEST = Path(__file__).resolve().parents[3] / "cross_test"
GO_HELPER = CROSS_TEST / "go_helper" / "go_helper"
VECTORS = CROSS_TEST / "test_vectors.json"

if not (GO_HELPER.exists() and os.access(GO_HELPER, os.X_OK)) and os.environ.get("CI"):
    raise RuntimeError(f"Go helper binary required in CI but not found at {GO_HELPER}")

pytestmark = pytest.mark.skipif(not GO_HELPER.exists(), reason=f"Go helper not found at {GO_HELPER}")

NETWORK_PUBLIC_KEY = "0x" + json.loads(VECTORS.read_text())["keys"]["public_key"]


# Its oneof result is required.
_INVALID_APPROVAL = ApprovePaymentQuoteResponse()
# failed.details is at most 1024 characters.
_INVALID_PAYOUT = PayoutResponse(failed=PayoutResponse.Failed(details="x" * 1025))


class _Provider(ProviderService):
    async def approve_payment_quotes(self, request, ctx):
        return _INVALID_APPROVAL

    async def pay_out(self, request, ctx):
        return _INVALID_PAYOUT


class _ProviderSync(ProviderServiceSync):
    def approve_payment_quotes(self, request, ctx):
        return _INVALID_APPROVAL

    def pay_out(self, request, ctx):
        return _INVALID_PAYOUT


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _wait_for_port(port: int, timeout: float = 10.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError(f"port {port} not ready after {timeout}s")


def _probe(port: int, *protocols: str) -> None:
    for protocol in protocols:
        result = subprocess.run(
            [
                str(GO_HELPER),
                "probe",
                f"http://127.0.0.1:{port}",
                "--sdk",
                "python",
                "--protocol",
                protocol,
                "--vectors",
                str(VECTORS),
            ],
            capture_output=True,
            text=True,
            timeout=300,
        )
        assert result.returncode == 0, f"probe over {protocol} failed:\n{result.stdout}{result.stderr}"


@pytest.fixture
def asgi_port() -> Iterator[int]:
    port = _free_port()
    config = Config()
    config.bind = [f"127.0.0.1:{port}"]
    loop = asyncio.new_event_loop()
    shutdown = asyncio.Event()

    def run() -> None:
        asyncio.set_event_loop(loop)
        app = new_asgi_app(NETWORK_PUBLIC_KEY, handler(ProviderServiceASGIApplication, _Provider()))
        loop.run_until_complete(serve(app, config, shutdown_trigger=shutdown.wait))

    thread = threading.Thread(target=run, daemon=True)
    thread.start()
    _wait_for_port(port)
    yield port
    loop.call_soon_threadsafe(shutdown.set)
    thread.join(timeout=10)


@pytest.fixture
def wsgi_port() -> Iterator[int]:
    port = _free_port()
    app = new_wsgi_app(NETWORK_PUBLIC_KEY, handler_sync(ProviderServiceWSGIApplication, _ProviderSync()))
    server = waitress.create_server(app, host="127.0.0.1", port=port)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    _wait_for_port(port)
    yield port
    server.close()
    thread.join(timeout=10)


def test_asgi_app(asgi_port: int) -> None:
    _probe(asgi_port, "connect", "grpc")


def test_wsgi_app(wsgi_port: int) -> None:
    _probe(wsgi_port, "connect")
