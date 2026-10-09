"""KYC file helpers against the Go helper's in-memory KycFileService.

Async and sync, Connect and gRPC, in one parametrized test. Build the helper first:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import asyncio
import os
import socket
import subprocess
import time
from pathlib import Path
from typing import TYPE_CHECKING

import pytest
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from t0_provider_sdk.kyc_sharing import download_file, download_file_sync, upload_file, upload_file_sync
from t0_provider_sdk.network import Protocol, new_service_client, new_service_client_sync
from tzero.v1.manage.kyc_sharing.file_connect import KycFileServiceClient, KycFileServiceClientSync
from tzero.v1.manage.kyc_sharing.file_pb2 import DownloadFileRequest, UploadFileRequest

if TYPE_CHECKING:
    from collections.abc import Iterator

GO_HELPER = Path(__file__).resolve().parents[3] / "cross_test" / "go_helper" / "go_helper"
CLIENT_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
CLIENT_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"
SHAPE = "download stream must be one metadata message followed by chunks"


def _go_available() -> bool:
    return GO_HELPER.exists() and os.access(GO_HELPER, os.X_OK)


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _wait_for_port(port: int, timeout: float = 10.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise TimeoutError(f"Port {port} not ready after {timeout}s")


def _patterned(n: int) -> bytes:
    return bytes(i & 0xFF for i in range(n))


if not _go_available() and os.environ.get("CI"):
    raise RuntimeError(f"Go helper binary required in CI but not found at {GO_HELPER}")

pytestmark = pytest.mark.skipif(
    not _go_available(),
    reason=f"Go helper binary not found at {GO_HELPER}. Build with: cd cross_test/go_helper && go build -o go_helper .",
)


@pytest.fixture(scope="module")
def go_server() -> Iterator[str]:
    port = _free_port()
    proc = subprocess.Popen(
        [str(GO_HELPER), "serve", str(port), CLIENT_PUBLIC_KEY],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        _wait_for_port(port)
        yield f"http://127.0.0.1:{port}"
    finally:
        proc.terminate()
        proc.wait(timeout=5)


def _metadata() -> UploadFileRequest.Metadata:
    return UploadFileRequest.Metadata(
        payout_provider_id=7,
        client_id="applicant-1",
        file_name="passport.pdf",
        declared_content_type="application/pdf",
        upload_id="upload-1",
    )


def _download(file_id: int, client_id: str = "applicant-1") -> DownloadFileRequest:
    return DownloadFileRequest(
        file_id=file_id,
        payout_requester_id=3,
        payout_provider_id=7,
        client_id=client_id,
    )


async def _round_trip_async(base_url: str, protocol: str) -> None:
    client = new_service_client(
        CLIENT_PRIVATE_KEY, KycFileServiceClient, base_url=base_url, protocol=Protocol(protocol), stream_timeout=30
    )
    data = _patterned(2621440)
    file_id = await upload_file(client, _metadata(), data, timeout_ms=30_000)
    assert file_id >= 1
    metadata, got = await download_file(client, _download(file_id), timeout_ms=30_000)
    assert metadata.content_type == "application/pdf"
    assert metadata.file_name == "passport.pdf"
    assert got == data


def _round_trip_sync(base_url: str, protocol: str) -> None:
    client = new_service_client_sync(
        CLIENT_PRIVATE_KEY, KycFileServiceClientSync, base_url=base_url, protocol=Protocol(protocol), stream_timeout=30
    )
    data = _patterned(2621440)
    file_id = upload_file_sync(client, _metadata(), data, timeout_ms=30_000)
    assert file_id >= 1
    metadata, got = download_file_sync(client, _download(file_id), timeout_ms=30_000)
    assert metadata.content_type == "application/pdf"
    assert metadata.file_name == "passport.pdf"
    assert got == data


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
@pytest.mark.parametrize("sync", [False, True], ids=["async", "sync"])
def test_kyc_file_round_trip(go_server: str, protocol: str, sync: bool) -> None:
    if sync:
        _round_trip_sync(go_server, protocol)
    else:
        asyncio.run(_round_trip_async(go_server, protocol))


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
@pytest.mark.parametrize("sync", [False, True], ids=["async", "sync"])
def test_kyc_file_unknown_id(go_server: str, protocol: str, sync: bool) -> None:
    request = _download(42)

    def check(err: ConnectError) -> None:
        assert err.code == Code.NOT_FOUND

    if sync:
        client = new_service_client_sync(
            CLIENT_PRIVATE_KEY, KycFileServiceClientSync, base_url=go_server, protocol=Protocol(protocol)
        )
        with pytest.raises(ConnectError) as exc:
            download_file_sync(client, request, timeout_ms=30_000)
        check(exc.value)
    else:
        client = new_service_client(
            CLIENT_PRIVATE_KEY, KycFileServiceClient, base_url=go_server, protocol=Protocol(protocol)
        )

        async def call() -> None:
            await download_file(client, request, timeout_ms=30_000)

        with pytest.raises(ConnectError) as exc:
            asyncio.run(call())
        check(exc.value)


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
@pytest.mark.parametrize("sync", [False, True], ids=["async", "sync"])
def test_kyc_file_download_shape(go_server: str, protocol: str, sync: bool) -> None:
    request = _download(9223372036854775807)

    def check(err: ConnectError) -> None:
        assert err.code == Code.INVALID_ARGUMENT
        assert err.message == SHAPE

    if sync:
        client = new_service_client_sync(
            CLIENT_PRIVATE_KEY, KycFileServiceClientSync, base_url=go_server, protocol=Protocol(protocol)
        )
        with pytest.raises(ConnectError) as exc:
            download_file_sync(client, request, timeout_ms=30_000)
        check(exc.value)
    else:
        client = new_service_client(
            CLIENT_PRIVATE_KEY, KycFileServiceClient, base_url=go_server, protocol=Protocol(protocol)
        )

        async def call() -> None:
            await download_file(client, request, timeout_ms=30_000)

        with pytest.raises(ConnectError) as exc:
            asyncio.run(call())
        check(exc.value)


@pytest.mark.parametrize("protocol", ["connect", "grpc"])
@pytest.mark.parametrize("sync", [False, True], ids=["async", "sync"])
def test_kyc_file_permission_denied(go_server: str, protocol: str, sync: bool) -> None:
    metadata = UploadFileRequest.Metadata(payout_provider_id=7, client_id="kyc-file-permission-denied")
    data = _patterned(8388608)

    def check(err: ConnectError) -> None:
        assert err.code == Code.PERMISSION_DENIED

    if sync:
        client = new_service_client_sync(
            CLIENT_PRIVATE_KEY, KycFileServiceClientSync, base_url=go_server, protocol=Protocol(protocol)
        )
        with pytest.raises(ConnectError) as exc:
            upload_file_sync(client, metadata, data, timeout_ms=30_000)
        check(exc.value)
    else:
        client = new_service_client(
            CLIENT_PRIVATE_KEY, KycFileServiceClient, base_url=go_server, protocol=Protocol(protocol)
        )

        async def call() -> None:
            await upload_file(client, metadata, data, timeout_ms=30_000)

        with pytest.raises(ConnectError) as exc:
            asyncio.run(call())
        check(exc.value)
