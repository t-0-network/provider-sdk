"""Generic client factory for creating ConnectRPC clients with signing transport.

Proto-agnostic: works with ANY generated ConnectRPC client class.
"""

from __future__ import annotations

import functools
import re
from typing import TYPE_CHECKING, Any, NoReturn, TypeVar
from urllib.parse import urlsplit

from connectrpc.code import Code
from connectrpc.compat import google_protobuf_json_codec
from connectrpc.errors import ConnectError
from connectrpc.protocol import ProtocolType

from t0_provider_sdk._messages import (
    BASE_URL_NOT_SET,
    BASE_URL_NOT_VALID,
    BIDI_NOT_SUPPORTED,
    CALL_DEADLINE_PASSED,
    KEY_AND_SIGNER,
    SIGNER_NULL,
    STREAM_TIMEOUT_NOT_VALID,
    TIMEOUT_NOT_VALID,
)
from t0_provider_sdk.crypto.signer import SignFn, new_signer_from_hex
from t0_provider_sdk.network.options import (
    DEFAULT_BASE_URL,
    DEFAULT_STREAM_TIMEOUT,
    DEFAULT_TIMEOUT,
    MAX_TIMEOUT,
    Protocol,
    WireFormat,
)
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient, _shared_transport

if TYPE_CHECKING:
    from collections.abc import Callable

T = TypeVar("T")


# (execute method, streams?). gRPC unary goes out through stream() but enters through execute_unary.
_EXECUTE_METHODS = (
    ("execute_unary", False),
    ("execute_client_stream", True),
    ("execute_server_stream", True),
)


