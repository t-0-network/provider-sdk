"""Tests for the rejection of bidirectional streams by the client factories.

Each call goes through the real ConnectRPC client down to a fake pyqwest client that records any
request it is asked to send.
"""

from __future__ import annotations

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

BIDI_STREAM = MethodInfo(
    name="BidiStream",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)


class _Client(ConnectClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def bidi_stream(self, request, *, timeout_ms=None):
        return self.execute_bidi_stream(request=request, method=BIDI_STREAM, timeout_ms=timeout_ms)


class _GRPCClient(_Client):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, protocol=ProtocolType.GRPC, **kwargs)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def bidi_stream(self, request, *, timeout_ms=None):
        return self.execute_bidi_stream(request=request, method=BIDI_STREAM, timeout_ms=timeout_ms)


class _GRPCSyncClient(_SyncClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, protocol=ProtocolType.GRPC, **kwargs)


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
    @pytest.mark.parametrize("client_class", [_Client, _GRPCClient])
    def test_async_client(self, client_class) -> None:
        """The async execute_bidi_stream returns the response iterator, so the call itself fails."""
        read: list[str] = []

        async def messages():
            read.append("m1")
            yield StringValue(value="m1")

        client = new_service_client(PRIVATE_KEY, client_class, base_url=BASE_URL, stream_timeout=30)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            client.bidi_stream(messages())
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert exc_info.value.message == "bidirectional streams are not supported"
        assert recorder.calls == []
        assert read == []

    @pytest.mark.parametrize("client_class", [_SyncClient, _GRPCSyncClient])
    def test_sync_client(self, client_class) -> None:
        read: list[str] = []

        def messages():
            read.append("m1")
            yield StringValue(value="m1")

        client = new_service_client_sync(PRIVATE_KEY, client_class, base_url=BASE_URL, stream_timeout=30)
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError) as exc_info:
            client.bidi_stream(messages(), timeout_ms=700)
        assert exc_info.value.code == Code.UNIMPLEMENTED
        assert recorder.calls == []
        assert read == []
