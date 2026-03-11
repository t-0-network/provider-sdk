from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class Blockchain(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    BLOCKCHAIN_UNSPECIFIED: _ClassVar[Blockchain]
    BLOCKCHAIN_BSC: _ClassVar[Blockchain]
    BLOCKCHAIN_ETH: _ClassVar[Blockchain]
    BLOCKCHAIN_TRON: _ClassVar[Blockchain]

class Stablecoin(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    STABLECOIN_UNSPECIFIED: _ClassVar[Stablecoin]
    STABLECOIN_USDT: _ClassVar[Stablecoin]
BLOCKCHAIN_UNSPECIFIED: Blockchain
BLOCKCHAIN_BSC: Blockchain
BLOCKCHAIN_ETH: Blockchain
BLOCKCHAIN_TRON: Blockchain
STABLECOIN_UNSPECIFIED: Stablecoin
STABLECOIN_USDT: Stablecoin

class Decimal(_message.Message):
    __slots__ = ("unscaled", "exponent")
    UNSCALED_FIELD_NUMBER: _ClassVar[int]
    EXPONENT_FIELD_NUMBER: _ClassVar[int]
    unscaled: int
    exponent: int
    def __init__(self, unscaled: _Optional[int] = ..., exponent: _Optional[int] = ...) -> None: ...
