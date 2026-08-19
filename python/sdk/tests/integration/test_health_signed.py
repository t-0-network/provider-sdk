"""End-to-end coverage of the health service the transport mounts.

Mirrors `go/provider/health_test.go`:
- a uvicorn-served ASGI app built via `new_asgi_app`, with a stub customer
  ProviderService registered alongside it;
- a signed `check` answers SERVING for the customer's service, for health
  itself, and for the whole-process query, and NOT_FOUND for anything else;
- the response carries the SDK identity headers;
- an unsigned call is rejected with INVALID_ARGUMENT, proving the signature
  middleware covers the mounted service too.
"""

from __future__ import annotations

import asyncio
import socket

import pytest
import uvicorn
from coincurve import PrivateKey
from connectrpc.client import ConnectClient
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from grpc_health.v1 import health_pb2
from t0_provider_sdk._version import __version__
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import handler, new_asgi_app
from t0_provider_sdk.provider.health import (
    HEALTH_SERVICE_FQN,
    SDK_ECOSYSTEM_HEADER,
    SDK_VERSION_HEADER,
)
from tzero.v1.payment import provider_pb2 as payment_pb2
from tzero.v1.payment.provider_connect import ProviderServiceASGIApplication

PROVIDER_SERVICE_FQN = "tzero.v1.payment.ProviderService"


def _find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("", 0))
        return s.getsockname()[1]


async def _wait_for_port(port: int, timeout: float = 5.0) -> None:
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        try:
            _, writer = await asyncio.wait_for(asyncio.open_connection("127.0.0.1", port), timeout=0.5)
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
    """Minimal async ProviderService — never invoked, registered only so its FQN
    appears in the health registry."""

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


async def _serve(app, port):
    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
    server = uvicorn.Server(config)
    task = asyncio.create_task(server.serve())
    await _wait_for_port(port)
    return server, task


_CHECK_METHOD = MethodInfo(
    name="Check",
    service_name=HEALTH_SERVICE_FQN,
    input=health_pb2.HealthCheckRequest,
    output=health_pb2.HealthCheckResponse,
    idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
)


class _CheckOnlyClient(ConnectClient):
    """connect-python publishes no health client, so the probe is issued through
    the SDK's own signing client against the Check procedure."""

    async def check(self, request, *, headers=None, timeout_ms=None):
        return await self.execute_unary(
            request=request, method=_CHECK_METHOD, headers=headers, timeout_ms=timeout_ms
        )


@pytest.mark.asyncio
async def test_signed_check_answers_for_registered_services():
    private_key_hex, public_key_hex = _new_keypair()
    port = _find_free_port()

    app = new_asgi_app(
        public_key_hex,
        handler(ProviderServiceASGIApplication, _StubProviderService()),
    )
    server, task = await _serve(app, port)
    try:
        client = new_service_client(
            private_key_hex, _CheckOnlyClient, base_url=f"http://127.0.0.1:{port}"
        )

        # The customer's own service, health itself, and the whole-process query.
        for service in (PROVIDER_SERVICE_FQN, HEALTH_SERVICE_FQN, ""):
            response = await client.check(health_pb2.HealthCheckRequest(service=service))
            assert response.status == health_pb2.HealthCheckResponse.SERVING, service

        with pytest.raises(ConnectError) as exc_info:
            await client.check(health_pb2.HealthCheckRequest(service="example.v1.NotRegistered"))
        assert exc_info.value.code == Code.NOT_FOUND
    finally:
        server.should_exit = True
        await task


@pytest.mark.asyncio
async def test_rejects_unsigned_request():
    """The probe is signed like every other call the Network makes. Without this
    the transport would be publishing an unauthenticated endpoint on a partner's
    port."""
    _, public_key_hex = _new_keypair()
    port = _find_free_port()

    app = new_asgi_app(public_key_hex)
    server, task = await _serve(app, port)
    try:
        plain = _CheckOnlyClient(f"http://127.0.0.1:{port}")

        with pytest.raises(ConnectError) as exc_info:
            await plain.check(health_pb2.HealthCheckRequest())

        assert exc_info.value.code == Code.INVALID_ARGUMENT
    finally:
        server.should_exit = True
        await task


def test_identity_headers_are_set_on_check():
    """Response headers are the only place the SDK reports what it is: the health
    contract has a single status field and names its service in the request, so
    the message itself has no room for this."""
    from connectrpc.request import Headers, RequestContext

    from t0_provider_sdk.provider.health import HealthImplSync

    impl = HealthImplSync([PROVIDER_SERVICE_FQN])
    ctx = RequestContext(method=_CHECK_METHOD, http_method="POST", request_headers=Headers())
    impl.check(health_pb2.HealthCheckRequest(), ctx)

    assert ctx.response_headers()[SDK_ECOSYSTEM_HEADER] == "python"
    assert ctx.response_headers()[SDK_VERSION_HEADER] == __version__
