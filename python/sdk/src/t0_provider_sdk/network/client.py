"""Generic client factory for creating ConnectRPC clients with signing transport.

Proto-agnostic: works with ANY generated ConnectRPC client class.
"""

from __future__ import annotations

import asyncio
import functools
import inspect
import ipaddress
import logging
import math
import re
import threading
import types
from typing import TYPE_CHECKING, Any, NoReturn, TypeVar
from urllib.parse import urlsplit

from connectrpc.code import Code
from connectrpc.compat import google_protobuf_json_codec
from connectrpc.errors import ConnectError
from connectrpc.protocol import ProtocolType

from t0_provider_sdk.crypto.signer import SignFn, new_signer_from_hex
from t0_provider_sdk.network.options import (
    DEFAULT_BASE_URL,
    DEFAULT_STREAM_TIMEOUT,
    DEFAULT_TIMEOUT,
    Protocol,
    WireFormat,
)
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient, _aclose, _close, _shared_transport

if TYPE_CHECKING:
    from collections.abc import Callable

T = TypeVar("T")

_LOGGER = logging.getLogger("t0_provider_sdk")

# Background closes of callers' request sources, kept here so they are not collected while pending.
_CLOSING: set[asyncio.Task[None]] = set()

# A host name: dot-separated labels of ASCII letters, digits and inner '-', none empty, the last one
# starting with a letter (so "1.2.3" is not taken for a name). Other names ("my_host", "a..b",
# "-foo") are refused: not every client this network talks to can connect to them.
_HOST_LABEL = r"[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
_HOST_NAME = re.compile(rf"(?:{_HOST_LABEL}\.)*[A-Za-z](?:[A-Za-z0-9-]*[A-Za-z0-9])?")

# The largest timeout every SDK accepts: 2^31 - 1 ms, about 24.8 days.
MAX_TIMEOUT_MS = 2**31 - 1

# (execute method, streams?). gRPC unary goes out through stream() but enters through execute_unary.
_EXECUTE_METHODS = (
    ("execute_unary", False),
    ("execute_client_stream", True),
    ("execute_server_stream", True),
)


def new_service_client(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str | None = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
    stream_timeout: float = DEFAULT_STREAM_TIMEOUT,
    wire_format: WireFormat = WireFormat.BINARY,
    protocol: Protocol = Protocol.CONNECT,
    sign_fn: SignFn | None = None,
) -> T:
    """Create an async ConnectRPC client with signing transport.

    Streaming calls are signed over their first request message and sent as soon as it is
    available: send one (or close the stream) before waiting for a response. Bidirectional calls
    raise ConnectError UNIMPLEMENTED. See docs/STREAMING.md.

    Args:
        private_key: Hex-encoded secp256k1 private key: 64 hex digits, optionally after 0x or 0X.
            Ignored when sign_fn is given.
        client_class: Generated ConnectRPC async client class (e.g. NetworkServiceClient).
        base_url: Base URL of the T-0 Network API: http:// or https://, a host (an IP literal, or
            dot-separated labels of ASCII letters, digits and inner '-', the last one starting
            with a letter) without user info, a port of 1..65535 if one is given, and no path,
            query or fragment (a single trailing "/" is allowed). None means the default; an
            empty string raises ValueError.
        timeout: Timeout of unary calls in seconds, 15 by default.
        stream_timeout: Timeout of client- and server-streaming calls in seconds, including the
            wait for the first request message, 300 by default.
        wire_format: WireFormat.BINARY (default) or WireFormat.JSON.
        protocol: Protocol.CONNECT (default) or Protocol.GRPC. gRPC on an http:// base URL uses
            HTTP/2 without TLS.
        sign_fn: Signs each request in place of private_key, e.g. with a key held elsewhere.

    Each timeout must be positive and at most 2147483647 ms; there is no way to turn one off. A
    call's own ``timeout_ms`` (same bounds) replaces the default, whether shorter or longer.

    Returns:
        An instance of client_class configured with signing transport.
    """
    base_url = _checked_base_url(base_url)
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    _check_enums(wire_format, protocol)
    transport = _transport(base_url, protocol, sync=False)
    signing_client = SigningClient(sign_fn or new_signer_from_hex(private_key), transport=transport)
    client = client_class(base_url, http_client=signing_client, **_client_kwargs(wire_format, protocol))  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    _close_client_stream_requests(client, sync=False)
    _reject_bidi_streams(client)
    return client


