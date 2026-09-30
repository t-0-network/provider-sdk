"""Cross-language streaming tests: Python client -> Go server.

The Go helper verifies test.v1.StreamTest requests over their first envelope, as the network does.
Its reply names the framing it accepted, and a refused call fails with UNAUTHENTICATED and the reason.
Its stderr log is read only to hold message 2 back until message 1 was verified, and to see that
nothing was sent. See docs/STREAMING.md.

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import asyncio
import base64
import http.client
import itertools
import os
import socket
import subprocess
import threading
import time
from pathlib import Path
from typing import TYPE_CHECKING

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from google.protobuf.wrappers_pb2 import StringValue
from grpc_health.v1 import health_pb2
from t0_provider_sdk.network import DEFAULT_STREAM_TIMEOUT, Protocol, WireFormat, signing
from t0_provider_sdk.network.client import new_service_client, new_service_client_sync
from t0_provider_sdk.provider.health import HEALTH_SERVICE_FQN, HealthClient, HealthClientSync

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Iterator, Mapping
    from typing import IO

    from connectrpc.request import Headers

GO_HELPER = Path(__file__).resolve().parents[3] / "cross_test" / "go_helper" / "go_helper"

# Key pair used by the "network" side (the one making requests)
CLIENT_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
CLIENT_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

MESSAGES = ["m1", "m2", "m3"]

STREAM_TEST_PREFIX = "/test.v1.StreamTest/"
CLIENT_STREAM_PATH = STREAM_TEST_PREFIX + "ClientStream"
# The no-buffering gate waits for this log line; every other check reads the call's own result.
CLIENT_STREAM_VERIFIED = f"{CLIENT_STREAM_PATH} verified over the first envelope"
# The helper prefixes each reply with the framing its verifier accepted.
ENVELOPE = "envelope:"

# The network's timestamp tolerance is 60 s.
STALE_BY_MS = 120_000


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
        kwargs.setdefault("codec", google_protobuf_binary_codec())
        super().__init__(address, **kwargs)  # type: ignore[arg-type]

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
        kwargs.setdefault("codec", google_protobuf_binary_codec())
        super().__init__(address, **kwargs)  # type: ignore[arg-type]

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


# -- The Go server and its log --


class _GoServer:
    """`go_helper serve` and its stderr log. A test looks only at lines logged after its mark()."""

    def __init__(self, proc: subprocess.Popen[bytes], port: int) -> None:
        self.url = f"http://127.0.0.1:{port}"
        self._port = port
        self._lines: list[str] = []
        self._logged = threading.Condition()
        self._marks = itertools.count()
        assert proc.stderr is not None
        assert proc.stdout is not None
        threading.Thread(target=self._read_log, args=(proc.stderr,), daemon=True).start()
        # Read so that the pipe never fills and blocks the server.
        threading.Thread(target=self._drain, args=(proc.stdout,), daemon=True).start()

    def _read_log(self, pipe: IO[bytes]) -> None:
        for line in pipe:
            with self._logged:
                self._lines.append(line.decode(errors="replace").rstrip("\n"))
                self._logged.notify_all()

    @staticmethod
    def _drain(pipe: IO[bytes]) -> None:
        for _ in pipe:
            pass

    def _index(self, text: str, since: int) -> int | None:
        for i in range(since, len(self._lines)):
            if text in self._lines[i]:
                return i
        return None

    def mark(self) -> int:
        """Sends a request of its own and waits for its line, so that late lines of earlier requests
        land before the mark."""
        path = f"{STREAM_TEST_PREFIX}Mark{next(self._marks)}"
        conn = http.client.HTTPConnection("127.0.0.1", self._port, timeout=5)
        try:
            conn.request("POST", path, body=b"")
            conn.getresponse().read()
        finally:
            conn.close()
        return self.wait_for_log(f"{path} rejected", since=0) + 1

    def wait_for_log(self, text: str, since: int, timeout: float = 10.0) -> int:
        """Waits for a line containing text logged at or after since, and returns its position."""
        with self._logged:
            found = self._logged.wait_for(lambda: self._index(text, since) is not None, timeout)
            if not found:
                log = "\n".join(self._lines[since:])
                raise AssertionError(f"{text!r} not logged within {timeout}s; logged since the mark:\n{log}")
            index = self._index(text, since)
            assert index is not None
            return index

    def logs(self, text: str, since: int, within: float) -> bool:
        """Whether a line containing text is logged at or after since, waiting up to within."""
        with self._logged:
            return self._logged.wait_for(lambda: self._index(text, since) is not None, within)


@pytest.fixture(scope="module")
def go_server() -> Iterator[_GoServer]:
    """Starts `go_helper serve`, which serves Connect over HTTP/1.1 and gRPC over h2c."""
    port = _find_free_port()
    proc = subprocess.Popen(
        [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    try:
        server = _GoServer(proc, port)
        _wait_for_port(port)
        yield server
    finally:
        proc.terminate()
        proc.wait(timeout=5)


# -- Clients --


def _async_client(
    client_class,
    base_url: str,
    protocol: str,
    stream_timeout: float = DEFAULT_STREAM_TIMEOUT,
    wire_format: WireFormat = WireFormat.BINARY,
):
    return new_service_client(
        CLIENT_PRIVATE_KEY,
        client_class,
        base_url=base_url,
        stream_timeout=stream_timeout,
        wire_format=wire_format,
        protocol=Protocol(protocol),
    )


def _sync_client(
    client_class,
    base_url: str,
    protocol: str,
    stream_timeout: float = DEFAULT_STREAM_TIMEOUT,
    wire_format: WireFormat = WireFormat.BINARY,
):
    return new_service_client_sync(
        CLIENT_PRIVATE_KEY,
        client_class,
        base_url=base_url,
        stream_timeout=stream_timeout,
        wire_format=wire_format,
        protocol=Protocol(protocol),
    )


def _record_signed(monkeypatch: pytest.MonkeyPatch) -> list[bytes]:
    """Records the bytes each request is signed over."""
    signed: list[bytes] = []
    sign_request = signing._sign_request

    def recording(sign_fn, body, headers):
        signed.append(body)
        return sign_request(sign_fn, body, headers)

    monkeypatch.setattr(signing, "_sign_request", recording)
    return signed


def _assert_one_uncompressed_envelope(signed: bytes) -> None:
    assert signed[0] == 0x00
    assert len(signed) == 5 + int.from_bytes(signed[1:5], "big")


def _stale_timestamps(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(signing, "_timestamp_ms", lambda: int(time.time() * 1000) - STALE_BY_MS)


def _large_value() -> str:
    # 256 KiB: the first envelope spans many reads and writes of the transport.
    return base64.b64encode(os.urandom(192 * 1024)).decode()


async def _stream_of(*values: str) -> AsyncIterator[StringValue]:
    for value in values:
        yield StringValue(value=value)


def _sync_stream_of(*values: str) -> Iterator[StringValue]:
    for value in values:
        yield StringValue(value=value)


def _json_envelope(value: str) -> bytes:
    payload = f'"{value}"'.encode()
    return bytes([0]) + len(payload).to_bytes(4, "big") + payload


@pytest.mark.asyncio
@pytest.mark.parametrize("protocol", ["connect", "grpc"])
class TestPythonAsyncClientGoServerStream:
    """Python async client -> Go server."""

    async def test_client_stream(self, go_server: _GoServer, protocol: str, monkeypatch: pytest.MonkeyPatch) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        signed = _record_signed(monkeypatch)

        response = await client.client_stream(_stream_of(*MESSAGES))

        assert response.value == ENVELOPE + ",".join(MESSAGES)
        assert len(signed) == 1
        _assert_one_uncompressed_envelope(signed[0])

    async def test_server_stream(self, go_server: _GoServer, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        received = [msg.value async for msg in client.server_stream(StringValue(value="hello"))]

        assert received == [ENVELOPE + "hello"] * 3

    async def test_client_stream_is_sent_before_its_second_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        mark = go_server.mark()

        async def messages() -> AsyncIterator[StringValue]:
            yield StringValue(value="m1")
            # A client that buffered the stream would send nothing yet, and this would time out.
            await asyncio.to_thread(go_server.wait_for_log, CLIENT_STREAM_VERIFIED, mark)
            yield StringValue(value="m2")
            yield StringValue(value="m3")

        response = await client.client_stream(messages())
        assert response.value == ENVELOPE + "m1,m2,m3"

    async def test_large_first_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        large = _large_value()

        response = await client.client_stream(_stream_of(large, "tail"))

        assert response.value == f"{ENVELOPE}{large},tail"

    async def test_stale_timestamp_is_rejected(
        self, go_server: _GoServer, protocol: str, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        _stale_timestamps(monkeypatch)

        with pytest.raises(ConnectError) as exc_info:
            await client.client_stream(_stream_of(*MESSAGES))

        assert exc_info.value.code == Code.UNAUTHENTICATED
        assert "timestamp is outside the allowed time window" in exc_info.value.message

    async def test_empty_client_stream_is_rejected(self, go_server: _GoServer, protocol: str) -> None:
        """Signed over empty bytes and sent; the server rejects a stream without a first message."""
        client = _async_client(_StreamTestClient, go_server.url, protocol)
        with pytest.raises(ConnectError) as exc_info:
            await client.client_stream(_stream_of())

        assert exc_info.value.code == Code.UNAUTHENTICATED
        assert "no first message" in exc_info.value.message

    async def test_stream_timeout_before_the_first_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _async_client(_StreamTestClient, go_server.url, protocol, stream_timeout=0.2)
        mark = go_server.mark()

        async def late() -> AsyncIterator[StringValue]:
            await asyncio.sleep(1)
            yield StringValue(value="m1")

        with pytest.raises(ConnectError) as exc_info:
            await client.client_stream(late())

        assert exc_info.value.code == Code.DEADLINE_EXCEEDED
        assert not go_server.logs(f"{CLIENT_STREAM_PATH} ", since=mark, within=0.5), "nothing is sent"


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
class TestPythonSyncClientGoServerStream:
    """Python sync client -> Go server."""

    def test_client_stream(self, go_server: _GoServer, protocol: str, monkeypatch: pytest.MonkeyPatch) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        signed = _record_signed(monkeypatch)

        response = client.client_stream(_sync_stream_of(*MESSAGES))

        assert response.value == ENVELOPE + ",".join(MESSAGES)
        assert len(signed) == 1
        _assert_one_uncompressed_envelope(signed[0])

    def test_server_stream(self, go_server: _GoServer, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        received = [msg.value for msg in client.server_stream(StringValue(value="hello"))]

        assert received == [ENVELOPE + "hello"] * 3

    def test_client_stream_is_sent_before_its_second_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        mark = go_server.mark()

        def messages() -> Iterator[StringValue]:
            yield StringValue(value="m1")
            # A client that buffered the stream would send nothing yet, and this would time out.
            go_server.wait_for_log(CLIENT_STREAM_VERIFIED, since=mark)
            yield StringValue(value="m2")
            yield StringValue(value="m3")

        response = client.client_stream(messages())
        assert response.value == ENVELOPE + "m1,m2,m3"

    def test_large_first_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        large = _large_value()

        response = client.client_stream(_sync_stream_of(large, "tail"))

        assert response.value == f"{ENVELOPE}{large},tail"

    def test_stale_timestamp_is_rejected(
        self, go_server: _GoServer, protocol: str, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        _stale_timestamps(monkeypatch)

        with pytest.raises(ConnectError) as exc_info:
            client.client_stream(_sync_stream_of(*MESSAGES))

        assert exc_info.value.code == Code.UNAUTHENTICATED
        assert "timestamp is outside the allowed time window" in exc_info.value.message

    def test_empty_client_stream_is_rejected(self, go_server: _GoServer, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol)
        with pytest.raises(ConnectError) as exc_info:
            client.client_stream(_sync_stream_of())

        assert exc_info.value.code == Code.UNAUTHENTICATED
        assert "no first message" in exc_info.value.message

    def test_stream_timeout_before_the_first_message(self, go_server: _GoServer, protocol: str) -> None:
        client = _sync_client(_StreamTestClientSync, go_server.url, protocol, stream_timeout=0.2)
        mark = go_server.mark()

        def late() -> Iterator[StringValue]:
            time.sleep(1)
            yield StringValue(value="m1")

        with pytest.raises(ConnectError) as exc_info:
            client.client_stream(late())

        assert exc_info.value.code == Code.DEADLINE_EXCEEDED
        assert not go_server.logs(f"{CLIENT_STREAM_PATH} ", since=mark, within=0.5), "nothing is sent"


# A gRPC unary call goes out through stream() as well, signed over its one envelope.


@pytest.mark.asyncio
async def test_async_grpc_unary_health_check(go_server: _GoServer) -> None:
    client = _async_client(HealthClient, go_server.url, "grpc")
    response = await client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
    assert response.status == health_pb2.HealthCheckResponse.SERVING


def test_sync_grpc_unary_health_check(go_server: _GoServer) -> None:
    client = _sync_client(HealthClientSync, go_server.url, "grpc")
    response = client.check(health_pb2.HealthCheckRequest(service=HEALTH_SERVICE_FQN))
    assert response.status == health_pb2.HealthCheckResponse.SERVING


# The codec does not matter: Connect JSON streams are signed over their first envelope as sent.


@pytest.mark.asyncio
async def test_async_connect_json_streams(go_server: _GoServer, monkeypatch: pytest.MonkeyPatch) -> None:
    client = _async_client(_StreamTestClient, go_server.url, "connect", wire_format=WireFormat.JSON)
    signed = _record_signed(monkeypatch)

    response = await client.client_stream(_stream_of(*MESSAGES))
    assert response.value == ENVELOPE + ",".join(MESSAGES)

    received = [msg.value async for msg in client.server_stream(StringValue(value="hello"))]
    assert received == [ENVELOPE + "hello"] * 3

    assert signed == [_json_envelope(MESSAGES[0]), _json_envelope("hello")]


def test_sync_connect_json_streams(go_server: _GoServer, monkeypatch: pytest.MonkeyPatch) -> None:
    client = _sync_client(_StreamTestClientSync, go_server.url, "connect", wire_format=WireFormat.JSON)
    signed = _record_signed(monkeypatch)

    response = client.client_stream(_sync_stream_of(*MESSAGES))
    assert response.value == ENVELOPE + ",".join(MESSAGES)

    received = [msg.value for msg in client.server_stream(StringValue(value="hello"))]
    assert received == [ENVELOPE + "hello"] * 3

    assert signed == [_json_envelope(MESSAGES[0]), _json_envelope("hello")]
