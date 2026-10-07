"""WSGI middleware for T-0 Network signature verification.

This middleware intercepts the raw request body BEFORE ConnectRPC deserializes it,
verifies the cryptographic signature, and stores any errors in contextvars for the
ConnectRPC interceptor to convert into proper error responses.

Parallel to middleware.py (ASGI). Reuses _verify_request() and related helpers.

Architecture:
    WSGI Request -> SignatureVerificationMiddleware -> ConnectRPC WSGI App
                          |                                |
                   Read raw body                    SignatureErrorInterceptorSync
                   Verify signature                 (reads error from contextvars)
                   Store error in contextvars
                   Replay body to downstream
"""

from __future__ import annotations

import io
from collections.abc import Callable, Iterable
from typing import Any

from t0_provider_sdk.provider.errors import BodyTooLargeError, SignatureVerificationError
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    CustomVerifyFn,
    VerifySignatureFn,
    _body_limit,
    _check_headers,
    _declared_length,
    _rejection_body,
    _verify_body,
    signature_error_var,
)

WSGIEnviron = dict[str, Any]
StartResponse = Callable[..., Any]
WSGIApp = Callable[[WSGIEnviron, StartResponse], Iterable[bytes]]


def signature_verification_middleware_wsgi(
    app: WSGIApp,
    verify_fn: VerifySignatureFn | CustomVerifyFn,
    max_body_size: int = DEFAULT_MAX_BODY_SIZE,
) -> WSGIApp:
    """Wrap a WSGI app with signature verification middleware.

    The same rules as signature_verification_middleware (ASGI): the signature headers are checked,
    then the body is read (at most max_body_size bytes) and the signature verified over it, before
    the app reads anything. A rejected request goes on with an empty request message in place of
    its body, and the interceptor answers it with the rejection's code and message. verify_fn is
    taken as by signature_verification_middleware.
    """
    max_body_size = _body_limit(max_body_size)

    def middleware(environ: WSGIEnviron, start_response: StartResponse) -> Iterable[bytes]:
        headers = _parse_wsgi_headers(environ)

        error: SignatureVerificationError | None = None
        try:
            signed = _check_headers(verify_fn, headers)
            body = _read_wsgi_body(environ, headers, max_body_size)
            _verify_body(verify_fn, headers, body, signed)
        except SignatureVerificationError as e:
            error = e
            body = _rejection_body(headers)
            # The replayed body is neither compressed nor of the sent length.
            for key in ("HTTP_CONTENT_ENCODING", "HTTP_CONNECT_CONTENT_ENCODING", "HTTP_GRPC_ENCODING"):
                environ.pop(key, None)

        # Replay body to downstream. A unary call has passed the interceptor by the time the app
        # returns, so the result is reset then: the thread serves other requests in this context,
        # and the response iterable, which may run later, never needs it.
        environ["wsgi.input"] = io.BytesIO(body)
        environ["CONTENT_LENGTH"] = str(len(body))
        token = signature_error_var.set(error)
        try:
            return app(environ, start_response)
        finally:
            signature_error_var.reset(token)

    return middleware


def _parse_wsgi_headers(environ: WSGIEnviron) -> dict[str, str]:
    """Extract HTTP headers from WSGI environ into a lowercase-hyphenated dict.

    WSGI stores headers as HTTP_X_PUBLIC_KEY -> x-public-key.
    Content-Type and Content-Length are special (no HTTP_ prefix).
    """
    result: dict[str, str] = {}
    for key, value in environ.items():
        if key.startswith("HTTP_"):
            # HTTP_X_PUBLIC_KEY -> x-public-key
            header_name = key[5:].replace("_", "-").lower()
            result[header_name] = value
    # Also include Content-Type and Content-Length if present
    if "CONTENT_TYPE" in environ:
        result["content-type"] = environ["CONTENT_TYPE"]
    if "CONTENT_LENGTH" in environ:
        result["content-length"] = environ["CONTENT_LENGTH"]
    return result


def _read_wsgi_body(environ: WSGIEnviron, headers: dict[str, str], max_size: int) -> bytes:
    """Read the whole body, at most max_size bytes: a declared Content-Length over the limit is
    refused before anything is read."""
    length = _declared_length(headers)
    if length is not None:
        if length > max_size:
            raise BodyTooLargeError(max_size)
        body = environ["wsgi.input"].read(length)
    elif environ.get("wsgi.input_terminated"):
        # A body without a length (chunked): the server ends the input where the body ends. One
        # byte over the limit is enough to refuse it.
        body = environ["wsgi.input"].read(max_size + 1)
    else:
        # No length and no end marked by the server: the request has no body (PEP 3333), and
        # reading would wait on the open connection.
        body = b""

    if len(body) > max_size:
        raise BodyTooLargeError(max_size)

    return body
