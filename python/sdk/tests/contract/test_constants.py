"""The named constants and messages of the SDK against `constants` and `messages` in
cross_test/test_vectors.json, the file every SDK compares its constants with."""

import asyncio
import json
from pathlib import Path

import pytest
from t0_provider_sdk import _messages
from t0_provider_sdk.common import PUBLIC_KEY_HEADER, SIGNATURE_HEADER, SIGNATURE_TIMESTAMP_HEADER
from t0_provider_sdk.network import DEFAULT_BASE_URL, DEFAULT_STREAM_TIMEOUT, DEFAULT_TIMEOUT, MAX_TIMEOUT
from t0_provider_sdk.provider import DEFAULT_MAX_BODY_SIZE, TIMESTAMP_WINDOW_MS, new_asgi_app, new_wsgi_app

from ..provider.test_handler import PUBLIC_KEY, _call_asgi, _call_wsgi, _sign_request, new_signer_from_hex

VECTORS = json.loads((Path(__file__).resolve().parents[4] / "cross_test" / "test_vectors.json").read_text())
CONSTANTS = VECTORS["constants"]

# Python's client timeouts are in seconds; the shared file's in milliseconds.
SDK = {
    "default_max_body_size": DEFAULT_MAX_BODY_SIZE,
    "timestamp_window_ms": TIMESTAMP_WINDOW_MS,
    "public_key_header": PUBLIC_KEY_HEADER,
    "signature_header": SIGNATURE_HEADER,
    "signature_timestamp_header": SIGNATURE_TIMESTAMP_HEADER,
    "default_base_url": DEFAULT_BASE_URL,
    "default_timeout_ms": round(DEFAULT_TIMEOUT * 1000),
    "default_stream_timeout_ms": round(DEFAULT_STREAM_TIMEOUT * 1000),
    "max_timeout_ms": round(MAX_TIMEOUT * 1000),
}

# The messages Python raises: all but those whose messages_scope entry leaves Python out.
PYTHON_MESSAGES = {
    name: text
    for name, text in VECTORS["messages"].items()
    if "python" in VECTORS["messages_scope"].get(name, ["python"])
}
SDK_MESSAGES = {name.lower(): value for name, value in vars(_messages).items() if name.isupper()}


def test_every_shared_constant_is_defined() -> None:
    assert sorted(SDK) == sorted(CONSTANTS)


def test_values_match_the_shared_file() -> None:
    for name, value in CONSTANTS.items():
        assert SDK[name] == value, name


def test_exactly_the_python_messages_are_defined() -> None:
    assert sorted(SDK_MESSAGES) == sorted(PYTHON_MESSAGES)


def test_messages_match_the_shared_file() -> None:
    """A `{placeholder}` message is a str.format template with the same placeholder, so the text
    compares as it is."""
    for name, text in PYTHON_MESSAGES.items():
        assert SDK_MESSAGES[name] == text, name


def _check_request(size: int) -> bytes:
    """A HealthCheckRequest of exactly size bytes: field 1 (service), its length, then the name."""
    for length_bytes in range(1, 6):
        name = size - 1 - length_bytes
        if name >= 0 and len(_varint(name)) == length_bytes:
            return b"\x0a" + _varint(name) + b"x" * name
    raise AssertionError(size)


def _varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte, value = value & 0x7F, value >> 7
        out.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(out)


@pytest.mark.parametrize("transport", ["asgi", "wsgi"])
@pytest.mark.parametrize("case", VECTORS["max_body_size_cases"], ids=lambda case: case["name"])
def test_max_body_size_cases(transport: str, case: dict) -> None:
    """The limit a server built with max_body_size uses: a body of exactly the limit is read (the
    service asked for is unknown), one byte more is refused as too large."""
    limit = case["limit"]
    for size, code in [(limit, "not_found"), (limit + 1, "resource_exhausted")]:
        body = _check_request(size)
        assert len(body) == size
        assert _call(transport, body, case["input"]) == code, (size, code)


def _call(transport: str, body: bytes, max_body_size: int) -> str:
    """The code a Check with this body gets from new_asgi_app / new_wsgi_app (signed over the body)."""
    headers = dict(_sign_request(new_signer_from_hex(VECTORS["keys"]["private_key"]), body, None).items())
    path = "/grpc.health.v1.Health/Check"
    if transport == "asgi":
        return asyncio.run(_call_asgi(new_asgi_app(PUBLIC_KEY, max_body_size=max_body_size), path, headers, body))[0]
    return _call_wsgi(new_wsgi_app(PUBLIC_KEY, max_body_size=max_body_size), path, headers, body)[0]
