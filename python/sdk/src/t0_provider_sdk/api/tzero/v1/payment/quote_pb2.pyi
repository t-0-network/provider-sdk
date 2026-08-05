import datetime

from tzero.v1.common import common_pb2 as _common_pb2
from tzero.v1.common import payment_method_pb2 as _payment_method_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class GetQuotesRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class GetQuotesResponse(_message.Message):
    __slots__ = ("quotes",)
    class CurrencyQuote(_message.Message):
        __slots__ = ("currency", "payment_method_quotes")
        class PaymentMethodQuote(_message.Message):
            __slots__ = ("payment_method", "provider_quotes")
            class ProviderQuote(_message.Message):
                __slots__ = ("provider_id", "balance", "quotes")
                class ProviderBalance(_message.Message):
                    __slots__ = ("credit_limit", "available")
                    CREDIT_LIMIT_FIELD_NUMBER: _ClassVar[int]
                    AVAILABLE_FIELD_NUMBER: _ClassVar[int]
                    credit_limit: _common_pb2.Decimal
                    available: _common_pb2.Decimal
                    def __init__(self, credit_limit: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., available: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
                class Quote(_message.Message):
                    __slots__ = ("quote_id", "max_amount", "rate", "fix", "expiration")
                    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
                    MAX_AMOUNT_FIELD_NUMBER: _ClassVar[int]
                    RATE_FIELD_NUMBER: _ClassVar[int]
                    FIX_FIELD_NUMBER: _ClassVar[int]
                    EXPIRATION_FIELD_NUMBER: _ClassVar[int]
                    quote_id: int
                    max_amount: _common_pb2.Decimal
                    rate: _common_pb2.Decimal
                    fix: _common_pb2.Decimal
                    expiration: _timestamp_pb2.Timestamp
                    def __init__(self, quote_id: _Optional[int] = ..., max_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., fix: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expiration: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
                PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
                BALANCE_FIELD_NUMBER: _ClassVar[int]
                QUOTES_FIELD_NUMBER: _ClassVar[int]
                provider_id: int
                balance: GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote.ProviderBalance
                quotes: _containers.RepeatedCompositeFieldContainer[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote.Quote]
                def __init__(self, provider_id: _Optional[int] = ..., balance: _Optional[_Union[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote.ProviderBalance, _Mapping]] = ..., quotes: _Optional[_Iterable[_Union[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote.Quote, _Mapping]]] = ...) -> None: ...
            PAYMENT_METHOD_FIELD_NUMBER: _ClassVar[int]
            PROVIDER_QUOTES_FIELD_NUMBER: _ClassVar[int]
            payment_method: _payment_method_pb2.PaymentMethodType
            provider_quotes: _containers.RepeatedCompositeFieldContainer[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote]
            def __init__(self, payment_method: _Optional[_Union[_payment_method_pb2.PaymentMethodType, str]] = ..., provider_quotes: _Optional[_Iterable[_Union[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote.ProviderQuote, _Mapping]]] = ...) -> None: ...
        CURRENCY_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_METHOD_QUOTES_FIELD_NUMBER: _ClassVar[int]
        currency: str
        payment_method_quotes: _containers.RepeatedCompositeFieldContainer[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote]
        def __init__(self, currency: _Optional[str] = ..., payment_method_quotes: _Optional[_Iterable[_Union[GetQuotesResponse.CurrencyQuote.PaymentMethodQuote, _Mapping]]] = ...) -> None: ...
    QUOTES_FIELD_NUMBER: _ClassVar[int]
    quotes: _containers.RepeatedCompositeFieldContainer[GetQuotesResponse.CurrencyQuote]
    def __init__(self, quotes: _Optional[_Iterable[_Union[GetQuotesResponse.CurrencyQuote, _Mapping]]] = ...) -> None: ...
