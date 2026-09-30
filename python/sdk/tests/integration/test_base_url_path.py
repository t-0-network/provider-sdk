"""A path in the base URL prefixes every call; the signature does not cover the URL.

A uvicorn-served app from `new_asgi_app` verifies each call, behind a proxy-like wrapper that
records the path a request arrives at and strips the prefix before the app routes it.
"""

from __future__ import annotations

import asyncio
import socket

import pytest
import uvicorn
from coincurve import PrivateKey
from connectrpc.client import ConnectClient
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.method import IdempotencyLevel, MethodInfo
from grpc_health.v1 import health_pb2
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import new_asgi_app
from t0_provider_sdk.provider.health import HEALTH_SERVICE_FQN

_CHECK_METHOD = MethodInfo(
    name="Check",
    service_name=HEALTH_SERVICE_FQN,
    input=health_pb2.HealthCheckRequest,
    output=health_pb2.HealthCheckResponse,
    idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
)


class _CheckOnlyClient(ConnectClient):
    def __init__(self, base_url: str, **kwargs) -> None:
        super().__init__(base_url, codec=google_protobuf_binary_codec(), **kwargs)

    async def check(self, request):
        return await self.execute_unary(request=request, method=_CHECK_METHOD)


def _under_prefix(app, prefix: str, paths: list[str]):
    """Serves app under prefix, recording each request's path as it arrives."""

    async def wrapped(scope, receive, send):
        if scope["type"] == "http":
            paths.append(scope["path"])
            if not scope["path"].startswith(prefix + "/"):
                await send({"type": "http.response.start", "status": 404, "headers": []})
                await send({"type": "http.response.body", "body": b""})
                return
            scope = {
                **scope,
                "path": scope["path"].removeprefix(prefix),
                "raw_path": scope["raw_path"].removeprefix(prefix.encode()),
            }
        await app(scope, receive, send)

    return wrapped


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


@pytest.mark.asyncio
@pytest.mark.parametrize("path", ["/prefix", "/prefix/", "/sda/payments/t0"])
async def test_base_url_path_prefixes_calls(path: str) -> None:
    priv = PrivateKey()
    public_key_hex = "0x" + priv.public_key.format(compressed=False).hex()
    prefix = path.removesuffix("/")
    paths: list[str] = []
    port = _find_free_port()

    app = _under_prefix(new_asgi_app(public_key_hex), prefix, paths)
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning"))
    task = asyncio.create_task(server.serve())
    await _wait_for_port(port)
    try:
        client = new_service_client(
            "0x" + priv.secret.hex(), _CheckOnlyClient, base_url=f"http://127.0.0.1:{port}{path}"
        )

        # SERVING only after the app's middleware has verified the signature.
        response = await client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
        assert response.status == health_pb2.HealthCheckResponse.SERVING
        assert paths == [f"{prefix}/{HEALTH_SERVICE_FQN}/Check"]
    finally:
        server.should_exit = True
        await task
