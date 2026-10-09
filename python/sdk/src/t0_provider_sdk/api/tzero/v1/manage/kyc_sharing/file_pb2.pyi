from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class UploadFileRequest(_message.Message):
    __slots__ = ("metadata", "chunk")
    class Metadata(_message.Message):
        __slots__ = ("payout_provider_id", "client_id", "file_name", "declared_content_type", "upload_id")
        PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
        FILE_NAME_FIELD_NUMBER: _ClassVar[int]
        DECLARED_CONTENT_TYPE_FIELD_NUMBER: _ClassVar[int]
        UPLOAD_ID_FIELD_NUMBER: _ClassVar[int]
        payout_provider_id: int
        client_id: str
        file_name: str
        declared_content_type: str
        upload_id: str
        def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ..., file_name: _Optional[str] = ..., declared_content_type: _Optional[str] = ..., upload_id: _Optional[str] = ...) -> None: ...
    class Chunk(_message.Message):
        __slots__ = ("data",)
        DATA_FIELD_NUMBER: _ClassVar[int]
        data: bytes
        def __init__(self, data: _Optional[bytes] = ...) -> None: ...
    METADATA_FIELD_NUMBER: _ClassVar[int]
    CHUNK_FIELD_NUMBER: _ClassVar[int]
    metadata: UploadFileRequest.Metadata
    chunk: UploadFileRequest.Chunk
    def __init__(self, metadata: _Optional[_Union[UploadFileRequest.Metadata, _Mapping]] = ..., chunk: _Optional[_Union[UploadFileRequest.Chunk, _Mapping]] = ...) -> None: ...

class UploadFileResponse(_message.Message):
    __slots__ = ("file_id",)
    FILE_ID_FIELD_NUMBER: _ClassVar[int]
    file_id: int
    def __init__(self, file_id: _Optional[int] = ...) -> None: ...

class DownloadFileRequest(_message.Message):
    __slots__ = ("file_id", "payout_requester_id", "payout_provider_id", "client_id")
    FILE_ID_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    file_id: int
    payout_requester_id: int
    payout_provider_id: int
    client_id: str
    def __init__(self, file_id: _Optional[int] = ..., payout_requester_id: _Optional[int] = ..., payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class DownloadFileResponse(_message.Message):
    __slots__ = ("metadata", "chunk")
    class Metadata(_message.Message):
        __slots__ = ("content_type", "file_name")
        CONTENT_TYPE_FIELD_NUMBER: _ClassVar[int]
        FILE_NAME_FIELD_NUMBER: _ClassVar[int]
        content_type: str
        file_name: str
        def __init__(self, content_type: _Optional[str] = ..., file_name: _Optional[str] = ...) -> None: ...
    class Chunk(_message.Message):
        __slots__ = ("data",)
        DATA_FIELD_NUMBER: _ClassVar[int]
        data: bytes
        def __init__(self, data: _Optional[bytes] = ...) -> None: ...
    METADATA_FIELD_NUMBER: _ClassVar[int]
    CHUNK_FIELD_NUMBER: _ClassVar[int]
    metadata: DownloadFileResponse.Metadata
    chunk: DownloadFileResponse.Chunk
    def __init__(self, metadata: _Optional[_Union[DownloadFileResponse.Metadata, _Mapping]] = ..., chunk: _Optional[_Union[DownloadFileResponse.Chunk, _Mapping]] = ...) -> None: ...
