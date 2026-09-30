"""A cancelled body source must abort the wire upload, not complete a partial request."""

from __future__ import annotations

import asyncio
import struct
from contextlib import suppress

import pyqwest
import pytest
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.signing import SigningClient


@pytest.mark.asyncio
@pytest.mark.parametrize("failure", [asyncio.CancelledError, ValueError])
async def test_source_failure_after_first_message_aborts_upload(failure) -> None:
    first = struct.pack(">BI", 0, 2) + b"m1"
    received_first = asyncio.Event()
    upload = asyncio.get_running_loop().create_future()

    async def receive(reader, writer):
        try:
            headers = await reader.readuntil(b"\r\n\r\n")
            assert b"transfer-encoding: chunked" in headers.lower()
            size = int(await reader.readline(), 16)
            body = await reader.readexactly(size)
            assert await reader.readexactly(2) == b"\r\n"
            received_first.set()
            try:
                last_chunk = await reader.readline()
            except ConnectionResetError:
                last_chunk = b""
            completed = last_chunk == b"0\r\n"
            if completed:
                # Answer a normally completed upload so a broken dependency fails the test promptly.
                writer.write(b"HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                await writer.drain()
            upload.set_result((body, completed))
        except Exception as error:
            upload.set_exception(error)
        finally:
            writer.close()
            with suppress(ConnectionResetError):
                await writer.wait_closed()

    async def source():
        yield first
        await received_first.wait()
        raise failure("source failed")

    signer = new_signer_from_hex("0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8")
    client = SigningClient(signer)
    server = await asyncio.start_server(receive, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]
    async with server, asyncio.timeout(5):
        with pytest.raises(pyqwest.WriteError):
            async with client.stream("POST", f"http://127.0.0.1:{port}/", content=source()):
                pass
        body, completed = await upload
    assert body == first
    assert not completed, "a failed source must not send the normal end-of-upload chunk"
