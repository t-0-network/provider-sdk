"""ConnectRPC interceptor that converts signature errors to proper ConnectError responses.

Reads the error stored by the ASGI middleware via contextvars and raises
ConnectError with the appropriate code before the RPC handler executes.

Go equivalent: provider/signature_error.go
"""

from __future__ import annotations

from typing import Any, Awaitable, Callable

from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext

from t0_provider_sdk.provider.errors import (
    SignatureFailedError,
    UnknownPublicKeyError,
)
from t0_provider_sdk.provider.middleware import signature_error_var


def _raise_if_signature_error() -> None:
    """Check contextvars for a signature error and raise ConnectError if present."""
    err = signature_error_var.get()
    if err is None:
        return

    if isinstance(err, (UnknownPublicKeyError, SignatureFailedError)):
        raise ConnectError(Code.UNAUTHENTICATED, str(err))

    # All other signature errors (missing header, invalid encoding, timestamp, body too large)
    raise ConnectError(Code.INVALID_ARGUMENT, str(err))


class SignatureErrorInterceptor:
    """Async ConnectRPC unary interceptor that converts signature verification errors.

    Implements the UnaryInterceptor protocol (connectrpc._interceptor_async.UnaryInterceptor).
    """

    async def intercept_unary(
        self,
        call_next: Callable[[Any, RequestContext], Awaitable[Any]],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        _raise_if_signature_error()
        return await call_next(request, ctx)


class SignatureErrorInterceptorSync:
    """Sync ConnectRPC unary interceptor that converts signature verification errors.

    Implements the UnaryInterceptorSync protocol (connectrpc._interceptor_sync.UnaryInterceptorSync).
    """

    def intercept_unary_sync(
        self,
        call_next: Callable[[Any, RequestContext], Any],
        request: Any,
        ctx: RequestContext,
    ) -> Any:
        _raise_if_signature_error()
        return call_next(request, ctx)
