"""Tests for the unary and stream default timeouts of the client factories.

Calls go through the real ConnectRPC client and signing transport to a fake pyqwest client, which
records the timeout header (and the sync pyqwest timeout) and fails the call.
"""

from __future__ import annotations

import math
from contextlib import asynccontextmanager, contextmanager

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from connectrpc.protocol import ProtocolType
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.network.client import new_service_client, new_service_client_sync

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
BASE_URL = "http://example.test"


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
SERVER_STREAM = _method("ServerStream")


class _Client(ConnectClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    async def unary(self, request, *, timeout_ms=None):
        return await self.execute_unary(request=request, method=UNARY, timeout_ms=timeout_ms)

    async def client_stream(self, request, *, timeout_ms=None):
        return await self.execute_client_stream(request=request, method=CLIENT_STREAM, timeout_ms=timeout_ms)

    def server_stream(self, request, *, timeout_ms=None):
        return self.execute_server_stream(request=request, method=SERVER_STREAM, timeout_ms=timeout_ms)


class _GRPCClient(_Client):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, protocol=ProtocolType.GRPC, **kwargs)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def unary(self, request, *, timeout_ms=None):
        return self.execute_unary(request=request, method=UNARY, timeout_ms=timeout_ms)

    def client_stream(self, request, *, timeout_ms=None):
        return self.execute_client_stream(request=request, method=CLIENT_STREAM, timeout_ms=timeout_ms)

    def server_stream(self, request, *, timeout_ms=None):
        return self.execute_server_stream(request=request, method=SERVER_STREAM, timeout_ms=timeout_ms)


class _GRPCSyncClient(_SyncClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, protocol=ProtocolType.GRPC, **kwargs)


class _SentError(Exception):
    pass


def _timeout_header(headers) -> str | None:
    return headers.get("connect-timeout-ms") or headers.get("grpc-timeout")


class _RecordingClient:
    """Stands in for pyqwest.Client: records the call's timeout header, then fails the call."""

    def __init__(self) -> None:
        self.timeout_header: str | None = None

    async def post(self, url, headers=None, content=None):
        self.timeout_header = _timeout_header(headers)
        raise _SentError

    @asynccontextmanager
    async def stream(self, method, url, headers=None, content=None):
        self.timeout_header = _timeout_header(headers)
        if hasattr(content, "aclose"):
            await content.aclose()
        raise _SentError
        yield


class _RecordingSyncClient:
    """Stands in for pyqwest.SyncClient; also records the pyqwest timeout."""

    def __init__(self) -> None:
        self.timeout_header: str | None = None
        self.timeout: float | None = None

    def post(self, url, headers=None, content=None, timeout=None):
        self.timeout_header, self.timeout = _timeout_header(headers), timeout
        raise _SentError

    @contextmanager
    def stream(self, method, url, headers=None, content=None, timeout=None):
        self.timeout_header, self.timeout = _timeout_header(headers), timeout
        if hasattr(content, "close"):
            content.close()
        raise _SentError
        yield


def _async_client(client_class=_Client, **kwargs):
    client = new_service_client(PRIVATE_KEY, client_class, base_url=BASE_URL, **kwargs)
    recorder = _RecordingClient()
    client._http_client._inner = recorder
    return client, recorder


def _sync_client(client_class=_SyncClient, **kwargs):
    client = new_service_client_sync(PRIVATE_KEY, client_class, base_url=BASE_URL, **kwargs)
    recorder = _RecordingSyncClient()
    client._http_client._inner = recorder
    return client, recorder


async def _messages():
    yield StringValue(value="m1")


async def _invoke(client, kind: str, timeout_ms: float | None = None) -> None:
    match kind:
        case "unary":
            await client.unary(StringValue(value="m1"), timeout_ms=timeout_ms)
        case "client_stream":
            await client.client_stream(_messages(), timeout_ms=timeout_ms)
        case "server_stream":
            async for _ in client.server_stream(StringValue(value="m1"), timeout_ms=timeout_ms):
                pass


