"""Tests for new_asgi_app / new_wsgi_app: the network public key check at startup, and how the
built app answers a call by the result of its signature check.

Mirrors Go's handler_test.go: a missing or malformed key fails at startup. The calls go to the app
in-process; the answer is read as the Connect error code ("ok" for a response).
"""

import asyncio
import io
import json
import logging

import pytest
from t0_provider_sdk._version import __version__
from t0_provider_sdk.crypto.keys import private_key_from_hex
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.signing import _sign_request
from t0_provider_sdk.provider import (
    NetworkPublicKeyRequiredError,
    handler,
    handler_sync,
    new_asgi_app,
    new_wsgi_app,
)
from t0_provider_sdk.provider.health import HealthASGIApplication, HealthImpl, HealthImplSync, HealthWSGIApplication
from t0_provider_sdk.provider.interceptor import SignatureErrorInterceptor, SignatureErrorInterceptorSync
from t0_provider_sdk.provider.middleware import DEFAULT_MAX_BODY_SIZE
from tzero.v1.payment import provider_pb2 as payment_pb2
from tzero.v1.payment.provider_connect import ProviderServiceASGIApplication, ProviderServiceWSGIApplication

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
COMPRESSED_PUBLIC_KEY = "0x" + private_key_from_hex(PRIVATE_KEY).public_key.format(compressed=True).hex()
OTHER_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"

CHECK_PATH = "/grpc.health.v1.Health/Check"
PAY_OUT_PATH = "/tzero.v1.payment.ProviderService/PayOut"

BUILDERS = [pytest.param(new_asgi_app, id="asgi"), pytest.param(new_wsgi_app, id="wsgi")]
TRANSPORTS = ["asgi", "wsgi"]


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize("key", ["", "  \n"], ids=["empty", "whitespace only"])
def test_missing_key_is_rejected(build, key):
    with pytest.raises(NetworkPublicKeyRequiredError, match="network public key is not set"):
        build(key)


@pytest.mark.parametrize(
    ("register", "app_class"),
    [(handler, ProviderServiceASGIApplication), (handler_sync, ProviderServiceWSGIApplication)],
    ids=["handler", "handler_sync"],
)
def test_service_must_not_be_none(register, app_class):
    with pytest.raises(ValueError, match="^service must not be null$"):
        register(app_class, None)


def test_network_public_key_required_error_is_importable_from_handler():
    """1.2.0 and 1.2.1 had it in this module's namespace; code that imports it from there still works."""
    from t0_provider_sdk.provider.handler import NetworkPublicKeyRequiredError as FromHandler

    assert FromHandler is NetworkPublicKeyRequiredError


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize(
    "key",
    [
        "0xnot-a-key",
        "0x",
        PUBLIC_KEY[:-2],
        "0x04" + "00" * 64,
        "0x02" + PUBLIC_KEY[4:],
        PUBLIC_KEY[:40] + " " + PUBLIC_KEY[40:],
    ],
    ids=["non-hex", "prefix only", "truncated", "off-curve", "02 prefix on 65 bytes", "whitespace inside"],
)
def test_malformed_key_is_rejected(build, key):
    with pytest.raises(ValueError, match="invalid network public key") as exc_info:
        build(key)
    assert not isinstance(exc_info.value, NetworkPublicKeyRequiredError)


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize(
    "key",
    [PUBLIC_KEY, f"  {PUBLIC_KEY}\n", "0X" + PUBLIC_KEY[2:], COMPRESSED_PUBLIC_KEY],
    ids=["valid", "valid with surrounding whitespace", "0X prefix", "compressed"],
)
def test_valid_key_builds_an_app(build, key):
    assert callable(build(key))


class _StubProviderService:
    async def pay_out(self, request, ctx):
        return payment_pb2.PayoutResponse()

    async def update_payment(self, request, ctx):
        return payment_pb2.UpdatePaymentResponse()

    async def update_limit(self, request, ctx):
        return payment_pb2.UpdateLimitResponse()

    async def append_ledger_entries(self, request, ctx):
        return payment_pb2.AppendLedgerEntriesResponse()

    async def approve_payment_quotes(self, request, ctx):
        return payment_pb2.ApprovePaymentQuoteResponse()


class _StubProviderServiceSync:
    def pay_out(self, request, ctx):
        return payment_pb2.PayoutResponse()

    def update_payment(self, request, ctx):
        return payment_pb2.UpdatePaymentResponse()

    def update_limit(self, request, ctx):
        return payment_pb2.UpdateLimitResponse()

    def append_ledger_entries(self, request, ctx):
        return payment_pb2.AppendLedgerEntriesResponse()

    def approve_payment_quotes(self, request, ctx):
        return payment_pb2.ApprovePaymentQuoteResponse()


