"""The factory-built clients never follow a redirect: it would re-send the signed request elsewhere."""

from __future__ import annotations

import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.network import new_service_client, new_service_client_sync

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
TARGET = "/target"


def _method(name: str) -> MethodInfo:
    return MethodInfo(
        name=name,
        service_name="test.v1.StreamTest",
        input=StringValue,
        output=StringValue,
        idempotency_level=IdempotencyLevel.UNKNOWN,
    )


UNARY = _method("Unary")
CLIENT_STREAM = _method("ClientStream")


class _Client(ConnectClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    async def unary(self, request):
        return await self.execute_unary(request=request, method=UNARY)

    async def client_stream(self, request):
        return await self.execute_client_stream(request=request, method=CLIENT_STREAM)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def unary(self, request):
        return self.execute_unary(request=request, method=UNARY)

    def client_stream(self, request):
        return self.execute_client_stream(request=request, method=CLIENT_STREAM)


class _RedirectingServer:
    """Answers every request with the given status and Location: /target, and records each path."""

    def __init__(self, status: int) -> None:
        self.paths: list[str] = []
        server = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def _handle(self) -> None:
                self._read_body()
                server.paths.append(self.path)
                if self.path == TARGET:
                    self.send_response(200)
                    self.send_header("Content-Length", "0")
                else:
                    self.send_response(status)
                    self.send_header("Location", TARGET)
                    self.send_header("Content-Length", "0")
                self.end_headers()

            def _read_body(self) -> None:
                if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
                    while True:
                        size = int(self.rfile.readline().split(b";")[0], 16)
                        self.rfile.read(size + 2)  # the chunk and its CRLF, or the final CRLF
                        if size == 0:
                            return
                self.rfile.read(int(self.headers.get("Content-Length") or 0))

            do_GET = do_POST = _handle  # noqa: N815 - the names http.server dispatches to

            def log_message(self, *args) -> None:
                pass

        self._httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self._httpd.server_port}"
        threading.Thread(target=self._httpd.serve_forever, daemon=True).start()

    def close(self) -> None:
        self._httpd.shutdown()
        self._httpd.server_close()


@pytest.fixture(params=[302, 307])
def server(request):
    server = _RedirectingServer(request.param)
    yield server
    server.close()


async def _messages():
    yield StringValue(value="m1")


@pytest.mark.asyncio
@pytest.mark.parametrize("kind", ["unary", "client_stream"])
async def test_async_client_does_not_follow(server: _RedirectingServer, kind: str) -> None:
    client = new_service_client(PRIVATE_KEY, _Client, base_url=server.url)

    with pytest.raises(ConnectError):
        if kind == "unary":
            await client.unary(StringValue(value="m1"))
        else:
            await client.client_stream(_messages())
    assert len(server.paths) == 1
    assert TARGET not in server.paths


@pytest.mark.parametrize("kind", ["unary", "client_stream"])
def test_sync_client_does_not_follow(server: _RedirectingServer, kind: str) -> None:
    client = new_service_client_sync(PRIVATE_KEY, _SyncClient, base_url=server.url)

    with pytest.raises(ConnectError):
        if kind == "unary":
            client.unary(StringValue(value="m1"))
        else:
            client.client_stream(iter([StringValue(value="m1")]))
    assert len(server.paths) == 1
    assert TARGET not in server.paths
