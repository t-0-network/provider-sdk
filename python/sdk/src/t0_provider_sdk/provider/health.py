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
assembled here from `Endpoint` rather than generated. Only `Check` is mounted:
`Watch` is server-streaming, and the body-hash signature scheme these servers
run behind has no story for streams.
"""

from __future__ import annotations

from collections.abc import Iterable

from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.interceptor import Interceptor, InterceptorSync
from connectrpc.method import IdempotencyLevel, MethodInfo
from connectrpc.request import RequestContext
from connectrpc.server import ConnectASGIApplication, ConnectWSGIApplication, Endpoint, EndpointSync
from grpc_health.v1 import health_pb2

from t0_provider_sdk._version import __version__

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
    idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
)


class _Health:
    def __init__(self, services: Iterable[str]) -> None:
        self._registered = frozenset(services)

    def _check(
        self,
        request: health_pb2.HealthCheckRequest,
        ctx: RequestContext,
    ) -> health_pb2.HealthCheckResponse:
        ctx.response_headers()[SDK_ECOSYSTEM_HEADER] = _SDK_ECOSYSTEM
        ctx.response_headers()[SDK_VERSION_HEADER] = __version__

        # An empty service name asks about the process as a whole, which is up if
        # this handler is running at all.
        if request.service and request.service not in self._registered:
            raise ConnectError(Code.NOT_FOUND, f"unknown service '{request.service}'")
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
    def __init__(self, service: HealthImpl, *, interceptors: Iterable[Interceptor] = ()) -> None:
        super().__init__(
            service=service,
            endpoints=lambda svc: {
                _CHECK_PATH: Endpoint.unary(method=_CHECK_METHOD, function=svc.check),
            },
            interceptors=interceptors,
        )

    @property
    def path(self) -> str:
        return f"/{HEALTH_SERVICE_FQN}"


class HealthWSGIApplication(ConnectWSGIApplication):
    def __init__(self, service: HealthImplSync, *, interceptors: Iterable[InterceptorSync] = ()) -> None:
        super().__init__(
            service=service,
            endpoints=lambda svc: {
                _CHECK_PATH: EndpointSync.unary(method=_CHECK_METHOD, function=svc.check),
            },
            interceptors=interceptors,
        )

    @property
    def path(self) -> str:
        return f"/{HEALTH_SERVICE_FQN}"
