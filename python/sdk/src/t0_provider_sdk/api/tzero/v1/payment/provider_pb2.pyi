import datetime

from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from tzero.v1.common import payment_receipt_pb2 as _payment_receipt_pb2
from tzero.v1.common import common_pb2 as _common_pb2
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

class AppendLedgerEntriesRequest(_message.Message):
    __slots__ = ("transactions",)
    class AccountType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
        __slots__ = ()
        ACCOUNT_TYPE_UNSPECIFIED: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_BALANCE: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_PAY_IN: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_PAY_OUT: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_FEE_EXPENSE: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_SETTLEMENT_IN: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_SETTLEMENT_OUT: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_PAYMENT_INTENT_IN: _ClassVar[AppendLedgerEntriesRequest.AccountType]
        ACCOUNT_TYPE_PAYMENT_INTENT_OUT: _ClassVar[AppendLedgerEntriesRequest.AccountType]
    ACCOUNT_TYPE_UNSPECIFIED: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_BALANCE: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_PAY_IN: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_PAY_OUT: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_FEE_EXPENSE: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_SETTLEMENT_IN: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_SETTLEMENT_OUT: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_PAYMENT_INTENT_IN: AppendLedgerEntriesRequest.AccountType
    ACCOUNT_TYPE_PAYMENT_INTENT_OUT: AppendLedgerEntriesRequest.AccountType
    class Transaction(_message.Message):
        __slots__ = ("transaction_id", "entries", "payout", "provider_settlement", "fee_settlement")
        class Payout(_message.Message):
            __slots__ = ("payment_id",)
            PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
            payment_id: int
            def __init__(self, payment_id: _Optional[int] = ...) -> None: ...
        class ProviderSettlement(_message.Message):
            __slots__ = ("settlement_id",)
            SETTLEMENT_ID_FIELD_NUMBER: _ClassVar[int]
            settlement_id: int
            def __init__(self, settlement_id: _Optional[int] = ...) -> None: ...
        class FeeSettlement(_message.Message):
            __slots__ = ("fee_settlement_id",)
            FEE_SETTLEMENT_ID_FIELD_NUMBER: _ClassVar[int]
            fee_settlement_id: int
            def __init__(self, fee_settlement_id: _Optional[int] = ...) -> None: ...
        TRANSACTION_ID_FIELD_NUMBER: _ClassVar[int]
        ENTRIES_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_FIELD_NUMBER: _ClassVar[int]
        PROVIDER_SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
        FEE_SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
        transaction_id: int
        entries: _containers.RepeatedCompositeFieldContainer[AppendLedgerEntriesRequest.LedgerEntry]
        payout: AppendLedgerEntriesRequest.Transaction.Payout
        provider_settlement: AppendLedgerEntriesRequest.Transaction.ProviderSettlement
        fee_settlement: AppendLedgerEntriesRequest.Transaction.FeeSettlement
        def __init__(self, transaction_id: _Optional[int] = ..., entries: _Optional[_Iterable[_Union[AppendLedgerEntriesRequest.LedgerEntry, _Mapping]]] = ..., payout: _Optional[_Union[AppendLedgerEntriesRequest.Transaction.Payout, _Mapping]] = ..., provider_settlement: _Optional[_Union[AppendLedgerEntriesRequest.Transaction.ProviderSettlement, _Mapping]] = ..., fee_settlement: _Optional[_Union[AppendLedgerEntriesRequest.Transaction.FeeSettlement, _Mapping]] = ...) -> None: ...
    class LedgerEntry(_message.Message):
        __slots__ = ("account_owner_id", "account_type", "debit", "credit")
        ACCOUNT_OWNER_ID_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_TYPE_FIELD_NUMBER: _ClassVar[int]
        DEBIT_FIELD_NUMBER: _ClassVar[int]
        CREDIT_FIELD_NUMBER: _ClassVar[int]
        account_owner_id: int
        account_type: AppendLedgerEntriesRequest.AccountType
        debit: _common_pb2.Decimal
        credit: _common_pb2.Decimal
        def __init__(self, account_owner_id: _Optional[int] = ..., account_type: _Optional[_Union[AppendLedgerEntriesRequest.AccountType, str]] = ..., debit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., credit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
    TRANSACTIONS_FIELD_NUMBER: _ClassVar[int]
    transactions: _containers.RepeatedCompositeFieldContainer[AppendLedgerEntriesRequest.Transaction]
    def __init__(self, transactions: _Optional[_Iterable[_Union[AppendLedgerEntriesRequest.Transaction, _Mapping]]] = ...) -> None: ...

class AppendLedgerEntriesResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class PayoutRequest(_message.Message):
    __slots__ = ("payment_id", "payout_id", "currency", "client_quote_id", "amount", "payout_details", "pay_in_provider_id", "travel_rule_data")
    class TravelRuleData(_message.Message):
        __slots__ = ("originator", "beneficiary", "originator_provider")
        ORIGINATOR_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_FIELD_NUMBER: _ClassVar[int]
        ORIGINATOR_PROVIDER_FIELD_NUMBER: _ClassVar[int]
        originator: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        beneficiary: _containers.RepeatedCompositeFieldContainer[_ivms101_pb2.Person]
        originator_provider: _ivms101_pb2.Person
        def __init__(self, originator: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ..., beneficiary: _Optional[_Iterable[_Union[_ivms101_pb2.Person, _Mapping]]] = ..., originator_provider: _Optional[_Union[_ivms101_pb2.Person, _Mapping]] = ...) -> None: ...
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_ID_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    CLIENT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_DETAILS_FIELD_NUMBER: _ClassVar[int]
    PAY_IN_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    TRAVEL_RULE_DATA_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    payout_id: int
    currency: str
    client_quote_id: str
    amount: _common_pb2.Decimal
    payout_details: _payment_method_pb2.PaymentDetails
    pay_in_provider_id: int
    travel_rule_data: PayoutRequest.TravelRuleData
    def __init__(self, payment_id: _Optional[int] = ..., payout_id: _Optional[int] = ..., currency: _Optional[str] = ..., client_quote_id: _Optional[str] = ..., amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., payout_details: _Optional[_Union[_payment_method_pb2.PaymentDetails, _Mapping]] = ..., pay_in_provider_id: _Optional[int] = ..., travel_rule_data: _Optional[_Union[PayoutRequest.TravelRuleData, _Mapping]] = ...) -> None: ...

class PayoutResponse(_message.Message):
    __slots__ = ("beneficiary_provider_legal_entity_id", "accepted", "failed", "manual_aml_check")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class ManualAmlCheck(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Failed(_message.Message):
        __slots__ = ("reason", "details")
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[PayoutResponse.Failed.Reason]
        REASON_UNSPECIFIED: PayoutResponse.Failed.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        DETAILS_FIELD_NUMBER: _ClassVar[int]
        reason: PayoutResponse.Failed.Reason
        details: str
        def __init__(self, reason: _Optional[_Union[PayoutResponse.Failed.Reason, str]] = ..., details: _Optional[str] = ...) -> None: ...
    BENEFICIARY_PROVIDER_LEGAL_ENTITY_ID_FIELD_NUMBER: _ClassVar[int]
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    FAILED_FIELD_NUMBER: _ClassVar[int]
    MANUAL_AML_CHECK_FIELD_NUMBER: _ClassVar[int]
    beneficiary_provider_legal_entity_id: int
    accepted: PayoutResponse.Accepted
    failed: PayoutResponse.Failed
    manual_aml_check: PayoutResponse.ManualAmlCheck
    def __init__(self, beneficiary_provider_legal_entity_id: _Optional[int] = ..., accepted: _Optional[_Union[PayoutResponse.Accepted, _Mapping]] = ..., failed: _Optional[_Union[PayoutResponse.Failed, _Mapping]] = ..., manual_aml_check: _Optional[_Union[PayoutResponse.ManualAmlCheck, _Mapping]] = ...) -> None: ...

class UpdatePaymentRequest(_message.Message):
    __slots__ = ("payment_id", "payment_client_id", "accepted", "failed", "confirmed", "manual_aml_check")
    class Accepted(_message.Message):
        __slots__ = ("payout_amount", "travel_rule_data")
        class TravelRuleData(_message.Message):
            __slots__ = ("beneficiary_provider",)
            BENEFICIARY_PROVIDER_FIELD_NUMBER: _ClassVar[int]
            beneficiary_provider: _ivms101_pb2.Person
            def __init__(self, beneficiary_provider: _Optional[_Union[_ivms101_pb2.Person, _Mapping]] = ...) -> None: ...
        PAYOUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        TRAVEL_RULE_DATA_FIELD_NUMBER: _ClassVar[int]
        payout_amount: _common_pb2.Decimal
        travel_rule_data: UpdatePaymentRequest.Accepted.TravelRuleData
        def __init__(self, payout_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., travel_rule_data: _Optional[_Union[UpdatePaymentRequest.Accepted.TravelRuleData, _Mapping]] = ...) -> None: ...
    class Failed(_message.Message):
        __slots__ = ("reason", "details")
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[UpdatePaymentRequest.Failed.Reason]
            REASON_NO_QUOTE_AFTER_AML_APPROVAL: _ClassVar[UpdatePaymentRequest.Failed.Reason]
            REASON_QUOTE_REJECTED_AFTER_AML_APPROVAL: _ClassVar[UpdatePaymentRequest.Failed.Reason]
            REASON_AML_RISK_CHECK_FAILED: _ClassVar[UpdatePaymentRequest.Failed.Reason]
            REASON_CREDIT_LIMIT_EXCEEDED_AFTER_AML_APPROVAL: _ClassVar[UpdatePaymentRequest.Failed.Reason]
        REASON_UNSPECIFIED: UpdatePaymentRequest.Failed.Reason
        REASON_NO_QUOTE_AFTER_AML_APPROVAL: UpdatePaymentRequest.Failed.Reason
        REASON_QUOTE_REJECTED_AFTER_AML_APPROVAL: UpdatePaymentRequest.Failed.Reason
        REASON_AML_RISK_CHECK_FAILED: UpdatePaymentRequest.Failed.Reason
        REASON_CREDIT_LIMIT_EXCEEDED_AFTER_AML_APPROVAL: UpdatePaymentRequest.Failed.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        DETAILS_FIELD_NUMBER: _ClassVar[int]
        reason: UpdatePaymentRequest.Failed.Reason
        details: str
        def __init__(self, reason: _Optional[_Union[UpdatePaymentRequest.Failed.Reason, str]] = ..., details: _Optional[str] = ...) -> None: ...
    class Confirmed(_message.Message):
        __slots__ = ("paid_out_at", "receipt")
        PAID_OUT_AT_FIELD_NUMBER: _ClassVar[int]
        RECEIPT_FIELD_NUMBER: _ClassVar[int]
        paid_out_at: _timestamp_pb2.Timestamp
        receipt: _payment_receipt_pb2.PaymentReceipt
        def __init__(self, paid_out_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., receipt: _Optional[_Union[_payment_receipt_pb2.PaymentReceipt, _Mapping]] = ...) -> None: ...
    class ManualAmlCheck(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    FAILED_FIELD_NUMBER: _ClassVar[int]
    CONFIRMED_FIELD_NUMBER: _ClassVar[int]
    MANUAL_AML_CHECK_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    payment_client_id: str
    accepted: UpdatePaymentRequest.Accepted
    failed: UpdatePaymentRequest.Failed
    confirmed: UpdatePaymentRequest.Confirmed
    manual_aml_check: UpdatePaymentRequest.ManualAmlCheck
    def __init__(self, payment_id: _Optional[int] = ..., payment_client_id: _Optional[str] = ..., accepted: _Optional[_Union[UpdatePaymentRequest.Accepted, _Mapping]] = ..., failed: _Optional[_Union[UpdatePaymentRequest.Failed, _Mapping]] = ..., confirmed: _Optional[_Union[UpdatePaymentRequest.Confirmed, _Mapping]] = ..., manual_aml_check: _Optional[_Union[UpdatePaymentRequest.ManualAmlCheck, _Mapping]] = ...) -> None: ...

class UpdatePaymentResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class UpdateLimitRequest(_message.Message):
    __slots__ = ("limits",)
    class Limit(_message.Message):
        __slots__ = ("version", "counterpart_id", "payout_limit", "credit_limit", "credit_usage", "reserve")
        VERSION_FIELD_NUMBER: _ClassVar[int]
        COUNTERPART_ID_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_LIMIT_FIELD_NUMBER: _ClassVar[int]
        CREDIT_LIMIT_FIELD_NUMBER: _ClassVar[int]
        CREDIT_USAGE_FIELD_NUMBER: _ClassVar[int]
        RESERVE_FIELD_NUMBER: _ClassVar[int]
        version: int
        counterpart_id: int
        payout_limit: _common_pb2.Decimal
        credit_limit: _common_pb2.Decimal
        credit_usage: _common_pb2.Decimal
        reserve: _common_pb2.Decimal
        def __init__(self, version: _Optional[int] = ..., counterpart_id: _Optional[int] = ..., payout_limit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., credit_limit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., credit_usage: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., reserve: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
    LIMITS_FIELD_NUMBER: _ClassVar[int]
    limits: _containers.RepeatedCompositeFieldContainer[UpdateLimitRequest.Limit]
    def __init__(self, limits: _Optional[_Iterable[_Union[UpdateLimitRequest.Limit, _Mapping]]] = ...) -> None: ...

class UpdateLimitResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ApprovePaymentQuoteRequest(_message.Message):
    __slots__ = ("payment_id", "pay_out_quote_id", "pay_out_rate", "pay_out_amount", "settlement_amount", "pay_out_fix")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_RATE_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PAY_OUT_FIX_FIELD_NUMBER: _ClassVar[int]
    payment_id: int
    pay_out_quote_id: int
    pay_out_rate: _common_pb2.Decimal
    pay_out_amount: _common_pb2.Decimal
    settlement_amount: _common_pb2.Decimal
    pay_out_fix: _common_pb2.Decimal
    def __init__(self, payment_id: _Optional[int] = ..., pay_out_quote_id: _Optional[int] = ..., pay_out_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_out_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., pay_out_fix: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...

class ApprovePaymentQuoteResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: ApprovePaymentQuoteResponse.Accepted
    rejected: ApprovePaymentQuoteResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[ApprovePaymentQuoteResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[ApprovePaymentQuoteResponse.Rejected, _Mapping]] = ...) -> None: ...
