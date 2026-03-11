from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PaymentReceipt(_message.Message):
    __slots__ = ("sepa", "swift", "pix", "fps", "nip")
    class Nip(_message.Message):
        __slots__ = ("session_id",)
        SESSION_ID_FIELD_NUMBER: _ClassVar[int]
        session_id: str
        def __init__(self, session_id: _Optional[str] = ...) -> None: ...
    class Sepa(_message.Message):
        __slots__ = ("banking_transaction_reference_id",)
        BANKING_TRANSACTION_REFERENCE_ID_FIELD_NUMBER: _ClassVar[int]
        banking_transaction_reference_id: str
        def __init__(self, banking_transaction_reference_id: _Optional[str] = ...) -> None: ...
    class Swift(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Pix(_message.Message):
        __slots__ = ("e2e_id",)
        E2E_ID_FIELD_NUMBER: _ClassVar[int]
        e2e_id: str
        def __init__(self, e2e_id: _Optional[str] = ...) -> None: ...
    class Fps(_message.Message):
        __slots__ = ("transaction_reference_id",)
        TRANSACTION_REFERENCE_ID_FIELD_NUMBER: _ClassVar[int]
        transaction_reference_id: str
        def __init__(self, transaction_reference_id: _Optional[str] = ...) -> None: ...
    SEPA_FIELD_NUMBER: _ClassVar[int]
    SWIFT_FIELD_NUMBER: _ClassVar[int]
    PIX_FIELD_NUMBER: _ClassVar[int]
    FPS_FIELD_NUMBER: _ClassVar[int]
    NIP_FIELD_NUMBER: _ClassVar[int]
    sepa: PaymentReceipt.Sepa
    swift: PaymentReceipt.Swift
    pix: PaymentReceipt.Pix
    fps: PaymentReceipt.Fps
    nip: PaymentReceipt.Nip
    def __init__(self, sepa: _Optional[_Union[PaymentReceipt.Sepa, _Mapping]] = ..., swift: _Optional[_Union[PaymentReceipt.Swift, _Mapping]] = ..., pix: _Optional[_Union[PaymentReceipt.Pix, _Mapping]] = ..., fps: _Optional[_Union[PaymentReceipt.Fps, _Mapping]] = ..., nip: _Optional[_Union[PaymentReceipt.Nip, _Mapping]] = ...) -> None: ...
