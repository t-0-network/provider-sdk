import datetime

from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from tzero.v1.common import payment_receipt_pb2 as _payment_receipt_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CreatePaymentIntentRequest(_message.Message):
    __slots__ = ("payment_reference", "pay_in_currency", "pay_in_amount", "pay_out_currency", "pay_out_details")
    PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_DETAILS_FIELD_NUMBER: _ClassVar[int]
    payment_reference: str
    pay_in_currency: str
    pay_in_amount: _common_pb2.Decimal
    pay_out_currency: str
    pay_out_details: _payment_method_pb2.PaymentDetails
    def __init__(self, payment_reference: _Optional[str] = ..., pay_in_currency: _Optional[str] = ..., pay_in_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_out_currency: _Optional[str] = ..., pay_out_details: _Optional[_Union[_payment_method_pb2.PaymentDetails, _Mapping]] = ...) -> None: ...

class CreatePaymentIntentResponse(_message.Message):
    __slots__ = ("payment_intent_id", "pay_in_payment_methods")
    class PaymentMethod(_message.Message):
        __slots__ = ("payment_url", "provider_id", "payment_method")
        PAYMENT_URL_FIELD_NUMBER: _ClassVar[int]
        PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
        payment_url: str
        provider_id: int
        payment_method: _payment_method_pb2.PaymentMethodType
        def __init__(self, payment_url: _Optional[str] = ..., provider_id: _Optional[int] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ...) -> None: ...
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_PAYMENT_METHODS_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    pay_in_payment_methods: _containers.RepeatedCompositeFieldContainer[CreatePaymentIntentResponse.PaymentMethod]
    def __init__(self, payment_intent_id: _Optional[int] = ..., pay_in_payment_methods: _Optional[_Iterable[_Union[CreatePaymentIntentResponse.PaymentMethod, _Mapping]]] = ...) -> None: ...

class GetQuoteRequest(_message.Message):
    __slots__ = ("pay_in_currency", "pay_in_amount", "pay_out_currency", "pay_in_payment_method", "pay_out_payment_method")
    PAY_IN_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    pay_in_currency: str
    pay_in_amount: _common_pb2.Decimal
    pay_out_currency: str
    pay_in_payment_method: _payment_method_pb2.PaymentMethodType
    pay_out_payment_method: _payment_method_pb2.PaymentMethodType
    def __init__(self, pay_in_currency: _Optional[str] = ..., pay_in_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_out_currency: _Optional[str] = ..., pay_in_payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., pay_out_payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ...) -> None: ...

class GetQuoteResponse(_message.Message):
    __slots__ = ("quote", "not_found")
    class Quote(_message.Message):
        __slots__ = ("rate", "expiration")
        RATE_FIELD_NUMBER: _ClassVar[int]
        EXPIRATION_FIELD_NUMBER: _ClassVar[int]
        rate: _common_pb2.Decimal
        expiration: _timestamp_pb2.Timestamp
        def __init__(self, rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    class NotFound(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    QUOTE_FIELD_NUMBER: _ClassVar[int]
    NOT_FOUND_FIELD_NUMBER: _ClassVar[int]
    quote: GetQuoteResponse.Quote
    not_found: GetQuoteResponse.NotFound
    def __init__(self, quote: _Optional[_Union[GetQuoteResponse.Quote, _Mapping]] = ..., not_found: _Optional[_Union[GetQuoteResponse.NotFound, _Mapping]] = ...) -> None: ...

class ConfirmPayInRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_reference", "payment_method")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_reference: str
    payment_method: _payment_method_pb2.PaymentMethodType
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_reference: _Optional[str] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ...) -> None: ...

class ConfirmPayInResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ConfirmPaymentRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_reference", "payment_method", "pay_out_amount", "receipt")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    RECEIPT_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_reference: str
    payment_method: _payment_method_pb2.PaymentMethodType
    pay_out_amount: _common_pb2.Decimal
    receipt: _payment_receipt_pb2.PaymentReceipt
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_reference: _Optional[str] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., receipt: _Optional[_Union[_payment_receipt_pb2.PaymentReceipt, _Mapping]] = ...) -> None: ...

class ConfirmPaymentResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class RejectPaymentIntentRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_reference", "reason")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_reference: str
    reason: str
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_reference: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...

class RejectPaymentIntentResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...
