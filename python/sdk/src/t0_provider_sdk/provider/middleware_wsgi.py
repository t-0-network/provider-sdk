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
from typing import Any, Callable, Iterable

from t0_provider_sdk.provider.errors import BodyTooLargeError
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    VerifySignatureFn,
    _verify_request,
    signature_error_var,
)

WSGIEnviron = dict[str, Any]
StartResponse = Callable[..., Any]
WSGIApp = Callable[[WSGIEnviron, StartResponse], Iterable[bytes]]


def signature_verification_middleware_wsgi(
    app: WSGIApp,
    verify_fn: VerifySignatureFn,
    max_body_size: int = DEFAULT_MAX_BODY_SIZE,
) -> WSGIApp:
    """Wrap a WSGI app with signature verification middleware.

    The middleware:
    1. Reads the entire request body from wsgi.input
    2. Parses signature headers from WSGI environ HTTP_* keys
    3. Validates timestamp within +/-60 seconds
    4. Verifies the signature against the network public key
    5. Stores any error in contextvars.ContextVar for the interceptor
    6. Replaces wsgi.input with a BytesIO to replay body downstream
    """

    def middleware(environ: WSGIEnviron, start_response: StartResponse) -> Iterable[bytes]:
        headers = _parse_wsgi_headers(environ)

        # Read the full body
        try:
            body = _read_wsgi_body(environ, max_body_size)
        except BodyTooLargeError as e:
            signature_error_var.set(e)
            environ["wsgi.input"] = io.BytesIO(b"")
            environ["CONTENT_LENGTH"] = "0"
            return app(environ, start_response)

        # Parse and verify
        error = _verify_request(verify_fn, headers, body)
        signature_error_var.set(error)

        # Replay body to downstream
        environ["wsgi.input"] = io.BytesIO(body)
        environ["CONTENT_LENGTH"] = str(len(body))
        return app(environ, start_response)

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


def _read_wsgi_body(environ: WSGIEnviron, max_size: int) -> bytes:
    """Read the full request body from WSGI environ, enforcing size limit."""
    content_length = environ.get("CONTENT_LENGTH", "")
    if content_length:
        length = int(content_length)
        if length > max_size:
            raise BodyTooLargeError(max_size)
        body = environ["wsgi.input"].read(length)
    else:
        body = environ["wsgi.input"].read()

    if len(body) > max_size:
        raise BodyTooLargeError(max_size)

    return body