def _signed_headers(**override: str) -> dict[str, str]:
    """Headers signing an empty body (an empty request message) with PRIVATE_KEY."""
    headers = dict(_sign_request(new_signer_from_hex(PRIVATE_KEY), b"", None).items())
    headers.update(override)
    return headers


def _answer(status: int, body: bytes) -> tuple[str, str]:
    if status == 200:
        return "ok", ""
    error = json.loads(body)
    return error["code"], error.get("message", "")


async def _send_asgi(
    app, path: str, headers: dict[str, str], body: bytes = b"", content_type: str = "application/proto"
) -> list[dict]:
    scope = {
        "type": "http",
        "method": "POST",
        "path": path,
        "root_path": "",
        "query_string": b"",
        "headers": [(b"content-type", content_type.encode())] + [(k.encode(), v.encode()) for k, v in headers.items()],
        "extensions": {"http.response.trailers": {}},  # gRPC needs trailers
    }
    sent = []

    async def receive():
        return {"type": "http.request", "body": body, "more_body": False}

    async def send(message):
        sent.append(message)

    await app(scope, receive, send)
    return sent


async def _call_asgi(
    app, path: str, headers: dict[str, str], body: bytes = b"", content_type: str = "application/proto"
) -> tuple[str, str]:
    sent = await _send_asgi(app, path, headers, body, content_type)
    return _answer(sent[0]["status"], b"".join(m.get("body", b"") for m in sent[1:]))


def _call_wsgi(
    app, path: str, headers: dict[str, str], body: bytes = b"", content_type: str = "application/proto"
) -> tuple[str, str]:
    environ = {
        "REQUEST_METHOD": "POST",
        "PATH_INFO": path,
        "SCRIPT_NAME": "",
        "CONTENT_TYPE": content_type,
        "CONTENT_LENGTH": str(len(body)),
        "wsgi.input": io.BytesIO(body),
        "wsgi.errors": io.StringIO(),
    }
    for name, value in headers.items():
        environ["HTTP_" + name.upper().replace("-", "_")] = value
    statuses = []

    def start_response(status, response_headers, exc_info=None):
        statuses.append(int(status.split()[0]))

    response = b"".join(app(environ, start_response))
    return _answer(statuses[0], response)


def _call(
    transport: str,
    headers: dict[str, str],
    *options,
    path: str = CHECK_PATH,
    body: bytes = b"",
    content_type: str = "application/proto",
) -> tuple[str, str]:
    """Call new_asgi_app / new_wsgi_app, with a ProviderService registered with the given options."""
    if transport == "asgi":
        app = new_asgi_app(PUBLIC_KEY, handler(ProviderServiceASGIApplication, _StubProviderService(), *options))
        return asyncio.run(_call_asgi(app, path, headers, body, content_type))
    app = new_wsgi_app(PUBLIC_KEY, handler_sync(ProviderServiceWSGIApplication, _StubProviderServiceSync(), *options))
    return _call_wsgi(app, path, headers, body, content_type)


@pytest.mark.parametrize("transport", TRANSPORTS)
@pytest.mark.parametrize(
    ("public_key", "code"),
    [
        (PUBLIC_KEY, "ok"),
        (COMPRESSED_PUBLIC_KEY, "ok"),
        ("0X" + PUBLIC_KEY[2:], "ok"),
        ("0xnot-a-key", "unauthenticated"),
        (PUBLIC_KEY[:-1], "unauthenticated"),
        (PUBLIC_KEY[:40] + " " + PUBLIC_KEY[40:], "unauthenticated"),
        (PUBLIC_KEY + "zz", "unauthenticated"),
        ("0x", "unauthenticated"),
        ("0x02" + PUBLIC_KEY[4:], "unauthenticated"),
        (PUBLIC_KEY[:-2], "unauthenticated"),
        ("0x04" + "00" * 64, "unauthenticated"),
        (OTHER_PUBLIC_KEY, "unauthenticated"),
    ],
    ids=[
        "uncompressed",
        "compressed",
        "0X prefix",
        "not hex",
        "odd length",
        "whitespace inside",
        "trailing junk",
        "prefix only",
        "02 prefix on 65 bytes",
        "wrong length",
        "off-curve",
        "other key",
    ],
)
def test_public_key_header(transport, public_key, code):
    """X-Public-Key: either form of the network key passes; anything else present (not hex, not a key,
    another key) is unauthenticated."""
    assert _call(transport, _signed_headers(**{"x-public-key": public_key}))[0] == code


