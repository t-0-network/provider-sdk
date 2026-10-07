"""ConnectRPC interceptor that validates provider responses against buf.validate rules.

Invalid responses are rejected with Code.INTERNAL since they indicate
a provider implementation bug, not a client error.

The interceptor also emits one ``error``-level log line before re-raising so
providers see the failure in their own logs even when they don't wrap their
responses with :func:`t0_provider_sdk.provider.validate.validate`. The logger
is overridable via the constructor; the default is
``logging.getLogger("t0_provider_sdk")``.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext

from t0_provider_sdk._messages import RESPONSE_INVALID
from t0_provider_sdk.provider._sdk_version import _reported_version
from t0_provider_sdk.provider.validate import _get_validator, _violations

DEFAULT_LOGGER_NAME = "t0_provider_sdk"


def _rpc_method_from_ctx(ctx: Any) -> str:
    """The RPC method as ``<service>/<method>`` (e.g. tzero.v1.payment.ProviderService/PayOut), from
    the MethodInfo of a RequestContext."""
    method = getattr(ctx, "method", None)
    if isinstance(method, str):
        return method
    service_name, name = getattr(method, "service_name", ""), getattr(method, "name", "")
    return f"{service_name}/{name}" if service_name and name else ""


def _log_validation_failure(
    logger: logging.Logger,
    response: Any,
    ctx: Any,
    violations: str,
    sdk_version: str,
) -> None:
    """Emit one error-level line with structured fields for a validation failure."""
    response_type = (
        type(response).DESCRIPTOR.full_name if hasattr(type(response), "DESCRIPTOR") else type(response).__name__
    )
    logger.error(
        "response validation failed: %s",
        violations,
        extra={
            "rpc_method": _rpc_method_from_ctx(ctx),
            "response_type": response_type,
            "violations": violations,
            "sdk_version": sdk_version,
        },
    )


class ValidationInterceptor:
    """Async ConnectRPC unary interceptor that validates responses against proto rules.

    version is the SDK version its log line reports (``sdk_version``): new_asgi_app passes its own
    ``version`` override; None or a blank value means this SDK's version.
    """

    def __init__(self, logger: logging.Logger | None = None, *, version: str | None = None) -> None:
        self._validator = _get_validator()
        self._logger = logger if logger is not None else logging.getLogger(DEFAULT_LOGGER_NAME)
        self._version = _reported_version(version)

    async def intercept_unary(
        self,
        call_next: Callable[[Any, RequestContext], Awaitable[Any]],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        response = await call_next(request, ctx)
        try:
            self._validator.validate(response)
        except protovalidate.ValidationError as e:
            violations = _violations(e)
            _log_validation_failure(self._logger, response, ctx, violations, self._version)
            raise ConnectError(Code.INTERNAL, RESPONSE_INVALID.format(violations=violations)) from e
        return response


class ValidationInterceptorSync:
    """Sync ConnectRPC unary interceptor that validates responses against proto rules.

    version: as for ValidationInterceptor (new_wsgi_app passes its own ``version`` override).
    """

    def __init__(self, logger: logging.Logger | None = None, *, version: str | None = None) -> None:
        self._validator = _get_validator()
        self._logger = logger if logger is not None else logging.getLogger(DEFAULT_LOGGER_NAME)
        self._version = _reported_version(version)

    def intercept_unary_sync(
        self,
        call_next: Callable[[Any, RequestContext], Any],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        response = call_next(request, ctx)
        try:
            self._validator.validate(response)
        except protovalidate.ValidationError as e:
            violations = _violations(e)
            _log_validation_failure(self._logger, response, ctx, violations, self._version)
            raise ConnectError(Code.INTERNAL, RESPONSE_INVALID.format(violations=violations)) from e
        return response


# Backwards-compatible aliases
ResponseValidationInterceptor = ValidationInterceptor
ResponseValidationInterceptorSync = ValidationInterceptorSync
