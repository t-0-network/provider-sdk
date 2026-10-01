"""Generic handler registration for ConnectRPC ASGI/WSGI applications.

This module provides proto-agnostic handler registration. It works with
any generated ConnectRPC service application class.
"""

from __future__ import annotations

import logging
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field
from typing import Any, TypeVar

from t0_provider_sdk.provider.errors import NetworkPublicKeyRequiredError
from t0_provider_sdk.provider.health import (
    HEALTH_SERVICE_FQN,
    HealthASGIApplication,
    HealthImpl,
    HealthImplSync,
    HealthWSGIApplication,
)
from t0_provider_sdk.provider.interceptor import SignatureErrorInterceptor, SignatureErrorInterceptorSync
from t0_provider_sdk.provider.middleware import (
    DEFAULT_MAX_BODY_SIZE,
    ASGIApp,
    VerifySignatureFn,
    new_verify_signature,
    signature_verification_middleware,
)
from t0_provider_sdk.provider.middleware_wsgi import (
    WSGIApp,
    signature_verification_middleware_wsgi,
)
from t0_provider_sdk.provider.validate_response import ValidationInterceptor, ValidationInterceptorSync

T = TypeVar("T")

# Type for a function that creates an ASGI app from handler options
BuildHandler = Callable[["_HandlerOptions"], tuple[str, ASGIApp]]

# Type for a function that creates a WSGI app from handler options
BuildHandlerSync = Callable[["_HandlerOptions"], tuple[str, WSGIApp]]

# Type for handler option modifiers
HandlerOption = Callable[["_HandlerOptions"], None]


@dataclass
class _HandlerOptions:
    """Internal options passed to handler builders."""

    interceptors: list[Any] = field(default_factory=list)
    max_body_size: int = DEFAULT_MAX_BODY_SIZE


def handler(
    asgi_app_factory: type[Any],
    service_impl: Any,
    *options: HandlerOption,
) -> BuildHandler:
    """Register a service handler with optional configuration.

    Args:
        asgi_app_factory: Generated ConnectRPC ASGI application class
            (e.g. ProviderServiceASGIApplication).
        service_impl: User's implementation of the service protocol.
        *options: Optional handler configuration functions.

    Returns:
        A BuildHandler that creates (path, asgi_app) when called with options.
    """

    def build(default_options: _HandlerOptions) -> tuple[str, ASGIApp]:
        opts = _HandlerOptions(
            interceptors=list(default_options.interceptors),
            max_body_size=default_options.max_body_size,
        )
        for opt in options:
            opt(opts)

        app = asgi_app_factory(
            service_impl, interceptors=_signature_check_first(opts.interceptors, SignatureErrorInterceptor)
        )
        return app.path, app

    return build


def _signature_check_first(interceptors: list[Any], signature_interceptor: type[Any]) -> list[Any]:
    """The interceptors an app is built with: the signature check first, whatever the options did.

    An option can change or empty the list it is given; the check is added here instead, ahead of
    every other interceptor, so none of them runs on an unverified request.
    """
    return [signature_interceptor(), *(i for i in interceptors if not isinstance(i, signature_interceptor))]


def _network_verify_fn(network_public_key: str) -> VerifySignatureFn:
    """Parse the network public key at startup, so a missing or mistyped key fails
    here rather than on every request."""
    key = (network_public_key or "").strip()
    if not key:
        raise NetworkPublicKeyRequiredError()
    try:
        return new_verify_signature(key)
    except ValueError as e:
        raise ValueError(f"invalid network public key: {e}") from e


def new_asgi_app(
    network_public_key: str,
    *build_handlers: BuildHandler,
    logger: logging.Logger | None = None,
    version: str | None = None,
) -> ASGIApp:
    """Create a composite ASGI app with signature verification.

    Args:
        network_public_key: Hex-encoded T-0 Network public key for signature verification.
            Required; surrounding whitespace is stripped.
        *build_handlers: Handler builders created via handler().
        logger: Optional logger used by the response-validation interceptor when
            it catches an invalid response. Defaults to
            ``logging.getLogger("t0_provider_sdk")``.
        version: Optional SDK version override. Wrapping SDKs pass their own
            version so health probes report it instead of provider-sdk's
            built-in version. Defaults to ``None`` (use built-in version).

    Returns:
        An ASGI application with signature verification middleware.

    Raises:
        NetworkPublicKeyRequiredError: The key is empty or whitespace only.
        ValueError: The key is malformed ("invalid network public key: ...").
    """
    verify_fn = _network_verify_fn(network_public_key)
    default_options = _HandlerOptions(
        interceptors=[SignatureErrorInterceptor(), ValidationInterceptor(logger=logger)],
    )

    # Build all service handlers
    routes: dict[str, ASGIApp] = {}
    for build in build_handlers:
        path, app = build(default_options)
        routes[path] = app

    # Health is the only service this transport mounts on its own — see
    # docs/HEALTH_SERVICE.md. Same interceptors and the same outer signature
    # middleware, so the probe is signed like any other call.
    service_names = [path.lstrip("/") for path in routes]
    service_names.append(HEALTH_SERVICE_FQN)
    health_app = HealthASGIApplication(
        HealthImpl(service_names, version=version),
        interceptors=list(default_options.interceptors),
    )
    routes[health_app.path] = health_app

    # Create router ASGI app
    router = _create_router(routes)

    return signature_verification_middleware(router, verify_fn, default_options.max_body_size)


