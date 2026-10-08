"""Tests for the base URL and signer options of the client factories."""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest
from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec
from connectrpc.errors import ConnectError
from connectrpc.method import IdempotencyLevel, MethodInfo
from google.protobuf.wrappers_pb2 import StringValue
from t0_provider_sdk.common.headers import PUBLIC_KEY_HEADER, SIGNATURE_HEADER
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network import (
    DEFAULT_BASE_URL,
    Protocol,
    new_service_client,
    new_service_client_sync,
)
from t0_provider_sdk.network.client import _transport

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"
FACTORIES = [new_service_client, new_service_client_sync]
VECTORS_PATH = Path(__file__).resolve().parents[4] / "cross_test" / "test_vectors.json"
BASE_URL_VECTORS = json.loads(VECTORS_PATH.read_text())["base_url_parsing"]

UNARY = MethodInfo(
    name="Unary",
    service_name="test.v1.StreamTest",
    input=StringValue,
    output=StringValue,
    idempotency_level=IdempotencyLevel.UNKNOWN,
)


CLIENT_STREAM = MethodInfo(
    name="ClientStream",
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

    async def client_stream(self, requests):
        return await self.execute_client_stream(request=requests, method=CLIENT_STREAM)


class _SyncClient(ConnectClientSync):
    def __init__(self, address: str, **kwargs) -> None:
        super().__init__(address, codec=google_protobuf_binary_codec(), **kwargs)

    def unary(self, request):
        return self.execute_unary(request=request, method=UNARY)

    def client_stream(self, requests):
        return self.execute_client_stream(request=requests, method=CLIENT_STREAM)


class _SentError(Exception):
    pass


class _RecordingClient:
    """Stands in for pyqwest.Client: records the request headers, then fails the call."""

    def __init__(self) -> None:
        self.headers = None

    async def post(self, url, headers=None, content=None):
        self.headers = headers
        raise _SentError


class _RecordingSyncClient:
    def __init__(self) -> None:
        self.headers = None

    def post(self, url, headers=None, content=None, timeout=None):
        self.headers = headers
        raise _SentError


def _failing_signer(digest: bytes) -> tuple[bytes, bytes]:
    raise RuntimeError("key store unavailable")


async def _one_message():
    yield StringValue(value="m1")


class TestBaseURL:
    @pytest.mark.parametrize(
        ("factory", "client_class"), [(new_service_client, _Client), (new_service_client_sync, _SyncClient)]
    )
    def test_none_means_the_default(self, factory, client_class) -> None:
        client = factory(PRIVATE_KEY, client_class, base_url=None)
        assert client._address == DEFAULT_BASE_URL

    @pytest.mark.parametrize("vec", BASE_URL_VECTORS, ids=lambda vec: vec["name"])
    @pytest.mark.parametrize("factory", FACTORIES)
    def test_cross_vector(self, factory, vec) -> None:
        """The rows every SDK shares (base_url_parsing in cross_test/test_vectors.json)."""
        base_url = vec["input"]
        if vec["valid"]:
            client = factory(PRIVATE_KEY, _Client, base_url=base_url)
            # Read as https without "://", and a trailing "/" is dropped: connectrpc appends
            # "/<service>/<method>" to the address.
            expected = base_url if "://" in base_url else "https://" + base_url
            assert client._address == expected.removesuffix("/")
        else:
            with pytest.raises(ValueError, match=f"^{re.escape(vec['error'])}$"):
                factory(PRIVATE_KEY, _Client, base_url=base_url)


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

    @pytest.mark.asyncio
    async def test_failing_signer_fails_the_call_internal(self) -> None:
        """Internal "signing the request failed: <cause>", not a retryable Unavailable; nothing sent."""
        client = new_service_client("", _Client, base_url="http://example.test", sign_fn=_failing_signer)
        recorder = client._http_client._inner = _RecordingClient()
        with pytest.raises(ConnectError) as exc_info:
            await client.unary(StringValue(value="m1"))
        assert exc_info.value.code == Code.INTERNAL
        assert exc_info.value.message == "signing the request failed: key store unavailable"
        assert recorder.headers is None

    @pytest.mark.asyncio
    @pytest.mark.parametrize(
        ("protocol", "kind"),
        [(Protocol.GRPC, "unary"), (Protocol.CONNECT, "client_stream"), (Protocol.GRPC, "client_stream")],
        ids=["grpc unary", "connect client stream", "grpc client stream"],
    )
    async def test_failing_signer_fails_a_stream_internal(self, protocol, kind) -> None:
        """stream() carries every gRPC call and every stream; the signer fails inside it."""
        client = new_service_client(
            "", _Client, base_url="http://example.test", protocol=protocol, sign_fn=_failing_signer
        )
        with pytest.raises(ConnectError) as exc_info:
            if kind == "unary":
                await client.unary(StringValue(value="m1"))
            else:
                await client.client_stream(_one_message())
        assert exc_info.value.code == Code.INTERNAL
        assert exc_info.value.message == "signing the request failed: key store unavailable"

    @pytest.mark.parametrize(
        ("protocol", "kind"),
        [(Protocol.GRPC, "unary"), (Protocol.CONNECT, "client_stream"), (Protocol.GRPC, "client_stream")],
        ids=["grpc unary", "connect client stream", "grpc client stream"],
    )
    def test_failing_signer_fails_a_sync_stream_internal(self, protocol, kind) -> None:
        client = new_service_client_sync(
            "", _SyncClient, base_url="http://example.test", protocol=protocol, sign_fn=_failing_signer
        )
        with pytest.raises(ConnectError) as exc_info:
            if kind == "unary":
                client.unary(StringValue(value="m1"))
            else:
                client.client_stream(iter([StringValue(value="m1")]))
        assert exc_info.value.code == Code.INTERNAL
        assert exc_info.value.message == "signing the request failed: key store unavailable"

    def test_failing_signer_fails_the_sync_call_internal(self) -> None:
        client = new_service_client_sync("", _SyncClient, base_url="http://example.test", sign_fn=_failing_signer)
        recorder = client._http_client._inner = _RecordingSyncClient()
        with pytest.raises(ConnectError) as exc_info:
            client.unary(StringValue(value="m1"))
        assert exc_info.value.code == Code.INTERNAL
        assert exc_info.value.message == "signing the request failed: key store unavailable"
        assert recorder.headers is None

    @pytest.mark.asyncio
    async def test_signer_as_the_first_argument(self) -> None:
        """new_signer_from_hex's result, or any SignFn, is the client's signer as it is."""
        client = new_service_client(new_signer_from_hex(OTHER_PRIVATE_KEY), _Client, base_url="http://example.test")
        recorder = client._http_client._inner = _RecordingClient()

        with pytest.raises(ConnectError):
            await client.unary(StringValue(value="m1"))
        assert recorder.headers[PUBLIC_KEY_HEADER] == OTHER_PUBLIC_KEY

    @pytest.mark.parametrize("factory", FACTORIES)
    @pytest.mark.parametrize("key", [PRIVATE_KEY, new_signer_from_hex(PRIVATE_KEY)], ids=["hex key", "signer"])
    def test_key_and_sign_fn_together_are_refused(self, factory, key) -> None:
        """Neither is silently dropped for the other."""
        with pytest.raises(ValueError, match="^a private key and a signer must not both be given$"):
            factory(key, _Client, sign_fn=new_signer_from_hex(OTHER_PRIVATE_KEY))

    @pytest.mark.parametrize("factory", FACTORIES)
    def test_none_signer_is_refused(self, factory) -> None:
        with pytest.raises(ValueError, match="^signer must not be null$"):
            factory(None, _Client)

    @pytest.mark.asyncio
    @pytest.mark.parametrize(
        ("signature", "public_key", "cause"),
        [
            (b"\x01" * 63, None, "signature must be 64 or 65 bytes"),
            (b"\x01" * 66, None, "signature must be 64 or 65 bytes"),
            ("01" * 65, None, "signature must be 64 or 65 bytes"),
            (None, b"\x02" + b"\x01" * 32, "public key must be 65 bytes, uncompressed"),
            (None, b"\x06" + b"\x01" * 64, "public key must be 65 bytes, uncompressed"),
            (None, OTHER_PUBLIC_KEY, "public key must be 65 bytes, uncompressed"),
            (b"\x01" * 63, b"\x02" + b"\x01" * 32, "signature must be 64 or 65 bytes"),
        ],
        ids=["63 bytes", "66 bytes", "hex text", "compressed key", "hybrid key", "key as hex text", "both"],
    )
    async def test_signer_output_is_checked_before_sending(self, signature, public_key, cause) -> None:
        """The signature's length first, then the key; nothing is sent."""
        sign = new_signer_from_hex(OTHER_PRIVATE_KEY)

        def custom(digest: bytes) -> tuple[bytes, bytes]:
            real_signature, real_public_key = sign(digest)
            return (
                real_signature if signature is None else signature,
                real_public_key if public_key is None else public_key,
            )

        client = new_service_client(custom, _Client, base_url="http://example.test")
        recorder = client._http_client._inner = _RecordingClient()
        with pytest.raises(ConnectError) as exc_info:
            await client.unary(StringValue(value="m1"))
        assert exc_info.value.code == Code.INTERNAL
        assert exc_info.value.message == f"signing the request failed: {cause}"
        assert recorder.headers is None

    @pytest.mark.asyncio
    @pytest.mark.parametrize("length", [64, 65])
    async def test_signature_is_sent_as_the_signer_returned_it(self, length) -> None:
        """64 bytes r || s, or 65 with any last byte: sent unchanged."""
        sign = new_signer_from_hex(OTHER_PRIVATE_KEY)

        def custom(digest: bytes) -> tuple[bytes, bytes]:
            signature, public_key = sign(digest)
            return (signature[:64] + b"\x1c")[:length], public_key

        client = new_service_client(custom, _Client, base_url="http://example.test")
        recorder = client._http_client._inner = _RecordingClient()
        with pytest.raises(ConnectError):
            await client.unary(StringValue(value="m1"))
        sent = bytes.fromhex(recorder.headers[SIGNATURE_HEADER].removeprefix("0x"))
        assert len(sent) == length
        assert sent[64:] == (b"\x1c" if length == 65 else b"")

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
