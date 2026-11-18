"""Signed calls to new_asgi_app and new_wsgi_app in memory, through pyqwest's test transports.

The client signs with SigningClient / SigningSyncClient, the server verifies with the SDK's middleware
and signature interceptor, and connectrpc runs on both ends. No port is opened: pyqwest.testing's
ASGITransport and WSGITransport hand each request to the app (connectrpc.com/docs/python/testing).
ASGITransport's receive gives the body, then waits while the client stays connected, as a server's
does.
"""

import asyncio
import time

import pytest
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from pyqwest.testing import ASGITransport, WSGITransport
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network import Protocol, WireFormat
from t0_provider_sdk.network.client import _client_kwargs
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient
from t0_provider_sdk.provider import handler, handler_sync, new_asgi_app, new_wsgi_app
from tzero.v1.payment import provider_pb2 as payment_pb2
from tzero.v1.payment.provider_connect import (
    ProviderServiceASGIApplication,
    ProviderServiceClient,
    ProviderServiceClientSync,
    ProviderServiceWSGIApplication,
)

# The network's key: the server is built with its public key, the client signs as the network.
NETWORK_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
NETWORK_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
OTHER_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"

BASE_URL = "http://provider.test"
PAY_OUT = payment_pb2.PayoutRequest(payment_id=42, payout_id=7, currency="EUR")
ACCEPTED = payment_pb2.PayoutResponse(accepted=payment_pb2.PayoutResponse.Accepted())
# Generous: an answer comes in milliseconds. A call cancelled or stuck fails instead of hanging.
CALL_TIMEOUT_MS = 5_000

# The protocols and wire formats of the client factories.
CLIENTS = [
    pytest.param(Protocol.CONNECT, WireFormat.BINARY, id="connect-proto"),
    pytest.param(Protocol.CONNECT, WireFormat.JSON, id="connect-json"),
    pytest.param(Protocol.GRPC, WireFormat.BINARY, id="grpc-proto"),
    pytest.param(Protocol.GRPC, WireFormat.JSON, id="grpc-json"),
]

NETWORK_SIGNER = new_signer_from_hex(NETWORK_PRIVATE_KEY)


def _bad_signature(digest: bytes) -> tuple[bytes, bytes]:
    """Signs with the network key, but over another digest: the signature does not verify."""
    return NETWORK_SIGNER(legacy_keccak256(b"another message"))


# A signer the server refuses, and the refusal: (signer, message). Both are UNAUTHENTICATED.
REFUSED_SIGNERS = [
    pytest.param(_bad_signature, "signature verification failed", id="bad signature"),
    pytest.param(new_signer_from_hex(OTHER_PRIVATE_KEY), "request signed with unknown public key", id="other key"),
]


class _ProviderService:
    """Records the PayOut requests it serves. With await_first, it awaits before it answers, as a
    handler that calls the network or a database does."""

    def __init__(self, *, await_first: bool = False) -> None:
        self.requests: list[payment_pb2.PayoutRequest] = []
        self._await_first = await_first

    async def pay_out(self, request, ctx):
        if self._await_first:
            # Long enough for connectrpc's disconnect watch to run; sleep(0) may finish first.
            await asyncio.sleep(0.02)
        self.requests.append(request)
        return ACCEPTED

    async def update_payment(self, request, ctx):
        raise AssertionError("not called")

    async def update_limit(self, request, ctx):
        raise AssertionError("not called")

    async def append_ledger_entries(self, request, ctx):
        raise AssertionError("not called")

    async def approve_payment_quotes(self, request, ctx):
        raise AssertionError("not called")


class _ProviderServiceSync:
    def __init__(self, *, sleep_first: bool = False) -> None:
        self.requests: list[payment_pb2.PayoutRequest] = []
        self._sleep_first = sleep_first

    def pay_out(self, request, ctx):
        if self._sleep_first:
            time.sleep(0.02)
        self.requests.append(request)
        return ACCEPTED

    def update_payment(self, request, ctx):
        raise AssertionError("not called")

    def update_limit(self, request, ctx):
        raise AssertionError("not called")

    def append_ledger_entries(self, request, ctx):
        raise AssertionError("not called")

    def approve_payment_quotes(self, request, ctx):
        raise AssertionError("not called")


