"""ConnectRPC interceptor that converts signature errors to proper ConnectError responses.

Reads the error stored by the ASGI or WSGI middleware via contextvars and raises ConnectError with
the appropriate code. It is a metadata interceptor, and handler() puts it first, so connectrpc runs
it before it reads the request body: a rejected call is answered before anything the caller sent
is decoded, and no handler runs, unary or streaming.
"""

from __future__ import annotations

from connectrpc.code import Code
from connectrpc.errors import ConnectError
from connectrpc.request import RequestContext

from t0_provider_sdk._messages import NO_SIGNATURE_RESULT
from t0_provider_sdk.provider.errors import (
    BodyTooLargeError,
    SignatureFailedError,
    UnknownPublicKeyError,
)
from t0_provider_sdk.provider.middleware import NOT_VERIFIED, signature_error_var


def _raise_if_signature_error() -> None:
    """Check contextvars for a signature error and raise ConnectError if present."""
    err = signature_error_var.get()
    if err is None:
        return

    if err is NOT_VERIFIED:
        # The signature middleware did not run for this request: refuse it rather than serve it unverified.
        raise ConnectError(Code.INTERNAL, NO_SIGNATURE_RESULT)

    if isinstance(err, (UnknownPublicKeyError, SignatureFailedError)):
        raise ConnectError(Code.UNAUTHENTICATED, str(err))

    if isinstance(err, BodyTooLargeError):
        raise ConnectError(Code.RESOURCE_EXHAUSTED, str(err))

    # All other signature errors (missing header, invalid encoding, timestamp)
    raise ConnectError(Code.INVALID_ARGUMENT, str(err))


class SignatureErrorInterceptor:
    """Async ConnectRPC metadata interceptor that converts signature verification errors.

    Implements the MetadataInterceptor protocol (connectrpc._interceptor_async.MetadataInterceptor).
    At the front of the interceptor list, its on_start runs before the request body is read.
    """

    async def on_start(self, ctx: RequestContext) -> None:
        _raise_if_signature_error()

    async def on_end(self, token: None, ctx: RequestContext, error: Exception | None, /) -> None:
        return


class SignatureErrorInterceptorSync:
    """Sync ConnectRPC metadata interceptor that converts signature verification errors.

    Implements the MetadataInterceptorSync protocol (connectrpc._interceptor_sync.MetadataInterceptorSync).
    At the front of the interceptor list, its on_start_sync runs before the request body is read.
    """

    def on_start_sync(self, ctx: RequestContext) -> None:
        _raise_if_signature_error()

    def on_end_sync(self, token: None, ctx: RequestContext, error: Exception | None, /) -> None:
        return
