"""ConnectRPC interceptor that validates provider responses against buf.validate rules.

Invalid responses are rejected with Code.INTERNAL since they indicate
a provider implementation bug, not a client error.

The interceptor also emits one ``error``-level log line before re-raising so
providers see the failure in their own logs even when they don't wrap their
responses with :func:`t0_provider_sdk.provider.validate.validate`. The logger
is overridable via the constructor; the default is
``logging.getLogger("t0_provider_sdk")``.

Go equivalent: provider/validate_response.go
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext

from t0_provider_sdk._version import __version__
from t0_provider_sdk.provider.validate import _get_validator

DEFAULT_LOGGER_NAME = "t0_provider_sdk"


def _rpc_method_from_ctx(ctx: Any) -> str:
    """Best-effort extraction of the RPC method FQN from a RequestContext."""
    for attr in ("method", "procedure", "path"):
        value = getattr(ctx, attr, None)
        if isinstance(value, str) and value:
            return value
    return ""


def _log_validation_failure(
    logger: logging.Logger,
    response: Any,
    ctx: Any,
    error: protovalidate.ValidationError,
) -> None:
    """Emit one error-level line with structured fields for a validation failure."""
    response_type = (
        type(response).DESCRIPTOR.full_name if hasattr(type(response), "DESCRIPTOR") else type(response).__name__
    )
    logger.error(
        "response validation failed: %s",
        error,
        extra={
            "rpc_method": _rpc_method_from_ctx(ctx),
            "response_type": response_type,
            "violations": str(error),
            "sdk_version": __version__,
        },
    )


class ValidationInterceptor:
    """Async ConnectRPC unary interceptor that validates responses against proto rules."""

    def __init__(self, logger: logging.Logger | None = None) -> None:
        self._validator = _get_validator()
        self._logger = logger if logger is not None else logging.getLogger(DEFAULT_LOGGER_NAME)

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
            _log_validation_failure(self._logger, response, ctx, e)
            raise ConnectError(Code.INTERNAL, f"response validation failed: {e}") from e
        return response


class ValidationInterceptorSync:
    """Sync ConnectRPC unary interceptor that validates responses against proto rules."""

    def __init__(self, logger: logging.Logger | None = None) -> None:
        self._validator = _get_validator()
        self._logger = logger if logger is not None else logging.getLogger(DEFAULT_LOGGER_NAME)

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
            _log_validation_failure(self._logger, response, ctx, e)
            raise ConnectError(Code.INTERNAL, f"response validation failed: {e}") from e
        return response