@pytest.mark.parametrize("transport", TRANSPORTS)
def test_missing_public_key_header(transport):
    headers = _signed_headers()
    del headers["x-public-key"]
    assert _call(transport, headers)[0] == "invalid_argument"


@pytest.mark.parametrize("transport", TRANSPORTS)
@pytest.mark.parametrize("timestamp", ["-1", str(2**63), str(2**64), "+1"])
def test_malformed_timestamp_is_a_bad_request(transport, timestamp):
    assert _call(transport, _signed_headers(**{"x-signature-timestamp": timestamp}))[0] == "invalid_argument"


@pytest.mark.parametrize("transport", TRANSPORTS)
@pytest.mark.parametrize("content_type", ["application/proto", "application/json"])
def test_body_over_the_limit_is_resource_exhausted(transport, content_type):
    body = b"\x00" * (DEFAULT_MAX_BODY_SIZE + 1)
    assert _call(transport, _signed_headers(), body=body, content_type=content_type) == (
        "resource_exhausted",
        f"max payload size of {DEFAULT_MAX_BODY_SIZE} bytes exceeded",
    )


def test_body_over_the_limit_is_resource_exhausted_over_grpc():
    """The body replayed after the refusal decodes as one empty message, so the answer is the
    interceptor's, not a decoding error ("unary request has zero messages")."""
    app = new_asgi_app(PUBLIC_KEY, handler(ProviderServiceASGIApplication, _StubProviderService()))
    body = b"\x00" * (DEFAULT_MAX_BODY_SIZE + 1)
    sent = asyncio.run(_send_asgi(app, CHECK_PATH, _signed_headers(), body, "application/grpc"))
    statuses = [v for m in sent for k, v in m.get("headers", []) if k == b"grpc-status"]
    assert statuses == [b"8"]  # RESOURCE_EXHAUSTED


@pytest.mark.parametrize("transport", TRANSPORTS)
def test_option_cannot_remove_the_signature_check(transport):
    """An option is handed the interceptor list and may empty it; the signature check is still run."""

    def clear_interceptors(opts):
        opts.interceptors.clear()

    assert _call(transport, _signed_headers(), clear_interceptors, path=PAY_OUT_PATH)[0] == "ok"
    assert _call(transport, {}, clear_interceptors, path=PAY_OUT_PATH)[0] == "invalid_argument"


def test_unverified_call_is_refused_over_asgi():
    """Served without the signature middleware, or after a request it verified: no result, no service."""
    unwrapped = HealthASGIApplication(HealthImpl([]), interceptors=[SignatureErrorInterceptor()])

    async def calls():
        assert (await _call_asgi(new_asgi_app(PUBLIC_KEY), CHECK_PATH, _signed_headers()))[0] == "ok"
        return await _call_asgi(unwrapped, CHECK_PATH, _signed_headers())

    assert asyncio.run(calls()) == ("internal", "no signature result in context")


def test_unverified_call_is_refused_over_wsgi():
    """Served without the signature middleware, or after a request it verified in the same thread: no
    result, no service."""
    unwrapped = HealthWSGIApplication(HealthImplSync([]), interceptors=[SignatureErrorInterceptorSync()])

    assert _call_wsgi(new_wsgi_app(PUBLIC_KEY), CHECK_PATH, _signed_headers())[0] == "ok"
    assert _call_wsgi(unwrapped, CHECK_PATH, _signed_headers()) == ("internal", "no signature result in context")


class _Records(logging.Handler):
    def __init__(self) -> None:
        super().__init__(level=logging.DEBUG)
        self.records: list[logging.LogRecord] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.records.append(record)


def _version_header(headers) -> str:
    return next(
        v.decode() if isinstance(v, bytes) else v
        for k, v in headers
        if k.lower() in (b"t0-sdk-version", "t0-sdk-version")
    )


