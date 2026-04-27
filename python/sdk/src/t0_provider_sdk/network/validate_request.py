"""ConnectRPC interceptor that validates outgoing requests against buf.validate rules.

Invalid requests are rejected with Code.INVALID_ARGUMENT before they are sent.

Go equivalent: network client uses connectrpc.com/validate interceptor.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext


class RequestValidationInterceptor:
    """Async ConnectRPC unary interceptor that validates outgoing requests."""

    def __init__(self) -> None:
        self._validator = protovalidate.Validator()

    async def intercept_unary(
        self,
        call_next: Callable[[Any, RequestContext], Awaitable[Any]],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        try:
            self._validator.validate(request)
        except protovalidate.ValidationError as e:
            raise ConnectError(Code.INVALID_ARGUMENT, f"request validation failed: {e}") from e
        return await call_next(request, ctx)


class RequestValidationInterceptorSync:
    """Sync ConnectRPC unary interceptor that validates outgoing requests."""

    def __init__(self) -> None:
        self._validator = protovalidate.Validator()

    def intercept_unary_sync(
        self,
        call_next: Callable[[Any, RequestContext], Any],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        try:
            self._validator.validate(request)
        except protovalidate.ValidationError as e:
            raise ConnectError(Code.INVALID_ARGUMENT, f"request validation failed: {e}") from e
        return call_next(request, ctx)
