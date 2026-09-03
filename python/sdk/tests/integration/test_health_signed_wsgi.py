"""WSGI counterpart of `test_health_signed.py`.

`new_wsgi_app` assembles the health application by hand from `EndpointSync`
rather than from generated code, so it is the one place a change in the
runtime's server constructor or codec defaults goes unnoticed by the generated
stubs. Drives a signed Check through a real WSGI server the way gunicorn would.
"""

from __future__ import annotations

import socket
import threading
import time
from wsgiref.simple_server import WSGIServer, make_server

import pytest
from coincurve import PrivateKey
from connectrpc.client import ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from grpc_health.v1 import health_pb2
from t0_provider_sdk.network.client import new_service_client_sync
from t0_provider_sdk.provider.handler import handler_sync, new_wsgi_app
from t0_provider_sdk.provider.health import HEALTH_SERVICE_FQN
from tzero.v1.payment import provider_pb2 as payment_pb2
from tzero.v1.payment.provider_connect import ProviderServiceWSGIApplication

PROVIDER_SERVICE_FQN = "tzero.v1.payment.ProviderService"


def _find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("", 0))
        return s.getsockname()[1]


def _wait_for_port(port: int, timeout: float = 5.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError(f"port {port} not ready")


def _new_keypair() -> tuple[str, str]:
    priv = PrivateKey()
    return "0x" + priv.secret.hex(), "0x" + priv.public_key.format(compressed=False).hex()


class _StubProviderServiceSync:
    def pay_out(self, request, ctx):
        return payment_pb2.PayoutResponse()

    def update_payment(self, request, ctx):
        return payment_pb2.UpdatePaymentResponse()

    def update_limit(self, request, ctx):
        return payment_pb2.UpdateLimitResponse()

    def append_ledger_entries(self, request, ctx):
        return payment_pb2.AppendLedgerEntriesResponse()

    def approve_payment_quotes(self, request, ctx):
        return payment_pb2.ApprovePaymentQuoteResponse()


_CHECK_METHOD = MethodInfo(
    name="Check",
    service_name=HEALTH_SERVICE_FQN,
    input=health_pb2.HealthCheckRequest,
    output=health_pb2.HealthCheckResponse,
    idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
)


class _CheckOnlyClientSync(ConnectClientSync):
    def __init__(self, base_url: str, **kwargs) -> None:
        super().__init__(base_url, codec=google_protobuf_binary_codec(), **kwargs)

    def check(self, request, *, headers=None, timeout_ms=None):
        return self.execute_unary(request=request, method=_CHECK_METHOD, headers=headers, timeout_ms=timeout_ms)


def _serve(app, port: int) -> WSGIServer:
    server = make_server("127.0.0.1", port, app)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    _wait_for_port(port)
    return server


def test_signed_check_answers_over_wsgi():
    private_key_hex, public_key_hex = _new_keypair()
    port = _find_free_port()

    app = new_wsgi_app(public_key_hex, handler_sync(ProviderServiceWSGIApplication, _StubProviderServiceSync()))
    server = _serve(app, port)
    try:
        client = new_service_client_sync(private_key_hex, _CheckOnlyClientSync, base_url=f"http://127.0.0.1:{port}")

        for service in (PROVIDER_SERVICE_FQN, HEALTH_SERVICE_FQN, ""):
            response = client.check(health_pb2.HealthCheckRequest(service=service))
            assert response.status == health_pb2.HealthCheckResponse.SERVING, service

        with pytest.raises(ConnectError) as exc_info:
            client.check(health_pb2.HealthCheckRequest(service="example.v1.NotRegistered"))
        assert exc_info.value.code == Code.NOT_FOUND
    finally:
        server.shutdown()
        server.server_close()


def test_rejects_unsigned_request_over_wsgi():
    _, public_key_hex = _new_keypair()
    port = _find_free_port()

    server = _serve(new_wsgi_app(public_key_hex), port)
    try:
        plain = _CheckOnlyClientSync(f"http://127.0.0.1:{port}")
        with pytest.raises(ConnectError) as exc_info:
            plain.check(health_pb2.HealthCheckRequest())
        assert exc_info.value.code == Code.INVALID_ARGUMENT
    finally:
        server.shutdown()
        server.server_close()
