"""Upload and download a KYC file through KycFileService.

The caller builds the client. These helpers only run the stream. They are not
re-exported from the package root.
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

from connectrpc.code import Code
from connectrpc.errors import ConnectError
from tzero.v1.manage.kyc_sharing.file_connect import KycFileServiceClient, KycFileServiceClientSync
from tzero.v1.manage.kyc_sharing.file_pb2 import (
    DownloadFileRequest,
    DownloadFileResponse,
    UploadFileRequest,
)

from t0_provider_sdk._messages import KYC_DOWNLOAD_SHAPE

# The largest Chunk.data a helper sends: the max_len of that field.
KYC_FILE_CHUNK_MAX_BYTES = 1048576


class _Download:
    """The shape both the async and sync readers apply to each message."""

    def __init__(self) -> None:
        self.metadata: DownloadFileResponse.Metadata | None = None
        self.parts: list[bytes] = []

    def add(self, message: DownloadFileResponse) -> None:
        kind = message.WhichOneof("payload")
        if self.metadata is None:
            if kind != "metadata":
                raise ConnectError(Code.INVALID_ARGUMENT, KYC_DOWNLOAD_SHAPE)
            self.metadata = message.metadata
            return
        if kind != "chunk":
            raise ConnectError(Code.INVALID_ARGUMENT, KYC_DOWNLOAD_SHAPE)
        self.parts.append(message.chunk.data)

    def finish(self) -> tuple[DownloadFileResponse.Metadata, bytes]:
        if self.metadata is None:
            raise ConnectError(Code.INVALID_ARGUMENT, KYC_DOWNLOAD_SHAPE)
        return self.metadata, b"".join(self.parts)


def _chunks(data: bytes) -> Iterator[bytes]:
    """Each slice is one chunk. An empty file yields nothing, so no chunk is empty."""
    for offset in range(0, len(data), KYC_FILE_CHUNK_MAX_BYTES):
        yield data[offset : offset + KYC_FILE_CHUNK_MAX_BYTES]


def _upload_messages(metadata: UploadFileRequest.Metadata, data: bytes) -> Iterator[UploadFileRequest]:
    yield UploadFileRequest(metadata=metadata)
    for chunk in _chunks(data):
        yield UploadFileRequest(chunk=UploadFileRequest.Chunk(data=chunk))


async def _upload_messages_async(metadata: UploadFileRequest.Metadata, data: bytes) -> AsyncIterator[UploadFileRequest]:
    for message in _upload_messages(metadata, data):
        yield message


async def _close_async(stream: object) -> None:
    close = getattr(stream, "aclose", None)
    if close is None:
        return
    try:
        await close()
    except Exception:
        return


def _close_sync(stream: object) -> None:
    close = getattr(stream, "close", None)
    if close is None:
        return
    try:
        close()
    except Exception:
        return


async def upload_file(
    client: KycFileServiceClient,
    metadata: UploadFileRequest.Metadata,
    data: bytes,
    *,
    timeout_ms: int | None = None,
) -> int:
    """Send metadata, then chunks, and return the stored file id.

    The generator ends only after the last chunk. It does not catch errors.
    ``timeout_ms`` is the deadline of this call, in place of the stream timeout.
    """
    response = await client.upload_file(_upload_messages_async(metadata, data), timeout_ms=timeout_ms)
    return response.file_id


def upload_file_sync(
    client: KycFileServiceClientSync,
    metadata: UploadFileRequest.Metadata,
    data: bytes,
    *,
    timeout_ms: int | None = None,
) -> int:
    """The sync form of :func:`upload_file`."""
    response = client.upload_file(_upload_messages(metadata, data), timeout_ms=timeout_ms)
    return response.file_id


async def download_file(
    client: KycFileServiceClient,
    request: DownloadFileRequest,
    *,
    timeout_ms: int | None = None,
) -> tuple[DownloadFileResponse.Metadata, bytes]:
    """Return the metadata and every byte.

    A stream that is not one metadata message followed by chunks is closed and
    fails with ``kyc_download_shape``. No bytes are returned. A server error is
    the call's error. ``timeout_ms`` is the deadline of this call.
    """
    stream = client.download_file(request, timeout_ms=timeout_ms)
    try:
        downloaded = _Download()
        async for message in stream:
            downloaded.add(message)
        return downloaded.finish()
    except Exception:
        await _close_async(stream)
        raise


def download_file_sync(
    client: KycFileServiceClientSync,
    request: DownloadFileRequest,
    *,
    timeout_ms: int | None = None,
) -> tuple[DownloadFileResponse.Metadata, bytes]:
    """The sync form of :func:`download_file`."""
    stream = client.download_file(request, timeout_ms=timeout_ms)
    try:
        downloaded = _Download()
        for message in stream:
            downloaded.add(message)
        return downloaded.finish()
    except Exception:
        _close_sync(stream)
        raise
