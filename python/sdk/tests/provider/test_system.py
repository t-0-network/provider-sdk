"""Unit tests for the auto-registered SystemService implementation."""

from __future__ import annotations

import time

import pytest
from connectrpc.method import IdempotencyLevel, MethodInfo
from connectrpc.request import Headers, RequestContext
from t0_provider_sdk._version import __version__
from t0_provider_sdk.provider.system import SystemServiceImpl, SystemServiceImplSync
from tzero.v1.system import system_pb2

PROVIDER_SERVICE_FQN = "tzero.v1.payment.ProviderService"
SYSTEM_SERVICE_FQN = "tzero.v1.system.SystemService"


def _ctx() -> RequestContext:
    return RequestContext(
        method=MethodInfo(
            name="Health",
            service_name=SYSTEM_SERVICE_FQN,
            input=system_pb2.HealthRequest,
            output=system_pb2.HealthResponse,
            idempotency_level=IdempotencyLevel.NO_SIDE_EFFECTS,
        ),
        http_method="POST",
        request_headers=Headers(),
    )


@pytest.mark.asyncio
async def test_async_impl_response_shape():
    services = [PROVIDER_SERVICE_FQN, SYSTEM_SERVICE_FQN]
    impl = SystemServiceImpl(services)

    resp = await impl.health(system_pb2.HealthRequest(), _ctx())

    assert list(resp.services) == services
    assert resp.sdk_version == __version__
    assert resp.sdk_ecosystem == system_pb2.SDK_ECOSYSTEM_PYTHON
    assert resp.current_time is not None

    skew_seconds = abs(time.time() - resp.current_time.seconds)
    assert skew_seconds < 5, f"current_time skew {skew_seconds}s"


def test_sync_impl_response_shape():
    services = [SYSTEM_SERVICE_FQN]
    impl = SystemServiceImplSync(services)

    resp = impl.health(system_pb2.HealthRequest(), _ctx())

    assert list(resp.services) == services
    assert resp.sdk_version == __version__
    assert resp.sdk_ecosystem == system_pb2.SDK_ECOSYSTEM_PYTHON
    assert resp.current_time is not None
