"""A client stream that fails early ends the caller's request iterator with the call.

The server answers 401 as soon as the headers arrive and keeps the connection open, so the
transport could go on sending. The error is held while the checks run, as a caller that logs or
re-raises it would.
"""

from __future__ import annotations

import socket
import threading
import time

import pytest
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.network import new_service_client, new_service_client_sync

from .test_redirects import _Client, _SyncClient

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
HOLD_SECONDS = 3.0


@pytest.fixture
def rejecting_server():
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(8)

    def reject(conn: socket.socket) -> None:
        with conn:
            data = b""
            while b"\r\n\r\n" not in data:
                chunk = conn.recv(4096)
                if not chunk:
                    return
                data += chunk
            conn.sendall(b"HTTP/1.1 401 Unauthorized\r\ncontent-type: application/json\r\ncontent-length: 2\r\n\r\n{}")
            time.sleep(HOLD_SECONDS)

    def serve() -> None:
        while True:
            try:
                conn, _ = listener.accept()
            except OSError:
                return
            threading.Thread(target=reject, args=(conn,), daemon=True).start()

    threading.Thread(target=serve, daemon=True).start()
    yield f"http://127.0.0.1:{listener.getsockname()[1]}"
    listener.close()


@pytest.mark.asyncio
async def test_async_request_is_closed_when_the_call_fails(rejecting_server: str) -> None:
    closed: list[bool] = []

    async def messages():
        try:
            for i in range(100_000):
                yield StringValue(value=f"m{i}")
        finally:
            closed.append(True)

    client = new_service_client(PRIVATE_KEY, _Client, base_url=rejecting_server)
    with pytest.raises(ConnectError) as exc_info:
        await client.client_stream(messages())

    assert exc_info.value.code == Code.UNAUTHENTICATED
    assert closed == [True], "closed by the call, not left to garbage collection"


def test_sync_request_stops_and_is_closed_when_the_call_fails(rejecting_server: str) -> None:
    produced: list[int] = []
    closed = threading.Event()

    def messages():
        try:
            for i in range(100_000):
                time.sleep(0.01)
                produced.append(i)
                yield StringValue(value=f"m{i}")
        finally:
            closed.set()

    client = new_service_client_sync(PRIVATE_KEY, _SyncClient, base_url=rejecting_server)
    with pytest.raises(ConnectError) as exc_info:
        client.client_stream(messages())
    at_return = len(produced)

    assert exc_info.value.code == Code.UNAUTHENTICATED
    assert closed.wait(1.0), "closed soon after the call, while the error is still held"
    assert len(produced) <= at_return + 1, "at most the message in flight is produced after the call"