def _invoke_sync(client, kind: str, timeout_ms: float | None = None) -> None:
    match kind:
        case "unary":
            client.unary(StringValue(value="m1"), timeout_ms=timeout_ms)
        case "client_stream":
            client.client_stream(iter([StringValue(value="m1")]), timeout_ms=timeout_ms)
        case "server_stream":
            for _ in client.server_stream(StringValue(value="m1"), timeout_ms=timeout_ms):
                pass


async def _call(client, kind: str, timeout_ms: float | None = None) -> None:
    with pytest.raises(ConnectError) as exc_info:
        await _invoke(client, kind, timeout_ms)
    assert exc_info.value.code == Code.UNAVAILABLE


def _call_sync(client, kind: str, timeout_ms: float | None = None) -> None:
    with pytest.raises(ConnectError) as exc_info:
        _invoke_sync(client, kind, timeout_ms)
    assert exc_info.value.code == Code.UNAVAILABLE


def _refused(option: str) -> str:
    return f"^{option} must be a positive duration of at most 2147483647 ms$"


STREAMS = ["client_stream", "server_stream"]
KINDS = ["unary", *STREAMS]
# Shorter and longer than both defaults: the call's own timeout replaces the default either way.
CALL_TIMEOUTS_MS = [700, 600_000]
BAD_TIMEOUTS_S = [0, -1.0, None, math.inf, math.nan, 2_147_484, True]
BAD_CALL_TIMEOUTS_MS = [0, -5, math.inf, math.nan, 2**31, True]


@pytest.mark.asyncio
class TestAsyncClientTimeouts:
    async def test_unary_gets_the_unary_default(self) -> None:
        client, recorder = _async_client()
        await _call(client, "unary")
        assert recorder.timeout_header == "15000"

    async def test_unary_timeout_option(self) -> None:
        client, recorder = _async_client(timeout=2.5, stream_timeout=30)
        await _call(client, "unary")
        assert recorder.timeout_header == "2500"

    async def test_grpc_unary_gets_the_unary_default(self) -> None:
        """gRPC unary goes out through stream(), but it is still a unary call."""
        client, recorder = _async_client(_GRPCClient, stream_timeout=30)
        await _call(client, "unary")
        assert recorder.timeout_header == "15000m"

    @pytest.mark.parametrize("kind", STREAMS)
    async def test_streams_get_five_minutes_by_default(self, kind: str) -> None:
        client, recorder = _async_client()
        await _call(client, kind)
        assert recorder.timeout_header == "300000"

    @pytest.mark.parametrize("kind", STREAMS)
    async def test_streams_get_the_stream_timeout_option(self, kind: str) -> None:
        client, recorder = _async_client(stream_timeout=30)
        await _call(client, kind)
        assert recorder.timeout_header == "30000"

    @pytest.mark.parametrize("kind", STREAMS)
    async def test_grpc_streams_get_the_stream_timeout_option(self, kind: str) -> None:
        client, recorder = _async_client(_GRPCClient, stream_timeout=30)
        await _call(client, kind)
        assert recorder.timeout_header == "30000m"

    @pytest.mark.parametrize("timeout_ms", CALL_TIMEOUTS_MS)
    @pytest.mark.parametrize("kind", KINDS)
    async def test_call_timeout_replaces_the_default(self, kind: str, timeout_ms: int) -> None:
        client, recorder = _async_client(stream_timeout=30)
        await _call(client, kind, timeout_ms=timeout_ms)
        assert recorder.timeout_header == str(timeout_ms)

    @pytest.mark.parametrize("timeout_ms", BAD_CALL_TIMEOUTS_MS)
    @pytest.mark.parametrize("kind", KINDS)
    async def test_bad_call_timeout_is_refused(self, kind: str, timeout_ms: float) -> None:
        client, recorder = _async_client()
        with pytest.raises(ValueError, match=_refused("timeout_ms")):
            await _invoke(client, kind, timeout_ms)
        assert recorder.timeout_header is None, "nothing is sent"