def handler_sync(
    wsgi_app_factory: type[Any],
    service_impl: Any,
    *options: HandlerOption,
) -> BuildHandlerSync:
    """Register a sync service handler with optional configuration.

    Parallel to handler() but for WSGI app factories.

    Args:
        wsgi_app_factory: Generated ConnectRPC WSGI application class
            (e.g. ProviderServiceWSGIApplication).
        service_impl: User's implementation of the sync service protocol.
        *options: Optional handler configuration functions.

    Returns:
        A BuildHandlerSync that creates (path, wsgi_app) when called with options.
    """

    def build(default_options: _HandlerOptions) -> tuple[str, WSGIApp]:
        opts = _HandlerOptions(
            interceptors=list(default_options.interceptors),
            max_body_size=default_options.max_body_size,
        )
        for opt in options:
            opt(opts)

        app = wsgi_app_factory(
            service_impl, interceptors=_signature_check_first(opts.interceptors, SignatureErrorInterceptorSync)
        )
        return app.path, app

    return build


def new_wsgi_app(
    network_public_key: str,
    *build_handlers: BuildHandlerSync,
    logger: logging.Logger | None = None,
    version: str | None = None,
) -> WSGIApp:
    """Create a composite WSGI app with signature verification.

    Parallel to new_asgi_app() but for synchronous WSGI servers (e.g. gunicorn).

    Args:
        network_public_key: Hex-encoded T-0 Network public key for signature verification.
            Required; surrounding whitespace is stripped.
        *build_handlers: Handler builders created via handler_sync().
        logger: Optional logger used by the response-validation interceptor when
            it catches an invalid response. Defaults to
            ``logging.getLogger("t0_provider_sdk")``.
        version: Optional SDK version override. Wrapping SDKs pass their own
            version so health probes report it instead of provider-sdk's
            built-in version. Defaults to ``None`` (use built-in version).

    Returns:
        A WSGI application with signature verification middleware.

    Raises:
        NetworkPublicKeyRequiredError: The key is empty or whitespace only.
        ValueError: The key is malformed ("invalid network public key: ...").
    """
    verify_fn = _network_verify_fn(network_public_key)
    default_options = _HandlerOptions(
        interceptors=[SignatureErrorInterceptorSync(), ValidationInterceptorSync(logger=logger)],
    )

    # Build all service handlers
    routes: dict[str, WSGIApp] = {}
    for build in build_handlers:
        path, app = build(default_options)
        routes[path] = app

    # Health is the only service this transport mounts on its own — see
    # docs/HEALTH_SERVICE.md. Same interceptors and the same outer signature
    # middleware, so the probe is signed like any other call.
    service_names = [path.lstrip("/") for path in routes]
    service_names.append(HEALTH_SERVICE_FQN)
    health_app = HealthWSGIApplication(
        HealthImplSync(service_names, version=version),
        interceptors=list(default_options.interceptors),
    )
    routes[health_app.path] = health_app

    # Create router WSGI app
    router = _create_wsgi_router(routes)

    return signature_verification_middleware_wsgi(router, verify_fn, default_options.max_body_size)


def _create_router(routes: dict[str, ASGIApp]) -> ASGIApp:
    """Create a simple path-prefix ASGI router.

    ConnectRPC requests have paths like:
    /tzero.v1.payment.ProviderService/PayOut

    The routes dict maps service path prefixes to their ASGI apps:
    {"/tzero.v1.payment.ProviderService": <app>}
    """

    async def router(scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            # Non-HTTP scopes (e.g. lifespan) - try first handler
            if routes:
                first_app = next(iter(routes.values()))
                await first_app(scope, receive, send)
            return

        path = scope.get("path", "")
        for prefix, app in routes.items():
            if path.startswith(prefix):
                await app(scope, receive, send)
                return

        # No matching route - return 404
        await send({"type": "http.response.start", "status": 404, "headers": []})
        await send({"type": "http.response.body", "body": b"Not Found"})

    return router


def _create_wsgi_router(routes: dict[str, WSGIApp]) -> WSGIApp:
    """Create a simple path-prefix WSGI router.

    Parallel to _create_router() but for WSGI apps.
    """

    def router(environ: dict[str, Any], start_response: Any) -> Iterable[bytes]:
        path = environ.get("PATH_INFO", "")
        for prefix, app in routes.items():
            if path.startswith(prefix):
                return app(environ, start_response)

        # No matching route - return 404
        start_response("404 Not Found", [("Content-Type", "text/plain")])
        return [b"Not Found"]

    return router
