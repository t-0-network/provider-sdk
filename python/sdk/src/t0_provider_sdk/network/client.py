"""Generic client factory for creating ConnectRPC clients with signing transport.

Go equivalent: network/client.go → NewServiceClient[T]

Proto-agnostic: works with ANY generated ConnectRPC client class.
"""

from __future__ import annotations

import functools
from typing import TYPE_CHECKING, Any, TypeVar

from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.options import DEFAULT_BASE_URL, DEFAULT_TIMEOUT
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient

if TYPE_CHECKING:
    from collections.abc import Callable

T = TypeVar("T")

# The ConnectClient / ConnectClientSync entry points of each call type, and whether the call
# streams. gRPC unary calls go out through stream() but still enter through execute_unary.
_EXECUTE_METHODS = (
    ("execute_unary", False),
    ("execute_client_stream", True),
    ("execute_server_stream", True),
    ("execute_bidi_stream", True),
)


def new_service_client(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
    stream_timeout: float | None = None,
) -> T:
    """Create an async ConnectRPC client with signing transport.

    Unary calls are signed over the whole request body. Client- and server-streaming calls are
    signed over their first request message only, and the request goes out as soon as that
    message is available: send a message (or close the stream) before waiting for a response.

    Args:
        private_key: Hex-encoded secp256k1 private key (with or without 0x prefix).
        client_class: Generated ConnectRPC async client class (e.g. NetworkServiceClient).
        base_url: Base URL of the T-0 Network API.
        timeout: Default timeout of unary calls in seconds. Must be greater than zero.
        stream_timeout: Default timeout of client- and server-streaming calls in seconds, from
            waiting for the first request message to reading the end of the response. None (or
            0) means no timeout: a stream can run as long as an upload or a download takes.

    A call's own ``timeout_ms`` overrides either default.

    Returns:
        An instance of client_class configured with signing transport.
    """
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    sign_fn = new_signer_from_hex(private_key)
    signing_client = SigningClient(sign_fn)
    client = client_class(base_url, http_client=signing_client, timeout_ms=None)  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    return client


def new_service_client_sync(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
    stream_timeout: float | None = None,
) -> T:
    """Create a sync ConnectRPC client with signing transport.

    Signing and timeouts work as in new_service_client.

    Args:
        private_key: Hex-encoded secp256k1 private key (with or without 0x prefix).
        client_class: Generated ConnectRPC sync client class (e.g. NetworkServiceClientSync).
        base_url: Base URL of the T-0 Network API.
        timeout: Default timeout of unary calls in seconds. Must be greater than zero.
        stream_timeout: Default timeout of client- and server-streaming calls in seconds. None
            (or 0) means no timeout.

    Returns:
        An instance of client_class configured with signing transport.
    """
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    sign_fn = new_signer_from_hex(private_key)
    signing_client = SigningSyncClient(sign_fn)
    client = client_class(base_url, http_client=signing_client, timeout_ms=None)  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    return client


def _default_timeouts_ms(timeout: float, stream_timeout: float | None) -> tuple[int, int | None]:
    if timeout <= 0:
        raise ValueError("timeout must be greater than zero")
    if stream_timeout is not None and stream_timeout < 0:
        raise ValueError("stream_timeout must not be negative")
    # At least 1 ms: connectrpc reads a timeout_ms of 0 as "no timeout".
    unary_ms = max(1, round(timeout * 1000))
    stream_ms = max(1, round(stream_timeout * 1000)) if stream_timeout else None
    return unary_ms, stream_ms


def _set_default_timeouts(client: object, unary_ms: int, stream_ms: int | None) -> None:
    """Gives each call type its own default timeout.

    A ConnectRPC client has a single timeout_ms for every call, so the client is built without
    one and each of its execute methods fills in the default of its call type instead.
    """
    for name, streaming in _EXECUTE_METHODS:
        execute = getattr(client, name, None)
        if execute is not None:
            setattr(client, name, _with_default_timeout(execute, stream_ms if streaming else unary_ms))


def _with_default_timeout(execute: Callable[..., Any], default_ms: int | None) -> Callable[..., Any]:
    @functools.wraps(execute)
    def execute_with_default_timeout(*args: Any, timeout_ms: int | None = None, **kwargs: Any) -> Any:
        # Like connectrpc, which falls back to the client's timeout when timeout_ms is falsy.
        return execute(*args, timeout_ms=timeout_ms or default_ms, **kwargs)

    return execute_with_default_timeout
