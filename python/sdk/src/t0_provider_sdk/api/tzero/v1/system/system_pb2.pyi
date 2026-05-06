import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SdkEcosystem(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    SDK_ECOSYSTEM_UNSPECIFIED: _ClassVar[SdkEcosystem]
    SDK_ECOSYSTEM_GO: _ClassVar[SdkEcosystem]
    SDK_ECOSYSTEM_NODE: _ClassVar[SdkEcosystem]
    SDK_ECOSYSTEM_PYTHON: _ClassVar[SdkEcosystem]
    SDK_ECOSYSTEM_JAVA: _ClassVar[SdkEcosystem]
    SDK_ECOSYSTEM_CSHARP: _ClassVar[SdkEcosystem]
SDK_ECOSYSTEM_UNSPECIFIED: SdkEcosystem
SDK_ECOSYSTEM_GO: SdkEcosystem
SDK_ECOSYSTEM_NODE: SdkEcosystem
SDK_ECOSYSTEM_PYTHON: SdkEcosystem
SDK_ECOSYSTEM_JAVA: SdkEcosystem
SDK_ECOSYSTEM_CSHARP: SdkEcosystem

class HealthRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class HealthResponse(_message.Message):
    __slots__ = ("services", "current_time", "sdk_version", "sdk_ecosystem")
    SERVICES_FIELD_NUMBER: _ClassVar[int]
    CURRENT_TIME_FIELD_NUMBER: _ClassVar[int]
    SDK_VERSION_FIELD_NUMBER: _ClassVar[int]
    SDK_ECOSYSTEM_FIELD_NUMBER: _ClassVar[int]
    services: _containers.RepeatedScalarFieldContainer[str]
    current_time: _timestamp_pb2.Timestamp
    sdk_version: str
    sdk_ecosystem: SdkEcosystem
    def __init__(self, services: _Optional[_Iterable[str]] = ..., current_time: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., sdk_version: _Optional[str] = ..., sdk_ecosystem: _Optional[_Union[SdkEcosystem, str]] = ...) -> None: ...
