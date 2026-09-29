"""Tests for the streaming paths of the signing transport.

Each test replaces the wrapped pyqwest client with a fake that records what it is asked to send
and reads the request body the way pyqwest does, closing it at the end.
"""

from __future__ import annotations

import struct
import time
from contextlib import asynccontextmanager, contextmanager

import pyqwest
import pytest
from t0_provider_sdk.common.headers import (
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient, _is_enveloped

PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
SIGN_FN = new_signer_from_hex(PRIVATE_KEY)
URL = "http://example.test/test.v1.StreamTest/ClientStream"

CONNECT_STREAM = "application/connect+proto"
GRPC = "application/grpc+proto"


def _envelope(payload: bytes) -> bytes:
    return struct.pack(">BI", 0, len(payload)) + payload


ENV1 = _envelope(b"message-1")
ENV2 = _envelope(b"message-2")
ENV3 = _envelope(b"message-3")


def _headers(content_type: str) -> pyqwest.Headers:
    return pyqwest.Headers({"Content-Type": content_type})


def _assert_signed_over(headers: pyqwest.Headers, signed: bytes) -> None:
    timestamp_ms = int(headers[SIGNATURE_TIMESTAMP_HEADER])
    expected, _ = SIGN_FN(legacy_keccak256(signed + struct.pack("<Q", timestamp_ms)))
    assert headers[SIGNATURE_HEADER] == f"0x{expected.hex()}"


class _FakeClient:
    """Stands in for pyqwest.Client."""

    def __init__(self, events: list[str] | None = None, *, read_body: bool = True) -> None:
        self.events = events if events is not None else []
        self.read_body = read_body
        self.headers: pyqwest.Headers | None = None
        self.content: object = None
        self.body: bytes | None = None

    @asynccontextmanager
    async def stream(self, method, url, headers=None, content=None):
        self.events.append("request sent")
        self.headers, self.content = headers, content
        if content is None or isinstance(content, bytes):
            self.body = content
        else:
            chunks = []
            try:
                if self.read_body:
                    async for chunk in content:
                        chunks.append(chunk)
            finally:
                await content.aclose()
            self.body = b"".join(chunks)
        yield "response"


class _FakeSyncClient:
    """Stands in for pyqwest.SyncClient."""

    def __init__(self, events: list[str] | None = None, *, read_body: bool = True) -> None:
        self.events = events if events is not None else []
        self.read_body = read_body
        self.headers: pyqwest.Headers | None = None
        self.content: object = None
        self.body: bytes | None = None
        self.timeout: float | None = None

    @contextmanager
    def stream(self, method, url, headers=None, content=None, timeout=None):
        self.events.append("request sent")
        self.headers, self.content, self.timeout = headers, content, timeout
        if content is None or isinstance(content, bytes):
            self.body = content
        else:
            chunks = []
            try:
                if self.read_body:
                    chunks.extend(content)
            finally:
                content.close()
            self.body = b"".join(chunks)
        yield "response"


def _async_client(fake: _FakeClient) -> SigningClient:
    client = SigningClient(SIGN_FN)
    client._inner = fake  # type: ignore[assignment]
    return client


def _sync_client(fake: _FakeSyncClient) -> SigningSyncClient:
    client = SigningSyncClient(SIGN_FN)
    client._inner = fake  # type: ignore[assignment]
    return client


async def _agen(*chunks: bytes):
    for chunk in chunks:
        yield chunk


async def _send(fake: _FakeClient, content_type: str, content) -> None:
    async with _async_client(fake).stream("POST", URL, headers=_headers(content_type), content=content) as resp:
        assert resp == "response"


def _send_sync(fake: _FakeSyncClient, content_type: str, content, timeout: float | None = None) -> None:
    client = _sync_client(fake)
    with client.stream("POST", URL, headers=_headers(content_type), content=content, timeout=timeout) as resp:
        assert resp == "response"


@pytest.mark.parametrize(
    ("content_type", "enveloped"),
    [
        ("application/connect+proto", True),
        ("application/connect+json", True),
        ("application/grpc", True),
        ("application/grpc+proto", True),
        ("Application/GRPC+proto; charset=utf-8", True),
        ("application/grpc-web", False),
        ("application/grpc-web+proto", False),
        ("application/proto", False),
        ("application/json", False),
        ("", False),
    ],
)
def test_is_enveloped(content_type: str, enveloped: bool) -> None:
    assert _is_enveloped(_headers(content_type)) is enveloped


def test_is_enveloped_without_headers() -> None:
    assert _is_enveloped(None) is False


# (chunks the source yields, what the signature covers)
CHUNKINGS = {
    "one envelope per chunk": ([ENV1, ENV2, ENV3], ENV1),
    "first envelope split across chunks": ([ENV1[:2], ENV1[2:7], ENV1[7:] + ENV2[:3], ENV2[3:], ENV3], ENV1),
    "first envelope merged with the next": ([ENV1 + ENV2, ENV3], ENV1),
    "empty stream": ([], b""),
}


@pytest.mark.asyncio
class TestSigningClientStream:
    @pytest.mark.parametrize("content_type", [CONNECT_STREAM, GRPC])
    @pytest.mark.parametrize("chunking", CHUNKINGS.keys())
    async def test_signs_first_envelope_and_forwards_all(self, content_type: str, chunking: str) -> None:
        chunks, signed = CHUNKINGS[chunking]
        fake = _FakeClient()
        await _send(fake, content_type, _agen(*chunks))

        _assert_signed_over(fake.headers, signed)
        assert fake.body == b"".join(chunks)

    async def test_sends_before_the_second_message(self) -> None:
        events: list[str] = []

        async def source():
            events.append("message 1")
            yield ENV1
            events.append("message 2")
            yield ENV2

        fake = _FakeClient(events)
        await _send(fake, CONNECT_STREAM, source())

        assert events == ["message 1", "request sent", "message 2"]
        assert fake.body == ENV1 + ENV2

    async def test_reads_first_message_on_enter(self) -> None:
        """connectrpc enters the context inside its call timeout, which must cover the wait."""
        events: list[str] = []

        async def source():
            events.append("message 1")
            yield ENV1

        cm = _async_client(_FakeClient(events)).stream("POST", URL, headers=_headers(GRPC), content=source())
        assert events == []
        async with cm:
            assert events == ["message 1", "request sent"]

    async def test_close_is_forwarded_to_source(self) -> None:
        events: list[str] = []

        async def source():
            try:
                yield ENV1
                yield ENV2
            finally:
                events.append("source closed")

        fake = _FakeClient(events, read_body=False)  # keeps the source alive: only a close ends it
        await _send(fake, CONNECT_STREAM, source())

        assert events == ["request sent", "source closed"]

    async def test_source_closed_when_first_message_fails(self) -> None:
        class Source:
            closed = False

            def __aiter__(self):
                return self

            async def __anext__(self) -> bytes:
                raise ValueError("encoding failed")

            async def aclose(self) -> None:
                Source.closed = True

        fake = _FakeClient()
        with pytest.raises(ValueError, match="encoding failed"):
            await _send(fake, CONNECT_STREAM, Source())
        assert Source.closed
        assert fake.events == []

    async def test_bytes_content_is_signed_whole(self) -> None:
        fake = _FakeClient()
        body = ENV1 + ENV2
        await _send(fake, "application/proto", body)

        _assert_signed_over(fake.headers, body)
        assert fake.content is body

    async def test_grpc_web_iterator_is_signed_whole(self) -> None:
        fake = _FakeClient()
        await _send(fake, "application/grpc-web+proto", _agen(ENV1, ENV2))

        _assert_signed_over(fake.headers, ENV1 + ENV2)
        assert fake.content == ENV1 + ENV2


class TestSigningSyncClientStream:
    @pytest.mark.parametrize("content_type", [CONNECT_STREAM, GRPC])
    @pytest.mark.parametrize("chunking", CHUNKINGS.keys())
    def test_signs_first_envelope_and_forwards_all(self, content_type: str, chunking: str) -> None:
        chunks, signed = CHUNKINGS[chunking]
        fake = _FakeSyncClient()
        _send_sync(fake, content_type, iter(chunks))

        _assert_signed_over(fake.headers, signed)
        assert fake.body == b"".join(chunks)

    def test_sends_before_the_second_message(self) -> None:
        events: list[str] = []

        def source():
            events.append("message 1")
            yield ENV1
            events.append("message 2")
            yield ENV2

        fake = _FakeSyncClient(events)
        _send_sync(fake, CONNECT_STREAM, source())

        assert events == ["message 1", "request sent", "message 2"]
        assert fake.body == ENV1 + ENV2

    def test_reads_first_message_on_enter(self) -> None:
        events: list[str] = []

        def source():
            events.append("message 1")
            yield ENV1

        cm = _sync_client(_FakeSyncClient(events)).stream("POST", URL, headers=_headers(GRPC), content=source())
        assert events == []
        with cm:
            assert events == ["message 1", "request sent"]

    def test_close_is_forwarded_to_source(self) -> None:
        events: list[str] = []

        def source():
            try:
                yield ENV1
                yield ENV2
            finally:
                events.append("source closed")

        fake = _FakeSyncClient(events, read_body=False)  # keeps the source alive: only a close ends it
        _send_sync(fake, CONNECT_STREAM, source())

        assert events == ["request sent", "source closed"]

    def test_close_skips_a_running_source(self) -> None:
        """pyqwest may close the body from another thread while its writer thread is inside the
        source; closing a running generator raises, so the chain leaves it alone."""
        fake = _FakeSyncClient()

        def source():
            yield ENV1
            fake.content.close()  # the source is running here
            yield ENV2

        _send_sync(fake, CONNECT_STREAM, source())

        assert fake.body == ENV1 + ENV2

    def test_source_closed_when_first_message_fails(self) -> None:
        class Source:
            closed = False

            def __iter__(self):
                return self

            def __next__(self) -> bytes:
                raise ValueError("encoding failed")

            def close(self) -> None:
                Source.closed = True

        fake = _FakeSyncClient()
        with pytest.raises(ValueError, match="encoding failed"):
            _send_sync(fake, CONNECT_STREAM, Source())
        assert Source.closed
        assert fake.events == []

    def test_timeout_covers_the_wait_for_the_first_message(self) -> None:
        def source():
            time.sleep(0.05)
            yield ENV1

        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, source(), timeout=10.0)

        assert fake.timeout is not None
        assert 9.0 < fake.timeout <= 10.0 - 0.05

    def test_timeout_elapsed_waiting_for_the_first_message(self) -> None:
        events: list[str] = []

        def source():
            try:
                time.sleep(0.05)
                yield ENV1
            finally:
                events.append("source closed")

        with pytest.raises(TimeoutError):
            _send_sync(_FakeSyncClient(events), CONNECT_STREAM, source(), timeout=0.01)
        assert events == ["source closed"]

    def test_no_timeout_stays_none(self) -> None:
        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, iter([ENV1]))
        assert fake.timeout is None

    def test_bytes_content_is_signed_whole(self) -> None:
        fake = _FakeSyncClient()
        body = ENV1 + ENV2
        _send_sync(fake, "application/proto", body, timeout=5.0)

        _assert_signed_over(fake.headers, body)
        assert fake.content is body
        assert fake.timeout == 5.0

    def test_grpc_web_iterator_is_signed_whole(self) -> None:
        fake = _FakeSyncClient()
        _send_sync(fake, "application/grpc-web+proto", iter([ENV1, ENV2]))

        _assert_signed_over(fake.headers, ENV1 + ENV2)
        assert fake.content == ENV1 + ENV2
