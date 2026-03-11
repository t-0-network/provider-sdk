from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CreatePaymentIntentRequest(_message.Message):
    __slots__ = ("payment_intent_id", "currency", "amount", "merchant_id")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    MERCHANT_ID_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    currency: str
    amount: _common_pb2.Decimal
    merchant_id: int
    def __init__(self, payment_intent_id: _Optional[int] = ..., currency: _Optional[str] = ..., amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., merchant_id: _Optional[int] = ...) -> None: ...

class CreatePaymentIntentResponse(_message.Message):
    __slots__ = ("payment_methods",)
    class PaymentMethod(_message.Message):
        __slots__ = ("payment_url", "payment_method")
        PAYMENT_URL_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
        payment_url: str
        payment_method: _payment_method_pb2.PaymentMethodType
        def __init__(self, payment_url: _Optional[str] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ...) -> None: ...
    PAYMENT_METHODS_FIELD_NUMBER: _ClassVar[int]
    payment_methods: _containers.RepeatedCompositeFieldContainer[CreatePaymentIntentResponse.PaymentMethod]
    def __init__(self, payment_methods: _Optional[_Iterable[_Union[CreatePaymentIntentResponse.PaymentMethod, _Mapping]]] = ...) -> None: ...

class ConfirmPaymentRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_method")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_method: _payment_method_pb2.PaymentMethodType
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ...) -> None: ...

class ConfirmPaymentResponse(_message.Message):
    __slots__ = ("settlement_amount", "payout_provider_id")
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    settlement_amount: _common_pb2.Decimal
    payout_provider_id: int
    def __init__(self, settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payout_provider_id: _Optional[int] = ...) -> None: ...

class RejectPaymentIntentRequest(_message.Message):
    __slots__ = ("payment_intent_id", "reason")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    reason: str
    def __init__(self, payment_intent_id: _Optional[int] = ..., reason: _Optional[str] = ...) -> None: ...

class RejectPaymentIntentResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ConfirmPayoutRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_id")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_id: int
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_id: _Optional[int] = ...) -> None: ...

class ConfirmPayoutResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ConfirmSettlementRequest(_message.Message):
    __slots__ = ("blockchain", "tx_hash", "payment_intent_id")
    BLOCKCHAIN_FIELD_NUMBER: _ClassVar[int]
    TX_HASH_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    blockchain: _common_pb2.Blockchain
    tx_hash: str
    payment_intent_id: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, blockchain: _Optional[_Union[_common_pb2.Blockchain, str]] = ..., tx_hash: _Optional[str] = ..., payment_intent_id: _Optional[_Iterable[int]] = ...) -> None: ...

class ConfirmSettlementResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...
