"""The generated ConnectRPC stubs must agree with the installed `connectrpc`
runtime about how messages are serialized.

Regression for a client-reported outage: connectrpc 0.11 switched its default
codec to `protobuf-py`, so stubs generated without the `protobuf=google` plugin
option hand `google.protobuf` messages to a codec that calls `to_binary()` on
them, and every request fails with `ConnectError('to_binary')` before it
reaches the wire. Nothing else in the suite crosses the codec boundary through
a generated stub, so this test drives a real request from a generated client
into a generated ASGI application.

It also pins the wire bytes to the plain `google.protobuf` serialization: those
are the bytes the signing transport hashes, so a codec that re-encoded the
message differently would silently change what gets signed.
"""

from __future__ import annotations

import asyncio
import gzip
import socket
from typing import Any

import pytest
import uvicorn
from coincurve import PrivateKey
from t0_provider_sdk.network.client import new_service_client
from tzero.v1.payment import provider_pb2
from tzero.v1.payment.provider_connect import ProviderServiceASGIApplication, ProviderServiceClient


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


class _EchoProviderService:
    """Echoes the request's payment_id back so the decode path is proven too."""

    async def pay_out(self, request: provider_pb2.PayoutRequest, ctx: Any) -> provider_pb2.PayoutResponse:
        return provider_pb2.PayoutResponse(
            beneficiary_provider_legal_entity_id=request.payment_id,
            accepted=provider_pb2.PayoutResponse.Accepted(),
        )

    async def update_payment(self, request, ctx):
        return provider_pb2.UpdatePaymentResponse()

    async def update_limit(self, request, ctx):
        return provider_pb2.UpdateLimitResponse()

    async def append_ledger_entries(self, request, ctx):
        return provider_pb2.AppendLedgerEntriesResponse()

    async def approve_payment_quotes(self, request, ctx):
        return provider_pb2.ApprovePaymentQuoteResponse()


def _capture_body(app: Any, sink: list[bytes]) -> Any:
    """ASGI wrapper recording the request body the server received, decoded
    per its Content-Encoding so the assertion sees the codec output rather
    than the transport's compression of it."""

    async def wrapped(scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            await app(scope, receive, send)
            return
        headers = {k.decode().lower(): v.decode() for k, v in scope.get("headers", [])}
        encoding = headers.get("content-encoding", "identity")
        chunks: list[bytes] = []

        async def recording_receive() -> dict[str, Any]:
            message = await receive()
            if message["type"] == "http.request":
                chunks.append(message.get("body", b""))
                if not message.get("more_body", False):
                    body = b"".join(chunks)
                    sink.append(gzip.decompress(body) if encoding == "gzip" else body)
            return message

        await app(scope, recording_receive, send)

    return wrapped


@pytest.mark.asyncio
async def test_generated_client_round_trips_through_generated_server():
    port = _find_free_port()
    bodies: list[bytes] = []
    # The generated application alone, without the SDK's validation interceptors,
    # so the only thing under test is the stub/runtime codec pairing.
    app = _capture_body(ProviderServiceASGIApplication(_EchoProviderService()), bodies)

    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
    server = uvicorn.Server(config)
    task = asyncio.create_task(server.serve())
    await _wait_for_port(port)
    try:
        private_key_hex = "0x" + PrivateKey().secret.hex()
        client = new_service_client(private_key_hex, ProviderServiceClient, base_url=f"http://127.0.0.1:{port}")
        request = provider_pb2.PayoutRequest(payment_id=4242, payout_id=7, currency="EUR")

        response = await client.pay_out(request)

        assert response.beneficiary_provider_legal_entity_id == 4242
        assert response.HasField("accepted")
        # The codec output must be the plain google.protobuf serialization: the
        # signing transport hashes the body it is handed, so a codec that
        # re-encoded the message differently would change what gets signed.
        assert bodies == [request.SerializeToString()]
    finally:
        server.should_exit = True
        await task
