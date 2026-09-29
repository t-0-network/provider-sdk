"""Tests for the base URL and signer options of the client factories."""

from __future__ import annotations

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.common.headers import PUBLIC_KEY_HEADER
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network import (
    DEFAULT_BASE_URL,
    Protocol,
    WireFormat,
    new_service_client,
    new_service_client_sync,
)
from t0_provider_sdk.network.client import _transport

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"
FACTORIES = [new_service_client, new_service_client_sync]

UNARY = MethodInfo(
    name="Unary",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)


class _Client(ConnectClient):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    async def unary(self, request):
        return await self.execute_unary(request=request, method=UNARY)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)


class _SentError(Exception):
    pass


class _RecordingClient:
    """Stands in for pyqwest.Client: records the request headers, then fails the call."""

    def __init__(self) -> None:
        self.headers = None

    async def post(self, url, headers=None, content=None):
        self.headers = headers
        raise _SentError


class TestBaseURL:
    @pytest.mark.parametrize(
        ("factory", "client_class"), [(new_service_client, _Client), (new_service_client_sync, _SyncClient)]
    )
    def test_none_means_the_default(self, factory, client_class) -> None:
        client = factory(PRIVATE_KEY, client_class, base_url=None)
        assert client._address == DEFAULT_BASE_URL

    @pytest.mark.parametrize("factory", FACTORIES)
    def test_empty_is_refused(self, factory) -> None:
        with pytest.raises(ValueError, match="^base URL is not set$"):
            factory(PRIVATE_KEY, _Client, base_url="")

    @pytest.mark.parametrize(
        "base_url", ["https://api.t-0.network", "http://localhost:8080", "http://127.0.0.1:1234", "http://[::1]:8080"]
    )
    @pytest.mark.parametrize("factory", FACTORIES)
    def test_valid_url_is_accepted(self, factory, base_url: str) -> None:
        client = factory(PRIVATE_KEY, _Client, base_url=base_url)
        assert client._address == base_url

    @pytest.mark.parametrize(
        "base_url",
        [
            "http://my_host:8080",
            "api.t-0.network",
            "api.t-0.network:443",
            "ftp://h",
            "http://",
            "http://:8080",
            "http:foo",
            "http://h:99999",
            "http://h:0",
            "not a url",
            "http://user@h",
            "http://h:",
            "http://bücher.example",
        ],
    )
    @pytest.mark.parametrize("factory", FACTORIES)
    def test_invalid_url_is_refused(self, factory, base_url: str) -> None:
        with pytest.raises(ValueError, match="^base URL is not valid$"):
            factory(PRIVATE_KEY, _Client, base_url=base_url)


class TestEnumOptions:
    @pytest.mark.parametrize("value", ["json", None, 1])
    @pytest.mark.parametrize("factory", FACTORIES)
    def test_wire_format_must_be_a_wire_format(self, factory, value: object) -> None:
        with pytest.raises(ValueError, match=r"^wire_format must be WireFormat\.BINARY or WireFormat\.JSON$"):
            factory(PRIVATE_KEY, _Client, wire_format=value)

    @pytest.mark.parametrize("value", ["grpc", None, WireFormat.JSON])
    @pytest.mark.parametrize("factory", FACTORIES)
    def test_protocol_must_be_a_protocol(self, factory, value: object) -> None:
        with pytest.raises(ValueError, match=r"^protocol must be Protocol\.CONNECT or Protocol\.GRPC$"):
            factory(PRIVATE_KEY, _Client, protocol=value)


class TestSigner:
    @pytest.mark.asyncio
    async def test_sign_fn_signs_in_place_of_the_key(self) -> None:
        client = new_service_client(
            "", _Client, base_url="http://example.test", sign_fn=new_signer_from_hex(OTHER_PRIVATE_KEY)
        )
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError):
            await client.unary(StringValue(value="m1"))
        assert recorder.headers[PUBLIC_KEY_HEADER] == OTHER_PUBLIC_KEY

    @pytest.mark.parametrize("factory", FACTORIES)
    def test_key_is_checked_without_sign_fn(self, factory) -> None:
        with pytest.raises(ValueError, match="^private key must not be null or empty$"):
            factory("", _Client)


class TestTransport:
    @pytest.mark.parametrize("sync", [False, True])
    def test_grpc_over_http_shares_one_transport(self, sync: bool) -> None:
        first = _transport("http://a.test", Protocol.GRPC, sync=sync)
        assert first is not None
        assert _transport("http://b.test:8080", Protocol.GRPC, sync=sync) is first
        assert _transport("http://a.test", Protocol.GRPC, sync=not sync) is not first

    @pytest.mark.parametrize(
        ("base_url", "protocol"), [("https://a.test", Protocol.GRPC), ("http://a.test", Protocol.CONNECT)]
    )
    def test_default_transport_otherwise(self, base_url: str, protocol: Protocol) -> None:
        assert _transport(base_url, protocol, sync=False) is None
