from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from buf.validate import validate_pb2 as _validate_pb2
from ivms101.v1.ivms import ivms101_pb2 as _ivms101_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class GetPaymentDetailsRequest(_message.Message):
    __slots__ = ("payment_intent_id", "confirmation_code", "payment_methods", "currency", "amount", "travel_rule")
    class TravelRuleData(_message.Message):
        __slots__ = ("beneficiary", "beneficiary_provider", "payer")
        BENEFICIARY_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_PROVIDER_FIELD_NUMBER: _ClassVar[int]
        PAYER_FIELD_NUMBER: _ClassVar[int]
        beneficiary: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        beneficiary_provider: _ivms101_pb2.LegalPerson
        payer: _ivms101_pb2.Person
        def __init__(self, beneficiary: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ..., beneficiary_provider: _Optional[_Union[_ivms101_pb2.LegalPerson, _Mapping]] = ..., payer: _Optional[_Union[_ivms101_pb2.Person, _Mapping]] = ...) -> None: ...
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    CONFIRMATION_CODE_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_METHODS_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    TRAVEL_RULE_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    confirmation_code: str
    payment_methods: _containers.RepeatedScalarFieldContainer[_payment_method_pb2.PaymentMethodType]
    currency: str
    amount: _common_pb2.Decimal
    travel_rule: GetPaymentDetailsRequest.TravelRuleData
    def __init__(self, payment_intent_id: _Optional[int] = ..., confirmation_code: _Optional[str] = ..., payment_methods: _Optional[_Iterable[_Union[_payment_method_pb2.PaymentMethodType, str]]] = ..., currency: _Optional[str] = ..., amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., travel_rule: _Optional[_Union[GetPaymentDetailsRequest.TravelRuleData, _Mapping]] = ...) -> None: ...

class GetPaymentDetailsResponse(_message.Message):
    __slots__ = ("details", "rejection")
    class Details(_message.Message):
        __slots__ = ("payment_details",)
        PAYMENT_DETAILS_FIELD_NUMBER: _ClassVar[int]
        payment_details: _containers.RepeatedCompositeFieldContainer[_payment_method_pb2.PaymentDetails]
        def __init__(self, payment_details: _Optional[_Iterable[_Union[_payment_method_pb2.PaymentDetails, _Mapping]]] = ...) -> None: ...
    class Rejection(_message.Message):
        __slots__ = ("reason",)
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: str
        def __init__(self, reason: _Optional[str] = ...) -> None: ...
    DETAILS_FIELD_NUMBER: _ClassVar[int]
    REJECTION_FIELD_NUMBER: _ClassVar[int]
    details: GetPaymentDetailsResponse.Details
    rejection: GetPaymentDetailsResponse.Rejection
    def __init__(self, details: _Optional[_Union[GetPaymentDetailsResponse.Details, _Mapping]] = ..., rejection: _Optional[_Union[GetPaymentDetailsResponse.Rejection, _Mapping]] = ...) -> None: ...
