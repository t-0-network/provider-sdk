"""End-to-end integration test for the auto-registered SystemService.

Spins a uvicorn-served ASGI app via `new_asgi_app` (no customer services),
calls `Health()` through `new_service_client` (which signs requests), and
asserts the response shape matches what `SystemServiceImpl` returns.
"""

from __future__ import annotations

import asyncio
import os
import socket

import pytest
import uvicorn
from coincurve import PrivateKey
from t0_provider_sdk._version import __version__
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import new_asgi_app
from tzero.v1.system import system_pb2
from tzero.v1.system.system_connect import SystemServiceClient

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


@pytest.mark.asyncio
async def test_system_service_auto_registered_signed_e2e():
    private_key_hex, public_key_hex = _new_keypair()
    port = _find_free_port()

    # No customer services — SystemService is still registered automatically.
    app = new_asgi_app(public_key_hex)

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

        assert SYSTEM_SERVICE_FQN in response.services, (
            f"services missing {SYSTEM_SERVICE_FQN}: {list(response.services)}"
        )
        assert response.sdk_version == __version__
        assert response.sdk_ecosystem == system_pb2.SDK_ECOSYSTEM_PYTHON
        assert response.current_time is not None
    finally:
        server.should_exit = True
        await server_task
