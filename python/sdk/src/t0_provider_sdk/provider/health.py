"""The health service the transport mounts on every server it builds.

See `docs/HEALTH_SERVICE.md`. Reports SERVING for the services registered on
this server and NOT_FOUND for anything else; the set is frozen at construction,
so nothing is computed per request.

The messages come from `grpcio-health-checking`, which publishes them under the
top-level `grpc_health` package. That naming is not incidental: a generated
`grpc.health.v1` would sit under a `grpc` namespace package, and grpcio ships a
*regular* `grpc/__init__.py` that wins over it — so any customer venv containing
grpcio would fail to import this SDK at all.

connect-python publishes no health bindings, so the ASGI/WSGI applications are
assembled here from `Endpoint` rather than generated. Because they are not
generated, they must pass the `google.protobuf` compat codecs explicitly — the
runtime's default codec targets protobuf-py and cannot serialize these
messages. Only `Check` is mounted, and over POST only:
`Watch` is server-streaming, and this server verifies the signature of unary
calls only. A call to `Watch` gets HTTP 404, which Connect and gRPC clients read
as UNIMPLEMENTED, the code the other SDKs answer it with.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping

from connectrpc.client import ConnectClient, ConnectClientSync
from connectrpc.code import Code
from connectrpc.compat import google_protobuf_binary_codec, google_protobuf_codecs
from connectrpc.errors import ConnectError
from connectrpc.interceptor import Interceptor, InterceptorSync
from connectrpc.method import IdempotencyLevel, MethodInfo
from connectrpc.request import Headers, RequestContext
from connectrpc.server import ConnectASGIApplication, ConnectWSGIApplication, Endpoint, EndpointSync
from grpc_health.v1 import health_pb2

from t0_provider_sdk._messages import UNKNOWN_SERVICE
from t0_provider_sdk.provider._sdk_version import _reported_version
from t0_provider_sdk.provider.middleware import DEFAULT_MAX_BODY_SIZE

HEALTH_SERVICE_FQN = health_pb2.DESCRIPTOR.services_by_name["Health"].full_name

# Headers carrying the identity of the SDK answering the probe. They ride on the
# health response and nowhere else: HealthCheckResponse has a single status field
# and Check names its service in the request, so the contract itself has no room
# for this.
SDK_ECOSYSTEM_HEADER = "t0-sdk-ecosystem"
SDK_VERSION_HEADER = "t0-sdk-version"

_SDK_ECOSYSTEM = "python"
_SERVING = health_pb2.HealthCheckResponse(status=health_pb2.HealthCheckResponse.SERVING)
_CHECK_PATH = f"/{HEALTH_SERVICE_FQN}/Check"

_CHECK_METHOD = MethodInfo(
    name="Check",
    service_name=HEALTH_SERVICE_FQN,
    input=health_pb2.HealthCheckRequest,
    output=health_pb2.HealthCheckResponse,
    # Not NO_SIDE_EFFECTS, which would serve Check over GET too: the signature covers the body, and a
    # GET carries its message in the query instead. POST only, as in every SDK.
    idempotency_level=IdempotencyLevel.UNKNOWN,
)


class _Health:
    def __init__(self, services: Iterable[str], version: str | None = None) -> None:
        self._registered = frozenset(services)
        self._version = _reported_version(version)

    def _check(
        self,
        request: health_pb2.HealthCheckRequest,
        ctx: RequestContext,
    ) -> health_pb2.HealthCheckResponse:
        ctx.response_headers[SDK_ECOSYSTEM_HEADER] = _SDK_ECOSYSTEM
        ctx.response_headers[SDK_VERSION_HEADER] = self._version

        # An empty service name asks about the process as a whole, which is up if
        # this handler is running at all.
        if request.service and request.service not in self._registered:
            raise ConnectError(Code.NOT_FOUND, UNKNOWN_SERVICE.format(service=request.service))
        return _SERVING


class HealthImpl(_Health):
    """Async (ASGI) health implementation."""

    async def check(
        self,
        request: health_pb2.HealthCheckRequest,
        ctx: RequestContext,
    ) -> health_pb2.HealthCheckResponse:
        return self._check(request, ctx)


class HealthImplSync(_Health):
    """Sync (WSGI) health implementation."""

    def check(
        self,
        request: health_pb2.HealthCheckRequest,
        ctx: RequestContext,
    ) -> health_pb2.HealthCheckResponse:
        return self._check(request, ctx)


class HealthASGIApplication(ConnectASGIApplication[HealthImpl]):
    def __init__(
        self,
        service: HealthImpl,
        *,
        interceptors: Iterable[Interceptor] = (),
        read_max_bytes: int | None = DEFAULT_MAX_BODY_SIZE,
    ) -> None:
        super().__init__(
            service=service,
            endpoints=lambda svc: {
                _CHECK_PATH: Endpoint.unary(method=_CHECK_METHOD, function=svc.check),
            },
            interceptors=interceptors,
            read_max_bytes=read_max_bytes,
            codecs=google_protobuf_codecs(),
        )

    @property
    def path(self) -> str:
        return f"/{HEALTH_SERVICE_FQN}"


class HealthWSGIApplication(ConnectWSGIApplication):
    def __init__(
        self,
        service: HealthImplSync,
        *,
        interceptors: Iterable[InterceptorSync] = (),
        read_max_bytes: int | None = DEFAULT_MAX_BODY_SIZE,
    ) -> None:
        # Unlike the ASGI base, the WSGI base takes the endpoint map directly
        # (no service/factory pair), which is also how generated WSGI stubs
        # call it.
        super().__init__(
            endpoints={
                _CHECK_PATH: EndpointSync.unary(method=_CHECK_METHOD, function=service.check),
            },
            interceptors=interceptors,
            read_max_bytes=read_max_bytes,
            codecs=google_protobuf_codecs(),
        )

    @property
    def path(self) -> str:
        return f"/{HEALTH_SERVICE_FQN}"


class HealthClient(ConnectClient):
    """Async health check client."""

    def __init__(self, address: str, **kwargs: object) -> None:
        kwargs.setdefault("codec", google_protobuf_binary_codec())
        super().__init__(address, **kwargs)

    async def check(
        self,
        request: health_pb2.HealthCheckRequest | None = None,
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> health_pb2.HealthCheckResponse:
        if request is None:
            request = health_pb2.HealthCheckRequest()
        return await self.execute_unary(
            request=request,
            method=_CHECK_METHOD,
            headers=headers,
            timeout_ms=timeout_ms,
        )


class HealthClientSync(ConnectClientSync):
    """Sync health check client."""

    def __init__(self, address: str, **kwargs: object) -> None:
        kwargs.setdefault("codec", google_protobuf_binary_codec())
        super().__init__(address, **kwargs)

    def check(
        self,
        request: health_pb2.HealthCheckRequest | None = None,
        *,
        headers: Headers | Mapping[str, str] | None = None,
        timeout_ms: int | None = None,
    ) -> health_pb2.HealthCheckResponse:
        if request is None:
            request = health_pb2.HealthCheckRequest()
        return self.execute_unary(
            request=request,
            method=_CHECK_METHOD,
            headers=headers,
            timeout_ms=timeout_ms,
        )
