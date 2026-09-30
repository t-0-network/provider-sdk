"""Tests for the streaming paths of the signing transport.

The fake pyqwest clients read the request body the way pyqwest does and close it at the end.
"""

from __future__ import annotations

import struct
import time
from contextlib import asynccontextmanager, contextmanager
from types import SimpleNamespace

import pyqwest
import pytest
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from t0_provider_sdk.common.headers import (
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient

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

    def post(self, url, headers=None, content=None, timeout=None):
        self.events.append("request sent")
        self.headers, self.body, self.timeout = headers, content, timeout
        return "response"

    @contextmanager
    def stream(self, method, url, headers=None, content=None, timeout=None):
        self.events.append("request sent")
        self.headers, self.content, self.timeout = headers, content, timeout
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


@pytest.mark.parametrize("operation", ["post", "stream"])
@pytest.mark.parametrize("timeout", [1.0, None])
@pytest.mark.parametrize("signing_time", [0.25, 1.25])
def test_sync_timeout_includes_signing(monkeypatch, operation, timeout, signing_time) -> None:
    now = 0.0
    closed = []

    def sign(digest):
        nonlocal now
        now += signing_time
        return SIGN_FN(digest)

    def source():
        nonlocal now
        try:
            now += 0.25
            yield ENV1
        finally:
            closed.append(True)

    monkeypatch.setattr("t0_provider_sdk.network.signing.time", SimpleNamespace(monotonic=lambda: now, time=time.time))
    fake = _FakeSyncClient()
    client = SigningSyncClient(sign)
    client._inner = fake  # type: ignore[assignment]

    def send():
        if operation == "stream":
            with client.stream("POST", URL, content=source(), timeout=timeout) as response:
                assert response == "response"
        else:
            assert client.post(URL, content=ENV1, timeout=timeout) == "response"

    elapsed = signing_time + (0.25 if operation == "stream" else 0)
    if timeout is not None and elapsed >= timeout:
        with pytest.raises(TimeoutError, match="request signing"):
            send()
        assert fake.events == [], "an expired call must never reach the transport"
    else:
        send()
        assert fake.events == ["request sent"]
        assert fake.timeout == (None if timeout is None else timeout - elapsed)
    if operation == "stream":
        assert closed == [True]


BROKEN_FIRST_MESSAGE = "streaming request ends inside its first message"

# A first chunk that ends early is a broken first message; one that holds more than an envelope
# means the library's framing changed. Neither is signed.
BAD_FIRST_CHUNKS = {
    "first envelope split across chunks": ([ENV1[:7], ENV1[7:], ENV2], Code.INVALID_ARGUMENT),
    "first envelope merged with the next": ([ENV1 + ENV2, ENV3], Code.INTERNAL),
    "partial prefix": ([ENV1[:3]], Code.INVALID_ARGUMENT),
    "empty first chunk": ([b"", ENV1], Code.INVALID_ARGUMENT),
}


def _closing_source(chunks: list[bytes], events: list[str]):
    try:
        yield from chunks
    finally:
        events.append("source closed")


async def _closing_asource(chunks: list[bytes], events: list[str]):
    try:
        for chunk in chunks:
            yield chunk
    finally:
        events.append("source closed")


@pytest.mark.asyncio
class TestSigningClientStream:
    async def test_empty_stream_is_signed_over_nothing(self) -> None:
        fake = _FakeClient()
        await _send(fake, CONNECT_STREAM, _agen())

        _assert_signed_over(fake.headers, b"")
        assert fake.body == b""

    @pytest.mark.parametrize("chunking", BAD_FIRST_CHUNKS.keys())
    async def test_refuses_a_first_chunk_that_is_not_one_envelope(self, chunking: str) -> None:
        events: list[str] = []
        fake = _FakeClient(events)
        with pytest.raises(ConnectError) as exc:
            await _send(fake, CONNECT_STREAM, _closing_asource(BAD_FIRST_CHUNKS[chunking][0], events))
        assert exc.value.code == BAD_FIRST_CHUNKS[chunking][1]
        if exc.value.code == Code.INVALID_ARGUMENT:
            assert exc.value.message == BROKEN_FIRST_MESSAGE
        assert events == ["source closed"], "nothing is sent and the source is closed"

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

    async def test_stream_is_signed_over_its_first_envelope(self) -> None:
        fake = _FakeClient()
        await _send(fake, CONNECT_STREAM, _agen(ENV1, ENV2))

        _assert_signed_over(fake.headers, ENV1)
        assert fake.body == ENV1 + ENV2

    async def test_bytes_body_is_signed_over_its_first_envelope(self) -> None:
        """A pre-framed body given whole, and no headers: the signature covers its first envelope as
        sent, and the body goes out unchanged."""
        fake = _FakeClient()
        async with _async_client(fake).stream("POST", URL, content=ENV1 + ENV2):
            pass

        _assert_signed_over(fake.headers, ENV1)
        assert fake.body == ENV1 + ENV2

    async def test_no_content_is_an_empty_stream(self) -> None:
        fake = _FakeClient()
        async with _async_client(fake).stream("POST", URL):
            pass

        _assert_signed_over(fake.headers, b"")
        assert fake.body == b""

    async def test_bytes_body_ending_inside_its_first_envelope_is_refused(self) -> None:
        fake = _FakeClient()
        with pytest.raises(ConnectError) as exc:
            async with _async_client(fake).stream("POST", URL, content=ENV1[:-1]):
                pass
        assert exc.value.code == Code.INVALID_ARGUMENT
        assert fake.events == [], "nothing is sent"


class TestSigningSyncClientStream:
    def test_empty_stream_is_signed_over_nothing(self) -> None:
        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, iter([]))

        _assert_signed_over(fake.headers, b"")
        assert fake.body == b""

    @pytest.mark.parametrize("chunking", BAD_FIRST_CHUNKS.keys())
    def test_refuses_a_first_chunk_that_is_not_one_envelope(self, chunking: str) -> None:
        events: list[str] = []
        fake = _FakeSyncClient(events)
        with pytest.raises(ConnectError) as exc:
            _send_sync(fake, CONNECT_STREAM, _closing_source(BAD_FIRST_CHUNKS[chunking][0], events))
        assert exc.value.code == BAD_FIRST_CHUNKS[chunking][1]
        if exc.value.code == Code.INVALID_ARGUMENT:
            assert exc.value.message == BROKEN_FIRST_MESSAGE
        assert events == ["source closed"], "nothing is sent and the source is closed"

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

    def test_wait_for_the_first_message_is_deducted_from_the_timeout(self) -> None:
        def source():
            time.sleep(0.05)
            yield ENV1

        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, source(), timeout=10.0)

        assert fake.timeout is not None
        assert 9.0 < fake.timeout <= 10.0 - 0.05

    def test_first_message_after_the_timeout_is_not_sent(self) -> None:
        """A blocked source is not interrupted: the call fails once it yields, with nothing sent."""
        events: list[str] = []

        def source():
            try:
                time.sleep(0.3)
                yield ENV1
            finally:
                events.append("source closed")

        started = time.monotonic()
        with pytest.raises(TimeoutError):
            _send_sync(_FakeSyncClient(events), CONNECT_STREAM, source(), timeout=0.05)
        assert time.monotonic() - started >= 0.3
        assert events == ["source closed"], "nothing is sent and the source is closed"

    def test_no_timeout_stays_none(self) -> None:
        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, iter([ENV1]))
        assert fake.timeout is None

    def test_stream_is_signed_over_its_first_envelope(self) -> None:
        fake = _FakeSyncClient()
        _send_sync(fake, CONNECT_STREAM, iter([ENV1, ENV2]))

        _assert_signed_over(fake.headers, ENV1)
        assert fake.body == ENV1 + ENV2

    def test_bytes_body_is_signed_over_its_first_envelope(self) -> None:
        fake = _FakeSyncClient()
        with _sync_client(fake).stream("POST", URL, content=ENV1 + ENV2):
            pass

        _assert_signed_over(fake.headers, ENV1)
        assert fake.body == ENV1 + ENV2

    def test_no_content_is_an_empty_stream(self) -> None:
        fake = _FakeSyncClient()
        with _sync_client(fake).stream("POST", URL):
            pass

        _assert_signed_over(fake.headers, b"")
        assert fake.body == b""

    def test_bytes_body_ending_inside_its_first_envelope_is_refused(self) -> None:
        fake = _FakeSyncClient()
        with pytest.raises(ConnectError) as exc, _sync_client(fake).stream("POST", URL, content=ENV1[:-1]):
            pass
        assert exc.value.code == Code.INVALID_ARGUMENT
        assert fake.events == [], "nothing is sent"
