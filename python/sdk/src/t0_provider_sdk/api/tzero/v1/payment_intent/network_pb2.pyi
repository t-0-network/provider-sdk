import datetime

from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from ivms101.v1.ivms import ivms101_pb2 as _ivms101_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class UpdateQuoteRequest(_message.Message):
    __slots__ = ("payment_intent_quotes",)
    class Quote(_message.Message):
        __slots__ = ("currency", "payment_method", "bands", "expiration", "timestamp")
        class Band(_message.Message):
            __slots__ = ("client_quote_id", "max_amount", "rate", "fix")
            CLIENT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
            MAX_AMOUNT_FIELD_NUMBER: _ClassVar[int]
            RATE_FIELD_NUMBER: _ClassVar[int]
            FIX_FIELD_NUMBER: _ClassVar[int]
            client_quote_id: str
            max_amount: _common_pb2.Decimal
            rate: _common_pb2.Decimal
            fix: _common_pb2.Decimal
            def __init__(self, client_quote_id: _Optional[str] = ..., max_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., fix: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
        CURRENCY_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
        BANDS_FIELD_NUMBER: _ClassVar[int]
        EXPIRATION_FIELD_NUMBER: _ClassVar[int]
        TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
        currency: str
        payment_method: _payment_method_pb2.PaymentMethodType
        bands: _containers.RepeatedCompositeFieldContainer[UpdateQuoteRequest.Quote.Band]
        expiration: _timestamp_pb2.Timestamp
        timestamp: _timestamp_pb2.Timestamp
        def __init__(self, currency: _Optional[str] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., bands: _Optional[_Iterable[_Union[UpdateQuoteRequest.Quote.Band, _Mapping]]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., timestamp: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    PAYMENT_INTENT_QUOTES_FIELD_NUMBER: _ClassVar[int]
    payment_intent_quotes: _containers.RepeatedCompositeFieldContainer[UpdateQuoteRequest.Quote]
    def __init__(self, payment_intent_quotes: _Optional[_Iterable[_Union[UpdateQuoteRequest.Quote, _Mapping]]] = ...) -> None: ...

class UpdateQuoteResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class GetQuoteRequest(_message.Message):
    __slots__ = ("currency", "amount", "pay_in_provider_ids")
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_PROVIDER_IDS_FIELD_NUMBER: _ClassVar[int]
    currency: str
    amount: _common_pb2.Decimal
    pay_in_provider_ids: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, currency: _Optional[str] = ..., amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_in_provider_ids: _Optional[_Iterable[int]] = ...) -> None: ...

class GetQuoteResponse(_message.Message):
    __slots__ = ("success", "quote_not_found")
    class Success(_message.Message):
        __slots__ = ("best_quotes", "all_quotes")
        class IndicativeQuote(_message.Message):
            __slots__ = ("payment_method", "provider_id", "indicative_rate", "indicative_fix")
            PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
            PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
            INDICATIVE_RATE_FIELD_NUMBER: _ClassVar[int]
            INDICATIVE_FIX_FIELD_NUMBER: _ClassVar[int]
            payment_method: _payment_method_pb2.PaymentMethodType
            provider_id: int
            indicative_rate: _common_pb2.Decimal
            indicative_fix: _common_pb2.Decimal
            def __init__(self, payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., provider_id: _Optional[int] = ..., indicative_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., indicative_fix: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
        BEST_QUOTES_FIELD_NUMBER: _ClassVar[int]
        ALL_QUOTES_FIELD_NUMBER: _ClassVar[int]
        best_quotes: _containers.RepeatedCompositeFieldContainer[GetQuoteResponse.Success.IndicativeQuote]
        all_quotes: _containers.RepeatedCompositeFieldContainer[GetQuoteResponse.Success.IndicativeQuote]
        def __init__(self, best_quotes: _Optional[_Iterable[_Union[GetQuoteResponse.Success.IndicativeQuote, _Mapping]]] = ..., all_quotes: _Optional[_Iterable[_Union[GetQuoteResponse.Success.IndicativeQuote, _Mapping]]] = ...) -> None: ...
    class QuoteNotFound(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    QUOTE_NOT_FOUND_FIELD_NUMBER: _ClassVar[int]
    success: GetQuoteResponse.Success
    quote_not_found: GetQuoteResponse.QuoteNotFound
    def __init__(self, success: _Optional[_Union[GetQuoteResponse.Success, _Mapping]] = ..., quote_not_found: _Optional[_Union[GetQuoteResponse.QuoteNotFound, _Mapping]] = ...) -> None: ...

class PaymentIntentPayInDetails(_message.Message):
    __slots__ = ("payment_method", "provider_id", "payment_details", "indicative_rate", "indicative_fix")
    PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_DETAILS_FIELD_NUMBER: _ClassVar[int]
    INDICATIVE_RATE_FIELD_NUMBER: _ClassVar[int]
    INDICATIVE_FIX_FIELD_NUMBER: _ClassVar[int]
    payment_method: _payment_method_pb2.PaymentMethodType
    provider_id: int
    payment_details: _payment_method_pb2.PaymentDetails
    indicative_rate: _common_pb2.Decimal
    indicative_fix: _common_pb2.Decimal
    def __init__(self, payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., provider_id: _Optional[int] = ..., payment_details: _Optional[_Union[_payment_method_pb2.PaymentDetails, _Mapping]] = ..., indicative_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., indicative_fix: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...

class CreatePaymentIntentRequest(_message.Message):
    __slots__ = ("external_reference", "currency", "amount", "travel_rule_data", "pay_in_provider_ids")
    class TravelRuleData(_message.Message):
        __slots__ = ("beneficiary", "payer")
        BENEFICIARY_FIELD_NUMBER: _ClassVar[int]
        PAYER_FIELD_NUMBER: _ClassVar[int]
        beneficiary: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        payer: _ivms101_pb2.Person
        def __init__(self, beneficiary: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ..., payer: _Optional[_Union[_ivms101_pb2.Person, _Mapping]] = ...) -> None: ...
    EXTERNAL_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    TRAVEL_RULE_DATA_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_PROVIDER_IDS_FIELD_NUMBER: _ClassVar[int]
    external_reference: str
    currency: str
    amount: _common_pb2.Decimal
    travel_rule_data: CreatePaymentIntentRequest.TravelRuleData
    pay_in_provider_ids: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, external_reference: _Optional[str] = ..., currency: _Optional[str] = ..., amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., travel_rule_data: _Optional[_Union[CreatePaymentIntentRequest.TravelRuleData, _Mapping]] = ..., pay_in_provider_ids: _Optional[_Iterable[int]] = ...) -> None: ...

class CreatePaymentIntentResponse(_message.Message):
    __slots__ = ("success", "failure")
    class Success(_message.Message):
        __slots__ = ("payment_intent_id", "pay_in_details")
        PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
        PAY_IN_DETAILS_FIELD_NUMBER: _ClassVar[int]
        payment_intent_id: int
        pay_in_details: _containers.RepeatedCompositeFieldContainer[PaymentIntentPayInDetails]
        def __init__(self, payment_intent_id: _Optional[int] = ..., pay_in_details: _Optional[_Iterable[_Union[PaymentIntentPayInDetails, _Mapping]]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            FAILURE_REASON_UNSPECIFIED: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            FAILURE_REASON_QUOTE_NOT_FOUND: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            FAILURE_REASON_REJECTED: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
        FAILURE_REASON_UNSPECIFIED: CreatePaymentIntentResponse.Failure.Reason
        FAILURE_REASON_QUOTE_NOT_FOUND: CreatePaymentIntentResponse.Failure.Reason
        FAILURE_REASON_REJECTED: CreatePaymentIntentResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: CreatePaymentIntentResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[CreatePaymentIntentResponse.Failure.Reason, str]] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    success: CreatePaymentIntentResponse.Success
    failure: CreatePaymentIntentResponse.Failure
    def __init__(self, success: _Optional[_Union[CreatePaymentIntentResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[CreatePaymentIntentResponse.Failure, _Mapping]] = ...) -> None: ...

class ConfirmFundsReceivedRequest(_message.Message):
    __slots__ = ("payment_intent_id", "confirmation_code", "payment_method", "transaction_reference", "originator_provider_legal_entity_id")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    CONFIRMATION_CODE_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
    TRANSACTION_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    ORIGINATOR_PROVIDER_LEGAL_ENTITY_ID_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    confirmation_code: str
    payment_method: _payment_method_pb2.PaymentMethodType
    transaction_reference: str
    originator_provider_legal_entity_id: int
    def __init__(self, payment_intent_id: _Optional[int] = ..., confirmation_code: _Optional[str] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., transaction_reference: _Optional[str] = ..., originator_provider_legal_entity_id: _Optional[int] = ...) -> None: ...

class ConfirmFundsReceivedResponse(_message.Message):
    __slots__ = ("accept", "reject")
    class Accept(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Reject(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REJECT_REASON_UNSPECIFIED: _ClassVar[ConfirmFundsReceivedResponse.Reject.Reason]
            REJECT_REASON_CONFIRMATION_CODE_MISMATCH: _ClassVar[ConfirmFundsReceivedResponse.Reject.Reason]
            REJECT_REASON_NO_ACTIVE_QUOTE: _ClassVar[ConfirmFundsReceivedResponse.Reject.Reason]
            REJECT_REASON_PROVIDER_NOT_ALLOWED: _ClassVar[ConfirmFundsReceivedResponse.Reject.Reason]
            REJECT_REASON_AMOUNT_TOO_SMALL: _ClassVar[ConfirmFundsReceivedResponse.Reject.Reason]
        REJECT_REASON_UNSPECIFIED: ConfirmFundsReceivedResponse.Reject.Reason
        REJECT_REASON_CONFIRMATION_CODE_MISMATCH: ConfirmFundsReceivedResponse.Reject.Reason
        REJECT_REASON_NO_ACTIVE_QUOTE: ConfirmFundsReceivedResponse.Reject.Reason
        REJECT_REASON_PROVIDER_NOT_ALLOWED: ConfirmFundsReceivedResponse.Reject.Reason
        REJECT_REASON_AMOUNT_TOO_SMALL: ConfirmFundsReceivedResponse.Reject.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: ConfirmFundsReceivedResponse.Reject.Reason
        def __init__(self, reason: _Optional[_Union[ConfirmFundsReceivedResponse.Reject.Reason, str]] = ...) -> None: ...
    ACCEPT_FIELD_NUMBER: _ClassVar[int]
    REJECT_FIELD_NUMBER: _ClassVar[int]
    accept: ConfirmFundsReceivedResponse.Accept
    reject: ConfirmFundsReceivedResponse.Reject
    def __init__(self, accept: _Optional[_Union[ConfirmFundsReceivedResponse.Accept, _Mapping]] = ..., reject: _Optional[_Union[ConfirmFundsReceivedResponse.Reject, _Mapping]] = ...) -> None: ...
