"""Cross-language streaming tests: Python client -> Go server.

The Go helper serves test.v1.StreamTest (cross_test/stream_test.proto) behind a verifier that
checks a streaming request's signature the way the T-0 Network does: over the first request
envelope only. It answers HTTP 401 to anything else. The client is built by hand on
google.protobuf.StringValue, as generated code would build it.

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import os
import socket
import subprocess
import time
from pathlib import Path
from typing import TYPE_CHECKING

import pyqwest
import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from connectrpc.protocol import ProtocolType
from google.protobuf.wrappers_pb2 import StringValue
from grpc_health.v1 import health_pb2
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.client import new_service_client, new_service_client_sync
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient
from t0_provider_sdk.provider.health import HEALTH_SERVICE_FQN, HealthClient, HealthClientSync

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Iterator, Mapping

    from connectrpc.request import Headers

GO_HELPER = Path(__file__).resolve().parents[3] / "cross_test" / "go_helper" / "go_helper"

# Key pair used by the "network" side (the one making requests)
CLIENT_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
CLIENT_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"

MESSAGES = ["m1", "m2", "m3"]


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


# -- test.v1.StreamTest client, built the way protoc-gen-connectrpc builds one --

_CLIENT_STREAM = MethodInfo(
    name="ClientStream",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)
_SERVER_STREAM = MethodInfo(
    name="ServerStream",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)


class _StreamTestClient(ConnectClient):
    def __init__(self, address: str, **kwargs: object) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)  # type: ignore[arg-type]

    async def client_stream(
        self,
        request: AsyncIterator[StringValue],
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> StringValue:
        return await self.execute_client_stream(
            request=request, method=_CLIENT_STREAM, headers=headers, timeout_ms=timeout_ms
        )

    def server_stream(
        self,
        request: StringValue,
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> AsyncIterator[StringValue]:
        return self.execute_server_stream(
            request=request, method=_SERVER_STREAM, headers=headers, timeout_ms=timeout_ms
        )


class _StreamTestClientSync(ConnectClientSync):
    def __init__(self, address: str, **kwargs: object) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)  # type: ignore[arg-type]

    def client_stream(
        self,
        request: Iterator[StringValue],
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> StringValue:
        return self.execute_client_stream(
            request=request, method=_CLIENT_STREAM, headers=headers, timeout_ms=timeout_ms
        )

    def server_stream(
        self,
        request: StringValue,
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> Iterator[StringValue]:
        return self.execute_server_stream(
            request=request, method=_SERVER_STREAM, headers=headers, timeout_ms=timeout_ms
        )


@pytest.fixture(scope="module")
def go_server_url() -> Iterator[str]:
    """Starts `go_helper serve`, which serves Connect over HTTP/1.1 and gRPC over h2c."""
    port = _find_free_port()
    proc = subprocess.Popen(
        [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    try:
        _wait_for_port(port)
        yield f"http://127.0.0.1:{port}"
    finally:
        proc.terminate()
        proc.wait(timeout=5)


# The factory speaks Connect over the default HTTP/1.1 transport. gRPC needs HTTP/2, h2c here,
# so gRPC clients are built on the signing wrapper directly.


def _async_client(client_class, base_url: str, protocol: str, private_key: str = CLIENT_PRIVATE_KEY):
    if protocol == "connect":
        return new_service_client(private_key, client_class, base_url=base_url)
    transport = pyqwest.HTTPTransport(http_version=pyqwest.HTTPVersion.HTTP2)
    http_client = SigningClient(new_signer_from_hex(private_key), transport=transport)
    return client_class(base_url, protocol=ProtocolType.GRPC, http_client=http_client)


def _sync_client(client_class, base_url: str, protocol: str):
    if protocol == "connect":
        return new_service_client_sync(CLIENT_PRIVATE_KEY, client_class, base_url=base_url)
    transport = pyqwest.SyncHTTPTransport(http_version=pyqwest.HTTPVersion.HTTP2)
    http_client = SigningSyncClient(new_signer_from_hex(CLIENT_PRIVATE_KEY), transport=transport)
    return client_class(base_url, protocol=ProtocolType.GRPC, http_client=http_client)


async def _messages() -> AsyncIterator[StringValue]:
    for value in MESSAGES:
        yield StringValue(value=value)


@pytest.mark.asyncio
@pytest.mark.parametrize("protocol", ["connect", "grpc"])
class TestPythonAsyncClientGoServerStream:
    """Python async client -> Go server."""

    async def test_client_stream(self, go_server_url: str, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server_url, protocol)
        response = await client.client_stream(_messages())
        assert response.value == ",".join(MESSAGES)

    async def test_server_stream(self, go_server_url: str, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server_url, protocol)
        received = [msg.value async for msg in client.server_stream(StringValue(value="hello"))]
        assert received == ["hello"] * 3

    async def test_unknown_key_is_rejected(self, go_server_url: str, protocol: str) -> None:
        """The server does verify: a signature by a key it does not trust gets HTTP 401."""
        client = _async_client(_StreamTestClient, go_server_url, protocol, private_key=OTHER_PRIVATE_KEY)
        with pytest.raises(ConnectError) as exc_info:
            await client.client_stream(_messages())
        assert exc_info.value.code == Code.UNAUTHENTICATED

    async def test_unary_health_check(self, go_server_url: str, protocol: str) -> None:
        """A gRPC unary call goes out through stream() as well, signed over its one envelope."""
        client = _async_client(HealthClient, go_server_url, protocol)
        response = await client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
        assert response.status == health_pb2.HealthCheckResponse.SERVING


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
class TestPythonSyncClientGoServerStream:
    """Python sync client -> Go server."""

    def test_client_stream(self, go_server_url: str, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server_url, protocol)
        response = client.client_stream(StringValue(value=value) for value in MESSAGES)
        assert response.value == ",".join(MESSAGES)

    def test_server_stream(self, go_server_url: str, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server_url, protocol)
        received = [msg.value for msg in client.server_stream(StringValue(value="hello"))]
        assert received == ["hello"] * 3

    def test_unary_health_check(self, go_server_url: str, protocol: str) -> None:
        client = _sync_client(HealthClientSync, go_server_url, protocol)
        response = client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
        assert response.status == health_pb2.HealthCheckResponse.SERVING
