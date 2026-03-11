from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PaymentIntentUpdateRequest(_message.Message):
    __slots__ = ("payment_intent_id", "funds_received")
    class FundsReceived(_message.Message):
        __slots__ = ("settlement_amount", "rate", "payment_amount", "payment_method", "transaction_reference")
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        RATE_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
        TRANSACTION_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        settlement_amount: _common_pb2.Decimal
        rate: _common_pb2.Decimal
        payment_amount: _common_pb2.Decimal
        payment_method: _payment_method_pb2.PaymentMethodType
        transaction_reference: str
        def __init__(self, settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payment_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., transaction_reference: _Optional[str] = ...) -> None: ...
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    FUNDS_RECEIVED_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    funds_received: PaymentIntentUpdateRequest.FundsReceived
    def __init__(self, payment_intent_id: _Optional[int] = ..., funds_received: _Optional[_Union[PaymentIntentUpdateRequest.FundsReceived, _Mapping]] = ...) -> None: ...

class PaymentIntentUpdateResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...