def new_service_client(
    private_key: str | SignFn | None,
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
        private_key: The signer: a hex-encoded secp256k1 private key (64 hex digits, optionally
            after 0x or 0X), or a SignFn, such as new_signer_from_hex's result or a function that
            signs with a key held elsewhere. None (with no sign_fn) raises ValueError ("signer must
            not be null"). With sign_fn, it must be None or "": both given raise ValueError.
        client_class: Generated ConnectRPC async client class (e.g. NetworkServiceClient).
        base_url: Base URL of the T-0 Network API. None means the default,
            https://api.t-0.network; an empty or malformed value, or one with whitespace or a
            control character anywhere, raises ValueError.
        timeout: Timeout of unary calls in seconds, 15 by default.
        stream_timeout: Timeout of client- and server-streaming calls in seconds, including the
            wait for the first request message, 300 by default.
        wire_format: WireFormat.BINARY (default) or WireFormat.JSON.
        protocol: Protocol.CONNECT (default) or Protocol.GRPC. gRPC on an http:// base URL uses
            HTTP/2 without TLS.
        sign_fn: A SignFn that signs each request in place of private_key; the same as passing it as
            private_key.

    A SignFn's output is checked before a request is sent: a signature of 64 or 65 bytes, sent as
    it is, and a 65-byte uncompressed public key. A failed check, or an exception of the SignFn,
    fails the call with INTERNAL "signing the request failed: <cause>".

    Each timeout must be greater than zero and at most MAX_TIMEOUT (2147483647 ms); one under a
    millisecond counts as one. A call's own ``timeout_ms`` replaces the default, whether shorter or
    longer; a ``timeout_ms`` of 0 or less fails the call with DEADLINE_EXCEEDED before it is sent.

    Returns:
        An instance of client_class configured with signing transport.
    """
    base_url = _checked_base_url(base_url)
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    transport = _transport(base_url, protocol, sync=False)
    signing_client = SigningClient(_signer(private_key, sign_fn), transport=transport)
    client = client_class(base_url, http_client=signing_client, **_client_kwargs(wire_format, protocol))  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    _reject_bidi_streams(client)
    return client


def new_service_client_sync(
    private_key: str | SignFn | None,
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
        private_key: The signer: a hex-encoded private key or a SignFn; see new_service_client.
        client_class: Generated ConnectRPC sync client class (e.g. NetworkServiceClientSync).
        base_url: Base URL of the T-0 Network API; see new_service_client.
        timeout: Timeout of unary calls in seconds, 15 by default.
        stream_timeout: Timeout of client- and server-streaming calls in seconds, 300 by default.
        wire_format: WireFormat.BINARY (default) or WireFormat.JSON.
        protocol: Protocol.CONNECT (default) or Protocol.GRPC.
        sign_fn: A SignFn that signs each request in place of private_key.

    Returns:
        An instance of client_class configured with signing transport.
    """
    base_url = _checked_base_url(base_url)
    unary_ms, stream_ms = _default_timeouts_ms(timeout, stream_timeout)
    transport = _transport(base_url, protocol, sync=True)
    signing_client = SigningSyncClient(_signer(private_key, sign_fn), transport=transport)
    client = client_class(base_url, http_client=signing_client, **_client_kwargs(wire_format, protocol))  # type: ignore[call-arg]
    _set_default_timeouts(client, unary_ms, stream_ms)
    _reject_bidi_streams(client)
    return client


def _signer(private_key: str | SignFn | None, sign_fn: SignFn | None) -> SignFn:
    """The signer of a client: sign_fn, else private_key, which is a hex key or a SignFn."""
    if sign_fn is not None:
        # A key is never silently dropped for the sign_fn given with it.
        if private_key is not None and private_key != "":
            raise ValueError(KEY_AND_SIGNER)
        return sign_fn
    if private_key is None:
        raise ValueError(SIGNER_NULL)
    if isinstance(private_key, str):
        return new_signer_from_hex(private_key)
    return private_key


# A whitespace or control character: U+0000..U+0020, U+007F, or another Unicode White_Space
# character. The same set in every SDK.
_SPACE_OR_CONTROL = re.compile(r"[\x00-\x20\x7f\x85\xa0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]")


def _checked_base_url(base_url: str | None) -> str:
    if base_url is None:
        return DEFAULT_BASE_URL
    if base_url == "":
        raise ValueError(BASE_URL_NOT_SET)
    if "://" not in base_url:  # a value without a scheme is read as https
        base_url = "https://" + base_url
    # Never trimmed; and urlsplit would drop a tab or newline anywhere, and leading spaces, and
    # keep the others.
    if _SPACE_OR_CONTROL.search(base_url):
        raise ValueError(BASE_URL_NOT_VALID)
    try:
        parts = urlsplit(base_url)
        port = parts.port  # ValueError outside 0..65535
    except ValueError:
        raise ValueError(BASE_URL_NOT_VALID) from None
    # urlsplit drops an empty "?" or "#", so look for the characters.
    if (
        parts.scheme not in ("http", "https")
        or not parts.hostname
        or parts.username is not None
        or port == 0
        or "?" in base_url
        or "#" in base_url
    ):
        raise ValueError(BASE_URL_NOT_VALID)
    # A path prefixes every call. connectrpc appends "/<service>/<method>", so a trailing "/" would
    # double the slash.
    return base_url.removesuffix("/")


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
    return _timeout_ms(timeout, TIMEOUT_NOT_VALID), _timeout_ms(stream_timeout, STREAM_TIMEOUT_NOT_VALID)


def _timeout_ms(seconds: float, message: str) -> int:
    # Not a bool (True would be 1 s); NaN fails the comparison.
    if isinstance(seconds, bool) or not 0 < seconds <= MAX_TIMEOUT:
        raise ValueError(message)
    # Whole milliseconds, as connectrpc writes the value into the timeout header; at least one, since
    # 0 would mean no deadline.
    return max(1, round(seconds * 1000))


def _set_default_timeouts(client: object, unary_ms: int, stream_ms: int) -> None:
    """A ConnectRPC client has one timeout_ms for every call, so each execute method fills in its own."""
    for name, streaming in _EXECUTE_METHODS:
        execute = getattr(client, name, None)
        if execute is not None:
            setattr(client, name, _with_default_timeout(execute, stream_ms if streaming else unary_ms))


def _reject_bidi_streams(client: object) -> None:
    """A policy, not a signing limit: the network does not accept bidirectional streams (#370)."""
    if getattr(client, "execute_bidi_stream", None) is not None:
        setattr(client, "execute_bidi_stream", _bidi_stream_unsupported)  # noqa: B010


def _bidi_stream_unsupported(*args: Any, **kwargs: Any) -> NoReturn:
    # Raised at the call for the async client too: its execute_bidi_stream is a plain def.
    raise ConnectError(Code.UNIMPLEMENTED, BIDI_NOT_SUPPORTED)


def _with_default_timeout(execute: Callable[..., Any], default_ms: int) -> Callable[..., Any]:
    @functools.wraps(execute)
    def execute_with_default_timeout(*args: Any, timeout_ms: int | None = None, **kwargs: Any) -> Any:
        if timeout_ms is not None and timeout_ms <= 0:
            # A deadline that has already passed: connectrpc would take 0 as no deadline at all.
            _deadline_passed()
        return execute(*args, timeout_ms=default_ms if timeout_ms is None else timeout_ms, **kwargs)

    return execute_with_default_timeout


def _deadline_passed() -> NoReturn:
    raise ConnectError(Code.DEADLINE_EXCEEDED, CALL_DEADLINE_PASSED)
