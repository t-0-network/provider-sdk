"""ConnectRPC interceptor that validates provider responses against buf.validate rules.

Invalid responses are rejected with Code.INTERNAL since they indicate
a provider implementation bug, not a client error.

Go equivalent: provider/validate_response.go
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext


class ValidationInterceptor:
    """Async ConnectRPC unary interceptor that validates responses against proto rules."""

    def __init__(self) -> None:
        self._validator = protovalidate.Validator()

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
            raise ConnectError(Code.INTERNAL, f"response validation failed: {e}") from e
        return response


class ValidationInterceptorSync:
    """Sync ConnectRPC unary interceptor that validates responses against proto rules."""

    def __init__(self) -> None:
        self._validator = protovalidate.Validator()

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
            raise ConnectError(Code.INTERNAL, f"response validation failed: {e}") from e
        return response


# Backwards-compatible aliases
ResponseValidationInterceptor = ValidationInterceptor
ResponseValidationInterceptorSync = ValidationInterceptorSync