def new_service_client_sync(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str | None = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
    stream_timeout: float = DEFAULT_STREAM_TIMEOUT,
    wire_format: WireFormat = WireFormat.BINARY,
    protocol: Protocol = Protocol.CONNECT,
    sign_fn: SignFn | None = None,
) -> T:
    """Create a sync ConnectRPC client with signing transport.

    Signing, timeouts and the rejection of bidirectional streams work as in new_service_client.

    Args:
        private_key: Hex-encoded secp256k1 private key: 64 hex digits, optionally after 0x or 0X.
            Ignored when sign_fn is given.
        client_class: Generated ConnectRPC sync client class (e.g. NetworkServiceClientSync).
        base_url: Base URL of the T-0 Network API; see new_service_client.
        timeout: Timeout of unary calls in seconds, 15 by default.
        stream_timeout: Timeout of client- and server-streaming calls in seconds, 300 by default.
        wire_format: WireFormat.BINARY (default) or WireFormat.JSON.
        protocol: Protocol.CONNECT (default) or Protocol.GRPC.
        sign_fn: Signs each request in place of private_key.

    Returns:
        An instance of client_class configured with signing transport.
    """
    base_url = _checked_base_url(base_url)
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    _check_enums(wire_format, protocol)
    transport = _transport(base_url, protocol, sync=True)
    signing_client = SigningSyncClient(sign_fn or new_signer_from_hex(private_key), transport=transport)
    client = client_class(base_url, http_client=signing_client, **_client_kwargs(wire_format, protocol))  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    _close_client_stream_requests(client, sync=True)
    _reject_bidi_streams(client)
    return client


def _checked_base_url(base_url: str | None) -> str:
    if base_url is None:
        return DEFAULT_BASE_URL
    if base_url == "":
        raise ValueError("base URL is not set")
    if "://" not in base_url:  # a value without a scheme is read as https
        base_url = "https://" + base_url
    if not _is_valid_base_url(base_url):
        raise ValueError("base URL is not valid")
    # connectrpc appends "/<service>/<method>", so a trailing "/" would double the slash.
    return base_url.removesuffix("/")


def _is_valid_base_url(base_url: str) -> bool:
    """http:// or https:// (any case), a host name or IP literal, a port of 1..65535 if given, and
    nothing after that but an optional "/"."""
    scheme, separator, _ = base_url.partition("://")
    if not separator or scheme.lower() not in ("http", "https"):
        return False
    try:
        parts = urlsplit(base_url)
        port = parts.port  # ValueError outside 0..65535 or not a number
    except ValueError:
        return False
    host = parts.hostname
    if not host or (port is not None and not 1 <= port <= 65535):
        return False
    # No user info, and no ':' without a port after it.
    if "@" in parts.netloc or parts.netloc.endswith(":"):
        return False
    # No path, query or fragment. urlsplit drops an empty "?" or "#", so look for the characters.
    if parts.path not in ("", "/") or "?" in base_url or "#" in base_url:
        return False
    if ":" in host:  # only a bracketed IPv6 literal keeps a ':' in its host
        try:
            ipaddress.IPv6Address(host)
        except ValueError:
            return False
        return True
    try:
        ipaddress.IPv4Address(host)
    except ValueError:
        return _HOST_NAME.fullmatch(host) is not None
    return True


def _check_enums(wire_format: WireFormat, protocol: Protocol) -> None:
    if not isinstance(wire_format, WireFormat):
        raise ValueError("wire_format must be WireFormat.BINARY or WireFormat.JSON")
    if not isinstance(protocol, Protocol):
        raise ValueError("protocol must be Protocol.CONNECT or Protocol.GRPC")


def _client_kwargs(wire_format: WireFormat, protocol: Protocol) -> dict[str, Any]:
    kwargs: dict[str, Any] = {
        "protocol": ProtocolType.GRPC if protocol is Protocol.GRPC else ProtocolType.CONNECT,
        # Requests go out uncompressed; the generated classes would gzip every message.
        "send_compression": None,
        # Each call gets its default from _set_default_timeouts.
        "timeout_ms": None,
    }
    if wire_format is WireFormat.JSON:
        # The generated classes use google.protobuf messages; binary is their own default codec.
        kwargs["codec"] = google_protobuf_json_codec()
    return kwargs


def _transport(base_url: str, protocol: Protocol, *, sync: bool) -> Any | None:
    # gRPC needs HTTP/2, and pyqwest speaks HTTP/1.1 on a plain http:// connection unless told.
    if protocol is not Protocol.GRPC or urlsplit(base_url).scheme != "http":
        return None
    return _shared_transport(sync=sync, http2=True)


def _default_timeouts_ms(timeout: float, stream_timeout: float) -> tuple[int, int]:
    return _timeout_ms("timeout", timeout, 1000), _timeout_ms("stream_timeout", stream_timeout, 1000)


def _timeout_ms(name: str, value: object, ms_per_unit: int) -> int:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        ms = value * ms_per_unit
        if 0 < ms <= MAX_TIMEOUT_MS:  # False for NaN too
            # Rounded up: a fraction never shortens the timeout, and never becomes 0, which
            # connectrpc reads as "no timeout".
            return math.ceil(ms)
    raise ValueError(f"{name} must be a positive duration of at most {MAX_TIMEOUT_MS} ms")


def _set_default_timeouts(client: object, unary_ms: int, stream_ms: int) -> None:
    """A ConnectRPC client has one timeout_ms for every call, so each execute method fills in its own."""
    for name, streaming in _EXECUTE_METHODS:
        execute = getattr(client, name, None)
        if execute is not None:
            setattr(client, name, _with_default_timeout(execute, stream_ms if streaming else unary_ms))


