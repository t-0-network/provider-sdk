import datetime

from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from tzero.v1.common import payment_receipt_pb2 as _payment_receipt_pb2
from ivms101.v1.ivms import ivms101_pb2 as _ivms101_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class QuoteType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    QUOTE_TYPE_UNSPECIFIED: _ClassVar[QuoteType]
    QUOTE_TYPE_REALTIME: _ClassVar[QuoteType]
QUOTE_TYPE_UNSPECIFIED: QuoteType
QUOTE_TYPE_REALTIME: QuoteType

class UpdateQuoteRequest(_message.Message):
    __slots__ = ("pay_out", "pay_in")
    class Quote(_message.Message):
        __slots__ = ("currency", "quote_type", "payment_method", "bands", "expiration", "timestamp")
        class Band(_message.Message):
            __slots__ = ("client_quote_id", "max_amount", "rate")
            CLIENT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
            MAX_AMOUNT_FIELD_NUMBER: _ClassVar[int]
            RATE_FIELD_NUMBER: _ClassVar[int]
            client_quote_id: str
            max_amount: _common_pb2.Decimal
            rate: _common_pb2.Decimal
            def __init__(self, client_quote_id: _Optional[str] = ..., max_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
        CURRENCY_FIELD_NUMBER: _ClassVar[int]
        QUOTE_TYPE_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
        BANDS_FIELD_NUMBER: _ClassVar[int]
        EXPIRATION_FIELD_NUMBER: _ClassVar[int]
        TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
        currency: str
        quote_type: QuoteType
        payment_method: _payment_method_pb2.PaymentMethodType
        bands: _containers.RepeatedCompositeFieldContainer[UpdateQuoteRequest.Quote.Band]
        expiration: _timestamp_pb2.Timestamp
        timestamp: _timestamp_pb2.Timestamp
        def __init__(self, currency: _Optional[str] = ..., quote_type: _Optional[_Union[QuoteType, str]] = ..., payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., bands: _Optional[_Iterable[_Union[UpdateQuoteRequest.Quote.Band, _Mapping]]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., timestamp: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    PAY_OUT_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_FIELD_NUMBER: _ClassVar[int]
    pay_out: _containers.RepeatedCompositeFieldContainer[UpdateQuoteRequest.Quote]
    pay_in: _containers.RepeatedCompositeFieldContainer[UpdateQuoteRequest.Quote]
    def __init__(self, pay_out: _Optional[_Iterable[_Union[UpdateQuoteRequest.Quote, _Mapping]]] = ..., pay_in: _Optional[_Iterable[_Union[UpdateQuoteRequest.Quote, _Mapping]]] = ...) -> None: ...

class UpdateQuoteResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class GetQuoteRequest(_message.Message):
    __slots__ = ("amount", "pay_out_currency", "pay_out_method", "quote_type")
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_METHOD_FIELD_NUMBER: _ClassVar[int]
    QUOTE_TYPE_FIELD_NUMBER: _ClassVar[int]
    amount: PaymentAmount
    pay_out_currency: str
    pay_out_method: _payment_method_pb2.PaymentMethodType
    quote_type: QuoteType
    def __init__(self, amount: _Optional[_Union[PaymentAmount, _Mapping]] = ..., pay_out_currency: _Optional[str] = ..., pay_out_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., quote_type: _Optional[_Union[QuoteType, str]] = ...) -> None: ...

class GetQuoteResponse(_message.Message):
    __slots__ = ("success", "failure", "all_quotes")
    class Success(_message.Message):
        __slots__ = ("rate", "expiration", "quote_id", "pay_out_amount", "settlement_amount")
        RATE_FIELD_NUMBER: _ClassVar[int]
        EXPIRATION_FIELD_NUMBER: _ClassVar[int]
        QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
        PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        rate: _common_pb2.Decimal
        expiration: _timestamp_pb2.Timestamp
        quote_id: QuoteId
        pay_out_amount: _common_pb2.Decimal
        settlement_amount: _common_pb2.Decimal
        def __init__(self, rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., quote_id: _Optional[_Union[QuoteId, _Mapping]] = ..., pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[GetQuoteResponse.Failure.Reason]
            REASON_QUOTE_NOT_FOUND: _ClassVar[GetQuoteResponse.Failure.Reason]
        REASON_UNSPECIFIED: GetQuoteResponse.Failure.Reason
        REASON_QUOTE_NOT_FOUND: GetQuoteResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: GetQuoteResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[GetQuoteResponse.Failure.Reason, str]] = ...) -> None: ...
    class ProviderQuote(_message.Message):
        __slots__ = ("quote_id", "rate", "expiration", "pay_out_amount", "settlement", "executable")
        class Settlement(_message.Message):
            __slots__ = ("amount", "credit_limit", "total_used", "prefunding_amount")
            AMOUNT_FIELD_NUMBER: _ClassVar[int]
            CREDIT_LIMIT_FIELD_NUMBER: _ClassVar[int]
            TOTAL_USED_FIELD_NUMBER: _ClassVar[int]
            PREFUNDING_AMOUNT_FIELD_NUMBER: _ClassVar[int]
            amount: _common_pb2.Decimal
            credit_limit: _common_pb2.Decimal
            total_used: _common_pb2.Decimal
            prefunding_amount: _common_pb2.Decimal
            def __init__(self, amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., credit_limit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., total_used: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., prefunding_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
        QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
        RATE_FIELD_NUMBER: _ClassVar[int]
        EXPIRATION_FIELD_NUMBER: _ClassVar[int]
        PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
        EXECUTABLE_FIELD_NUMBER: _ClassVar[int]
        quote_id: QuoteId
        rate: _common_pb2.Decimal
        expiration: _timestamp_pb2.Timestamp
        pay_out_amount: _common_pb2.Decimal
        settlement: GetQuoteResponse.ProviderQuote.Settlement
        executable: bool
        def __init__(self, quote_id: _Optional[_Union[QuoteId, _Mapping]] = ..., rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement: _Optional[_Union[GetQuoteResponse.ProviderQuote.Settlement, _Mapping]] = ..., executable: _Optional[bool] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    ALL_QUOTES_FIELD_NUMBER: _ClassVar[int]
    success: GetQuoteResponse.Success
    failure: GetQuoteResponse.Failure
    all_quotes: _containers.RepeatedCompositeFieldContainer[GetQuoteResponse.ProviderQuote]
    def __init__(self, success: _Optional[_Union[GetQuoteResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[GetQuoteResponse.Failure, _Mapping]] = ..., all_quotes: _Optional[_Iterable[_Union[GetQuoteResponse.ProviderQuote, _Mapping]]] = ...) -> None: ...

class CreatePaymentRequest(_message.Message):
    __slots__ = ("payment_client_id", "amount", "currency", "payment_details", "quote_id", "travel_rule_data")
    class TravelRuleData(_message.Message):
        __slots__ = ("originator", "beneficiary")
        ORIGINATOR_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_FIELD_NUMBER: _ClassVar[int]
        originator: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        beneficiary: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        def __init__(self, originator: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ..., beneficiary: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ...) -> None: ...
    PAYMENT_CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_DETAILS_FIELD_NUMBER: _ClassVar[int]
    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    TRAVEL_RULE_DATA_FIELD_NUMBER: _ClassVar[int]
    payment_client_id: str
    amount: PaymentAmount
    currency: str
    payment_details: _payment_method_pb2.PaymentDetails
    quote_id: QuoteId
    travel_rule_data: CreatePaymentRequest.TravelRuleData
    def __init__(self, payment_client_id: _Optional[str] = ..., amount: _Optional[_Union[PaymentAmount, _Mapping]] = ..., currency: _Optional[str] = ..., payment_details: _Optional[_Union[_payment_method_pb2.PaymentDetails, _Mapping]] = ..., quote_id: _Optional[_Union[QuoteId, _Mapping]] = ..., travel_rule_data: _Optional[_Union[CreatePaymentRequest.TravelRuleData, _Mapping]] = ...) -> None: ...

class QuoteId(_message.Message):
    __slots__ = ("quote_id", "provider_id")
    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    quote_id: int
    provider_id: int
    def __init__(self, quote_id: _Optional[int] = ..., provider_id: _Optional[int] = ...) -> None: ...

class CreatePaymentResponse(_message.Message):
    __slots__ = ("payment_client_id", "accepted", "settlement_required", "failure")
    class Accepted(_message.Message):
        __slots__ = ("payment_id", "settlement_amount", "payout_amount", "payout_provider_id")
        PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        payment_id: int
        settlement_amount: _common_pb2.Decimal
        payout_amount: _common_pb2.Decimal
        payout_provider_id: int
        def __init__(self, payment_id: _Optional[int] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payout_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payout_provider_id: _Optional[int] = ...) -> None: ...
    class SettlementRequired(_message.Message):
        __slots__ = ("payment_id", "settlement_amount", "payout_provider_id")
        PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        payment_id: int
        settlement_amount: _common_pb2.Decimal
        payout_provider_id: int
        def __init__(self, payment_id: _Optional[int] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payout_provider_id: _Optional[int] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[CreatePaymentResponse.Failure.Reason]
            REASON_QUOTE_NOT_FOUND: _ClassVar[CreatePaymentResponse.Failure.Reason]
            REASON_CREDIT_OR_PREDEPOSIT_REQUIRED: _ClassVar[CreatePaymentResponse.Failure.Reason]
        REASON_UNSPECIFIED: CreatePaymentResponse.Failure.Reason
        REASON_QUOTE_NOT_FOUND: CreatePaymentResponse.Failure.Reason
        REASON_CREDIT_OR_PREDEPOSIT_REQUIRED: CreatePaymentResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: CreatePaymentResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[CreatePaymentResponse.Failure.Reason, str]] = ...) -> None: ...
    PAYMENT_CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_REQUIRED_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    payment_client_id: str
    accepted: CreatePaymentResponse.Accepted
    settlement_required: CreatePaymentResponse.SettlementRequired
    failure: CreatePaymentResponse.Failure
    def __init__(self, payment_client_id: _Optional[str] = ..., accepted: _Optional[_Union[CreatePaymentResponse.Accepted, _Mapping]] = ..., settlement_required: _Optional[_Union[CreatePaymentResponse.SettlementRequired, _Mapping]] = ..., failure: _Optional[_Union[CreatePaymentResponse.Failure, _Mapping]] = ...) -> None: ...

class ConfirmPayoutRequest(_message.Message):
    __slots__ = ("payment_id", "payout_id", "receipt")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_ID_FIELD_NUMBER: _ClassVar[int]
    RECEIPT_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    payout_id: int
    receipt: _payment_receipt_pb2.PaymentReceipt
    def __init__(self, payment_id: _Optional[int] = ..., payout_id: _Optional[int] = ..., receipt: _Optional[_Union[_payment_receipt_pb2.PaymentReceipt, _Mapping]] = ...) -> None: ...

class ConfirmPayoutResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class CompleteManualAmlCheckRequest(_message.Message):
    __slots__ = ("payment_id", "approved", "rejected")
    class Approved(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason",)
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: str
        def __init__(self, reason: _Optional[str] = ...) -> None: ...
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    APPROVED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    approved: CompleteManualAmlCheckRequest.Approved
    rejected: CompleteManualAmlCheckRequest.Rejected
    def __init__(self, payment_id: _Optional[int] = ..., approved: _Optional[_Union[CompleteManualAmlCheckRequest.Approved, _Mapping]] = ..., rejected: _Optional[_Union[CompleteManualAmlCheckRequest.Rejected, _Mapping]] = ...) -> None: ...

class CompleteManualAmlCheckResponse(_message.Message):
    __slots__ = ("approved", "rejected")
    class Approved(_message.Message):
        __slots__ = ("pay_out_amount", "settlement_amount", "pay_out_quote_id", "pay_out_client_quote_id")
        PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        PAY_OUT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
        PAY_OUT_CLIENT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
        pay_out_amount: _common_pb2.Decimal
        settlement_amount: _common_pb2.Decimal
        pay_out_quote_id: int
        pay_out_client_quote_id: str
        def __init__(self, pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_out_quote_id: _Optional[int] = ..., pay_out_client_quote_id: _Optional[str] = ...) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    APPROVED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    approved: CompleteManualAmlCheckResponse.Approved
    rejected: CompleteManualAmlCheckResponse.Rejected
    def __init__(self, approved: _Optional[_Union[CompleteManualAmlCheckResponse.Approved, _Mapping]] = ..., rejected: _Optional[_Union[CompleteManualAmlCheckResponse.Rejected, _Mapping]] = ...) -> None: ...

class PaymentAmount(_message.Message):
    __slots__ = ("pay_out_amount", "settlement_amount")
    PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    pay_out_amount: _common_pb2.Decimal
    settlement_amount: _common_pb2.Decimal
    def __init__(self, pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...

class FinalizePayoutRequest(_message.Message):
    __slots__ = ("payment_id", "success", "failure")
    class Success(_message.Message):
        __slots__ = ("receipt",)
        RECEIPT_FIELD_NUMBER: _ClassVar[int]
        receipt: _payment_receipt_pb2.PaymentReceipt
        def __init__(self, receipt: _Optional[_Union[_payment_receipt_pb2.PaymentReceipt, _Mapping]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: str
        def __init__(self, reason: _Optional[str] = ...) -> None: ...
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    success: FinalizePayoutRequest.Success
    failure: FinalizePayoutRequest.Failure
    def __init__(self, payment_id: _Optional[int] = ..., success: _Optional[_Union[FinalizePayoutRequest.Success, _Mapping]] = ..., failure: _Optional[_Union[FinalizePayoutRequest.Failure, _Mapping]] = ...) -> None: ...

class FinalizePayoutResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...