class TestSyncClientTimeouts:
    def test_unary_gets_the_unary_default(self) -> None:
        client, recorder = _sync_client()
        _call_sync(client, "unary")
        assert recorder.timeout_header == "15000"
        # connectrpc passes what is left of the call's deadline.
        assert recorder.timeout is not None
        assert 14.0 < recorder.timeout <= 15.0

    def test_grpc_unary_gets_the_unary_default(self) -> None:
        client, recorder = _sync_client(_GRPCSyncClient, stream_timeout=30)
        _call_sync(client, "unary")
        assert recorder.timeout_header == "15000m"
        assert recorder.timeout is not None
        assert 14.0 < recorder.timeout <= 15.0

    @pytest.mark.parametrize("kind", STREAMS)
    def test_streams_get_five_minutes_by_default(self, kind: str) -> None:
        client, recorder = _sync_client()
        _call_sync(client, kind)
        assert recorder.timeout_header == "300000"
        assert recorder.timeout is not None
        assert 299.0 < recorder.timeout <= 300.0

    @pytest.mark.parametrize("kind", STREAMS)
    def test_streams_get_the_stream_timeout_option(self, kind: str) -> None:
        client, recorder = _sync_client(stream_timeout=30)
        _call_sync(client, kind)
        assert recorder.timeout_header == "30000"
        assert recorder.timeout is not None
        assert 29.0 < recorder.timeout <= 30.0

    @pytest.mark.parametrize("timeout_ms", CALL_TIMEOUTS_MS)
    @pytest.mark.parametrize("kind", KINDS)
    def test_call_timeout_replaces_the_default(self, kind: str, timeout_ms: int) -> None:
        client, recorder = _sync_client(stream_timeout=30)
        _call_sync(client, kind, timeout_ms=timeout_ms)
        assert recorder.timeout_header == str(timeout_ms)
        assert recorder.timeout is not None
        assert timeout_ms / 1000 - 0.1 < recorder.timeout <= timeout_ms / 1000

    @pytest.mark.parametrize("timeout_ms", BAD_CALL_TIMEOUTS_MS)
    @pytest.mark.parametrize("kind", KINDS)
    def test_bad_call_timeout_is_refused(self, kind: str, timeout_ms: float) -> None:
        client, recorder = _sync_client()
        with pytest.raises(ValueError, match=_refused("timeout_ms")):
            _invoke_sync(client, kind, timeout_ms)
        assert recorder.timeout_header is None, "nothing is sent"


class TestTimeoutOptions:
    @pytest.mark.parametrize("value", BAD_TIMEOUTS_S)
    @pytest.mark.parametrize("option", ["timeout", "stream_timeout"])
    @pytest.mark.parametrize("factory", [new_service_client, new_service_client_sync])
    def test_bad_value_is_refused(self, factory, option: str, value: object) -> None:
        with pytest.raises(ValueError, match=_refused(option)):
            factory(PRIVATE_KEY, _Client, **{option: value})

    @pytest.mark.asyncio
    async def test_largest_value_is_accepted(self) -> None:
        client, recorder = _async_client(stream_timeout=2_147_483)
        await _call(client, "client_stream")
        assert recorder.timeout_header == "2147483000"

    @pytest.mark.asyncio
    async def test_largest_call_timeout_is_accepted(self) -> None:
        client, recorder = _async_client()
        await _call(client, "unary", timeout_ms=2**31 - 1)
        assert recorder.timeout_header == "2147483647"

    @pytest.mark.asyncio
    async def test_sub_millisecond_timeout_is_not_dropped(self) -> None:
        client, recorder = _async_client(timeout=0.0001)
        await _call(client, "unary")
        assert recorder.timeout_header == "1"
