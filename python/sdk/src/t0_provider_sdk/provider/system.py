"""Auto-registered SystemService implementation for ASGI and WSGI servers.

Returns the list of registered RPC services on this provider, the server's
current wall-clock time, the SDK version, and the SDK ecosystem identifier.
"""

from __future__ import annotations

from datetime import datetime, timezone

from connectrpc.request import RequestContext
from google.protobuf.timestamp_pb2 import Timestamp

from t0_provider_sdk._version import __version__
from tzero.v1.system import system_pb2


class SystemServiceImpl:
    """Async (ASGI) SystemService implementation."""

    def __init__(self, services: list[str]) -> None:
        self._services = services

    async def health(
        self,
        _request: system_pb2.HealthRequest,
        _ctx: RequestContext,
    ) -> system_pb2.HealthResponse:
        return _build_response(self._services)


class SystemServiceImplSync:
    """Sync (WSGI) SystemService implementation."""

    def __init__(self, services: list[str]) -> None:
        self._services = services

    def health(
        self,
        _request: system_pb2.HealthRequest,
        _ctx: RequestContext,
    ) -> system_pb2.HealthResponse:
        return _build_response(self._services)


def _build_response(services: list[str]) -> system_pb2.HealthResponse:
    now = Timestamp()
    now.FromDatetime(datetime.now(tz=timezone.utc))
    return system_pb2.HealthResponse(
        services=services,
        current_time=now,
        sdk_version=__version__,
        sdk_ecosystem=system_pb2.SDK_ECOSYSTEM_PYTHON,
    )
