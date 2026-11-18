"""A refused call over HTTP/2 leaves the other calls on its connection alone.

The refused call is still uploading when it is answered. hypercorn 0.18 stops reading an HTTP/2
connection when data arrives for a stream whose response has ended (pgjones/hypercorn#389), and every
other call on that connection then hangs. So the middleware ends the response to a refused call only
after it has read the rest of the body, over HTTP/2 as over HTTP/1.1 (_AnswerThenDrain).

It sits with the cross tests because they run hypercorn, a dev dependency of this workspace. It does
not need the Go helper.
"""

from __future__ import annotations

import asyncio
import socket
import threading
import time
from collections.abc import Iterator

import pyqwest
import pytest
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.protocol import ProtocolType
from hypercorn.asyncio import serve
from hypercorn.config import Config
from t0_provider_sdk._messages import UNKNOWN_PUBLIC_KEY
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import (
    ProviderService,
    ProviderServiceASGIApplication,
    ProviderServiceClient,
)
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import PayoutRequest, UpdateLimitRequest, UpdateLimitResponse
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.signing import SigningClient
from t0_provider_sdk.provider.handler import handler, new_asgi_app

NETWORK_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
NETWORK_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"

# Large enough that the client is still sending when the refusal goes out.
REFUSED_BODY_SIZE = 6 * 1024 * 1024
TIMEOUT_S = 5.0


class _HeldProvider(ProviderService):
    """UpdateLimit stays in flight from its start until the test releases it."""

    def __init__(self) -> None:
        self.started = threading.Event()
        self.release = threading.Event()

    async def update_limit(self, request, ctx):
        self.started.set()
        await asyncio.to_thread(self.release.wait, TIMEOUT_S)
        return UpdateLimitResponse()


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _wait_for_port(port: int) -> None:
    deadline = time.monotonic() + TIMEOUT_S
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError(f"port {port} not ready after {TIMEOUT_S}s")


@pytest.fixture
def h2c_server() -> Iterator[tuple[int, _HeldProvider]]:
    """new_asgi_app under hypercorn, which serves h2c with prior knowledge."""
    provider = _HeldProvider()
    port = _free_port()
    config = Config()
    config.bind = [f"127.0.0.1:{port}"]
    config.graceful_timeout = 1.0
    loop = asyncio.new_event_loop()
    shutdown = asyncio.Event()

    def run() -> None:
        asyncio.set_event_loop(loop)
        app = new_asgi_app(NETWORK_PUBLIC_KEY, handler(ProviderServiceASGIApplication, provider))
        loop.run_until_complete(serve(app, config, shutdown_trigger=shutdown.wait))

    thread = threading.Thread(target=run, daemon=True)
    thread.start()
    _wait_for_port(port)
    yield port, provider
    provider.release.set()
    loop.call_soon_threadsafe(shutdown.set)
    thread.join(timeout=TIMEOUT_S)


async def test_refused_upload_leaves_the_other_calls_on_its_connection_alone(h2c_server):
    """An accepted call is in flight when a call signed with another key uploads 6 MiB on the same
    HTTP/2 connection. The refused call gets its refusal, and the accepted call completes."""
    port, provider = h2c_server
    # One transport with HTTP/2 prior knowledge, so both clients share one connection. The SDK's own
    # client speaks HTTP/1.1 to an http:// URL for Connect.
    async with pyqwest.HTTPTransport(http_version=pyqwest.HTTPVersion.HTTP2) as transport:

        def client(private_key: str) -> ProviderServiceClient:
            return ProviderServiceClient(
                f"http://127.0.0.1:{port}",
                http_client=SigningClient(new_signer_from_hex(private_key), transport=transport),
                protocol=ProtocolType.CONNECT,
                send_compression=None,
                timeout_ms=int(TIMEOUT_S * 1000),
            )

        accepted, refused = client(NETWORK_PRIVATE_KEY), client(OTHER_PRIVATE_KEY)
        in_flight = asyncio.create_task(accepted.update_limit(UpdateLimitRequest()))
        try:
            assert await asyncio.to_thread(provider.started.wait, TIMEOUT_S)
            with pytest.raises(ConnectError) as refusal:
                await refused.pay_out(PayoutRequest(currency="A" * REFUSED_BODY_SIZE))
            assert (refusal.value.code, refusal.value.message) == (Code.UNAUTHENTICATED, UNKNOWN_PUBLIC_KEY)
            provider.release.set()
            assert await asyncio.wait_for(in_flight, TIMEOUT_S) == UpdateLimitResponse()
        finally:
            provider.release.set()
            in_flight.cancel()