def _close_client_stream_requests(client: object, *, sync: bool) -> None:
    """Ends the caller's request iterator with the call. connectrpc does not close it, so after an
    early failure it would stay open until garbage collection, and a sync transport would go on
    pulling messages from it in its writer thread."""
    execute = getattr(client, "execute_client_stream", None)
    if execute is not None:
        wrap = _with_closing_request_sync if sync else _with_closing_request
        setattr(client, "execute_client_stream", wrap(execute))  # noqa: B010


def _with_closing_request(execute: Callable[..., Any]) -> Callable[..., Any]:
    @functools.wraps(execute)
    async def execute_client_stream(*args: Any, request: Any, **kwargs: Any) -> Any:
        messages = _CallRequest(request)
        try:
            return await execute(*args, request=messages, **kwargs)
        finally:
            messages.finish()

    return execute_client_stream


def _with_closing_request_sync(execute: Callable[..., Any]) -> Callable[..., Any]:
    @functools.wraps(execute)
    def execute_client_stream(*args: Any, request: Any, **kwargs: Any) -> Any:
        messages = _CallRequestSync(request)
        try:
            return execute(*args, request=messages, **kwargs)
        finally:
            messages.finish()

    return execute_client_stream


class _CallRequest:
    """The caller's request messages, ended and closed when the call ends."""

    def __init__(self, request: Any) -> None:
        self._source = aiter(request)
        self._done = False

    def __aiter__(self) -> _CallRequest:
        return self

    async def __anext__(self) -> Any:
        if self._done:
            self._close()
            raise StopAsyncIteration
        return await anext(self._source)

    def finish(self) -> None:
        self._done = True
        self._close()

    def _close(self) -> None:
        # In the background and best effort: the caller's cleanup may await anything, and it must
        # never delay the call's result (a deadline error included) or replace it.
        source = self._source
        if inspect.isasyncgen(source) and inspect.getasyncgenstate(source) in (
            inspect.AGEN_RUNNING,
            inspect.AGEN_CLOSED,
        ):
            # RUNNING: the transport's task is inside it; the transport cancels that task when the
            # call ends, and the cancellation closes it.
            return
        if getattr(source, "aclose", None) is None:
            return
        task = asyncio.get_running_loop().create_task(_aclose_logged(source))
        _CLOSING.add(task)
        task.add_done_callback(_CLOSING.discard)


class _CallRequestSync:
    """Sync variant of _CallRequest. The transport's writer thread may be inside the source when the
    call ends; then it is closed from that thread at the next pull, which stops there."""

    def __init__(self, request: Any) -> None:
        self._source = iter(request)
        self._done = False

    def __iter__(self) -> _CallRequestSync:
        return self

    def __next__(self) -> Any:
        if self._done:
            self._close()
            raise StopIteration
        return next(self._source)

    def finish(self) -> None:
        self._done = True
        self._close()

    def _close(self) -> None:
        # In a thread of its own and best effort: the caller's cleanup may block, and it must never
        # delay the call's result (a deadline error included) or replace it.
        source = self._source
        if isinstance(source, types.GeneratorType) and inspect.getgeneratorstate(source) in (
            inspect.GEN_RUNNING,  # the writer thread is inside it; it closes it at its next pull
            inspect.GEN_CLOSED,
        ):
            return
        if getattr(source, "close", None) is None:
            return
        threading.Thread(target=_close_logged, args=(source,), name="t0-close-request", daemon=True).start()


async def _aclose_logged(source: Any) -> None:
    try:
        await _aclose(source)
    except Exception:
        _LOGGER.warning("closing the request messages of a client stream failed", exc_info=True)


def _close_logged(source: Any) -> None:
    try:
        _close(source)
    except Exception:
        if isinstance(source, types.GeneratorType) and inspect.getgeneratorstate(source) == inspect.GEN_RUNNING:
            return  # the writer thread entered it between the check and the close; it closes it next
        _LOGGER.warning("closing the request messages of a client stream failed", exc_info=True)


def _reject_bidi_streams(client: object) -> None:
    """A policy, not a signing limit: the network does not accept bidirectional streams (#370)."""
    if getattr(client, "execute_bidi_stream", None) is not None:
        setattr(client, "execute_bidi_stream", _bidi_stream_unsupported)  # noqa: B010


def _bidi_stream_unsupported(*args: Any, **kwargs: Any) -> NoReturn:
    # Raised at the call for the async client too: its execute_bidi_stream is a plain def.
    raise ConnectError(Code.UNIMPLEMENTED, "bidirectional streams are not supported")


def _with_default_timeout(execute: Callable[..., Any], default_ms: int) -> Callable[..., Any]:
    @functools.wraps(execute)
    def execute_with_default_timeout(*args: Any, timeout_ms: int | None = None, **kwargs: Any) -> Any:
        # Checked here: connectrpc would read 0 as "use the client's timeout" and pass on a negative one.
        timeout_ms = default_ms if timeout_ms is None else _timeout_ms("timeout_ms", timeout_ms, 1)
        return execute(*args, timeout_ms=timeout_ms, **kwargs)

    return execute_with_default_timeout