def _report_of(transport: str, version: str | None) -> tuple[str, str]:
    """The version a server built with this override reports: in the health headers, and in the log
    line of a response that fails validation (the stub's PayoutResponse has no result)."""
    logger = logging.getLogger(f"t0_provider_sdk.tests.version.{transport}")
    logger.propagate = False
    records = _Records()
    logger.addHandler(records)
    try:
        if transport == "asgi":
            app = new_asgi_app(
                PUBLIC_KEY,
                handler(ProviderServiceASGIApplication, _StubProviderService()),
                logger=logger,
                version=version,
            )
            health = asyncio.run(_send_asgi(app, CHECK_PATH, _signed_headers()))[0]["headers"]
            assert asyncio.run(_call_asgi(app, PAY_OUT_PATH, _signed_headers()))[0] == "internal"
        else:
            app = new_wsgi_app(
                PUBLIC_KEY,
                handler_sync(ProviderServiceWSGIApplication, _StubProviderServiceSync()),
                logger=logger,
                version=version,
            )
            captured = []
            environ = {
                "REQUEST_METHOD": "POST",
                "PATH_INFO": CHECK_PATH,
                "SCRIPT_NAME": "",
                "CONTENT_TYPE": "application/proto",
                "CONTENT_LENGTH": "0",
                "wsgi.input": io.BytesIO(b""),
                "wsgi.errors": io.StringIO(),
            }
            for name, value in _signed_headers().items():
                environ["HTTP_" + name.upper().replace("-", "_")] = value
            b"".join(app(environ, lambda status, headers, exc_info=None: captured.append(headers)))
            health = captured[0]
            assert _call_wsgi(app, PAY_OUT_PATH, _signed_headers())[0] == "internal"
    finally:
        logger.removeHandler(records)
    [record] = records.records
    return _version_header(health), record.sdk_version


@pytest.mark.parametrize("transport", TRANSPORTS)
@pytest.mark.parametrize(
    ("version", "reported"),
    [("0.2.9", "0.2.9"), (None, __version__), ("", __version__), (" \t", __version__)],
    ids=["override", "none", "empty", "whitespace"],
)
def test_version_override_reaches_health_headers_and_validation_log(transport, version, reported):
    """One server reports one version: the override when it is not blank, else the SDK's."""
    assert _report_of(transport, version) == (reported, reported)


def _status(transport: str, method: str, path: str, query: str = "", content_type: str = "application/proto") -> int:
    """The HTTP status a signed call with an empty body gets from new_asgi_app / new_wsgi_app."""
    headers = _signed_headers()
    if transport == "asgi":
        app = new_asgi_app(PUBLIC_KEY, handler(ProviderServiceASGIApplication, _StubProviderService()))
        scope = {
            "type": "http",
            "method": method,
            "path": path,
            "root_path": "",
            "query_string": query.encode(),
            "headers": [(b"content-type", content_type.encode())]
            + [(k.encode(), v.encode()) for k, v in headers.items()],
            "extensions": {"http.response.trailers": {}},
        }
        sent = []

        async def receive():
            return {"type": "http.request", "body": b"", "more_body": False}

        async def send(message):
            sent.append(message)

        asyncio.run(app(scope, receive, send))
        return sent[0]["status"]
    app = new_wsgi_app(PUBLIC_KEY, handler_sync(ProviderServiceWSGIApplication, _StubProviderServiceSync()))
    environ = {
        "REQUEST_METHOD": method,
        "PATH_INFO": path,
        "SCRIPT_NAME": "",
        "QUERY_STRING": query,
        "CONTENT_TYPE": content_type,
        "CONTENT_LENGTH": "0",
        "wsgi.input": io.BytesIO(b""),
        "wsgi.errors": io.StringIO(),
    }
    for name, value in headers.items():
        environ["HTTP_" + name.upper().replace("-", "_")] = value
    statuses = []
    b"".join(app(environ, lambda status, headers, exc_info=None: statuses.append(int(status.split()[0]))))
    return statuses[0]


@pytest.mark.parametrize("transport", TRANSPORTS)
def test_health_check_is_post_only(transport):
    """A Connect GET would carry the request in the query, outside the signed body: refused, as in
    every SDK."""
    assert _status(transport, "POST", CHECK_PATH) == 200
    assert _status(transport, "GET", CHECK_PATH, query="encoding=proto&message=") == 405


@pytest.mark.parametrize(
    ("transport", "content_type"),
    [("asgi", "application/proto"), ("asgi", "application/grpc"), ("wsgi", "application/proto")],
    ids=["asgi connect", "asgi grpc", "wsgi connect"],
)
@pytest.mark.parametrize("method", ["Watch", "List"])
def test_health_watch_and_list_are_not_served(transport, content_type, method):
    """HTTP 404, which Connect and gRPC clients read as UNIMPLEMENTED, the code of every SDK."""
    assert _status(transport, "POST", f"/grpc.health.v1.Health/{method}", content_type=content_type) == 404