def _asgi_client(service, signer, protocol: Protocol, wire_format: WireFormat) -> ProviderServiceClient:
    """A client of new_asgi_app serving service, configured as new_service_client configures one."""
    app = new_asgi_app(NETWORK_PUBLIC_KEY, handler(ProviderServiceASGIApplication, service))
    http_client = SigningClient(signer, transport=ASGITransport(app))
    return ProviderServiceClient(BASE_URL, http_client=http_client, **_client_kwargs(wire_format, protocol))


def _input_terminated(app):
    """Marks the input as ending where the body ends, as a WSGI server does for a body it reads to
    its end. WSGITransport's input does end there, but the transport sets neither CONTENT_LENGTH nor
    wsgi.input_terminated, and without either the middleware reads no body (PEP 3333)."""

    def marked(environ, start_response):
        environ["wsgi.input_terminated"] = True
        return app(environ, start_response)

    return marked


def _wsgi_client(service, signer, protocol: Protocol, wire_format: WireFormat) -> ProviderServiceClientSync:
    app = new_wsgi_app(NETWORK_PUBLIC_KEY, handler_sync(ProviderServiceWSGIApplication, service))
    http_client = SigningSyncClient(signer, transport=WSGITransport(_input_terminated(app)))
    return ProviderServiceClientSync(BASE_URL, http_client=http_client, **_client_kwargs(wire_format, protocol))


class TestASGI:
    @pytest.mark.parametrize(("protocol", "wire_format"), CLIENTS)
    async def test_signed_call_is_served(self, protocol, wire_format):
        service = _ProviderService()
        client = _asgi_client(service, NETWORK_SIGNER, protocol, wire_format)

        assert await client.pay_out(PAY_OUT, timeout_ms=CALL_TIMEOUT_MS) == ACCEPTED
        assert service.requests == [PAY_OUT]

    @pytest.mark.parametrize(("protocol", "wire_format"), CLIENTS)
    @pytest.mark.parametrize(("signer", "message"), REFUSED_SIGNERS)
    async def test_refused_call_never_reaches_the_handler(self, protocol, wire_format, signer, message):
        service = _ProviderService()
        client = _asgi_client(service, signer, protocol, wire_format)

        with pytest.raises(ConnectError) as exc_info:
            await client.pay_out(PAY_OUT, timeout_ms=CALL_TIMEOUT_MS)
        assert (exc_info.value.code, exc_info.value.message) == (Code.UNAUTHENTICATED, message)
        assert service.requests == []

    @pytest.mark.parametrize(("protocol", "wire_format"), CLIENTS)
    async def test_handler_that_awaits_answers(self, protocol, wire_format):
        """A handler that awaits runs to its answer while the client stays connected. The replayed
        receive must not report a disconnect after the body: connectrpc would cancel the handler and
        the caller would get CANCELED (#455)."""
        service = _ProviderService(await_first=True)
        client = _asgi_client(service, NETWORK_SIGNER, protocol, wire_format)

        answer = await asyncio.wait_for(client.pay_out(PAY_OUT, timeout_ms=CALL_TIMEOUT_MS), CALL_TIMEOUT_MS / 1000)
        assert answer == ACCEPTED
        assert service.requests == [PAY_OUT]


class TestWSGI:
    @pytest.mark.parametrize(("protocol", "wire_format"), CLIENTS)
    @pytest.mark.parametrize("sleep_first", [False, True], ids=["answers at once", "sleeps first"])
    def test_signed_call_is_served(self, protocol, wire_format, sleep_first):
        service = _ProviderServiceSync(sleep_first=sleep_first)
        client = _wsgi_client(service, NETWORK_SIGNER, protocol, wire_format)

        assert client.pay_out(PAY_OUT, timeout_ms=CALL_TIMEOUT_MS) == ACCEPTED
        assert service.requests == [PAY_OUT]

    @pytest.mark.parametrize(("protocol", "wire_format"), CLIENTS)
    @pytest.mark.parametrize(("signer", "message"), REFUSED_SIGNERS)
    def test_refused_call_never_reaches_the_handler(self, protocol, wire_format, signer, message):
        service = _ProviderServiceSync()
        client = _wsgi_client(service, signer, protocol, wire_format)

        with pytest.raises(ConnectError) as exc_info:
            client.pay_out(PAY_OUT, timeout_ms=CALL_TIMEOUT_MS)
        assert (exc_info.value.code, exc_info.value.message) == (Code.UNAUTHENTICATED, message)
        assert service.requests == []
