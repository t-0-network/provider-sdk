"""Tests for the calls the client factories refuse: bidirectional streams and GET requests."""

from __future__ import annotations

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.network import Protocol, new_service_client, new_service_client_sync

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
BASE_URL = "http://example.test"

BIDI_STREAM = MethodInfo(
    name="BidiStream",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)
NO_SIDE_EFFECTS = MethodInfo(
    name="Get",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
)


class _Client(ConnectClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def bidi_stream(self, request, *, timeout_ms=None):
        return self.execute_bidi_stream(request=request, method=BIDI_STREAM, timeout_ms=timeout_ms)

    async def get(self, request):
        return await self.execute_unary(request=request, method=NO_SIDE_EFFECTS, use_get=True)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def bidi_stream(self, request, *, timeout_ms=None):
        return self.execute_bidi_stream(request=request, method=BIDI_STREAM, timeout_ms=timeout_ms)

    def get(self, request):
        return self.execute_unary(request=request, method=NO_SIDE_EFFECTS, use_get=True)


class _RecordingClient:
    """Stands in for pyqwest.Client and pyqwest.SyncClient: records every request."""

    def __init__(self) -> None:
        self.calls: list[str] = []

    def get(self, *args, **kwargs):
        self.calls.append("get")
        raise AssertionError("nothing is sent")

    def post(self, *args, **kwargs):
        self.calls.append("post")
        raise AssertionError("nothing is sent")

    def stream(self, *args, **kwargs):
        self.calls.append("stream")
        raise AssertionError("nothing is sent")


class TestBidiStreamsAreRejected:
    @pytest.mark.parametrize("protocol", [Protocol.CONNECT, Protocol.GRPC])
    def test_async_client(self, protocol: Protocol) -> None:
        """The async execute_bidi_stream returns the response iterator, so the call itself fails."""
        read: list[str] = []

        async def messages():
            read.append("m1")
            yield StringValue(value="m1")

        client = new_service_client(PRIVATE_KEY, _Client, base_url=BASE_URL, protocol=protocol)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            client.bidi_stream(messages())
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert exc_info.value.message == "bidirectional streams are not supported"
        assert recorder.calls == []
        assert read == []

    @pytest.mark.parametrize("protocol", [Protocol.CONNECT, Protocol.GRPC])
    def test_sync_client(self, protocol: Protocol) -> None:
        read: list[str] = []

        def messages():
            read.append("m1")
            yield StringValue(value="m1")

        client = new_service_client_sync(PRIVATE_KEY, _SyncClient, base_url=BASE_URL, protocol=protocol)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            client.bidi_stream(messages(), timeout_ms=700)
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert recorder.calls == []
        assert read == []


class TestGetRequestsAreRefused:
    """A GET carries its message in the URL, outside the signature."""

    @pytest.mark.asyncio
    async def test_async_client(self) -> None:
        client = new_service_client(PRIVATE_KEY, _Client, base_url=BASE_URL)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            await client.get(StringValue(value="m1"))
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert exc_info.value.message == "GET requests are not supported"
        assert recorder.calls == []

    def test_sync_client(self) -> None:
        client = new_service_client_sync(PRIVATE_KEY, _SyncClient, base_url=BASE_URL)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            client.get(StringValue(value="m1"))
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert exc_info.value.message == "GET requests are not supported"
        assert recorder.calls == []
