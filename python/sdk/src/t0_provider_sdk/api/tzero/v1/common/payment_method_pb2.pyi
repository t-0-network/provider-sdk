from tzero.v1.common import common_pb2 as _common_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import descriptor_pb2 as _descriptor_pb2
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PaymentMethodType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    PAYMENT_METHOD_TYPE_UNSPECIFIED: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_SEPA: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_SWIFT: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_ACH: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_DOMESTIC_WIRE: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_FPS: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_M_PESA: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_G_CASH: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_INDIAN_BANK_TRANSFER: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_PESONET: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_INSTAPAY: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_PAKISTAN_BANK_TRANSFER: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_PAKISTAN_MOBILE_WALLET: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_PIX: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_AFRICAN_MOBILE_MONEY: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_CNAPS: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_NIP: _ClassVar[PaymentMethodType]
    PAYMENT_METHOD_TYPE_RTP: _ClassVar[PaymentMethodType]
PAYMENT_METHOD_TYPE_UNSPECIFIED: PaymentMethodType
PAYMENT_METHOD_TYPE_SEPA: PaymentMethodType
PAYMENT_METHOD_TYPE_SWIFT: PaymentMethodType
PAYMENT_METHOD_TYPE_ACH: PaymentMethodType
PAYMENT_METHOD_TYPE_DOMESTIC_WIRE: PaymentMethodType
PAYMENT_METHOD_TYPE_FPS: PaymentMethodType
PAYMENT_METHOD_TYPE_M_PESA: PaymentMethodType
PAYMENT_METHOD_TYPE_G_CASH: PaymentMethodType
PAYMENT_METHOD_TYPE_INDIAN_BANK_TRANSFER: PaymentMethodType
PAYMENT_METHOD_TYPE_PESONET: PaymentMethodType
PAYMENT_METHOD_TYPE_INSTAPAY: PaymentMethodType
PAYMENT_METHOD_TYPE_PAKISTAN_BANK_TRANSFER: PaymentMethodType
PAYMENT_METHOD_TYPE_PAKISTAN_MOBILE_WALLET: PaymentMethodType
PAYMENT_METHOD_TYPE_PIX: PaymentMethodType
PAYMENT_METHOD_TYPE_AFRICAN_MOBILE_MONEY: PaymentMethodType
PAYMENT_METHOD_TYPE_CNAPS: PaymentMethodType
PAYMENT_METHOD_TYPE_NIP: PaymentMethodType
PAYMENT_METHOD_TYPE_RTP: PaymentMethodType
PAYMENT_METHOD_TYPE_FIELD_NUMBER: _ClassVar[int]
payment_method_type: _descriptor.FieldDescriptor

class PaymentDetails(_message.Message):
    __slots__ = ("sepa", "swift", "ach", "domestic_wire", "fps", "mpesa", "gcash", "indian_bank_transfer", "pesonet", "instapay", "pakistan_bank_transfer", "pakistan_mobile_wallet", "pix", "african_mobile_money", "naps", "nip", "rtp")
    class Sepa(_message.Message):
        __slots__ = ("iban", "beneficiary_name", "payment_reference")
        IBAN_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        iban: str
        beneficiary_name: str
        payment_reference: str
        def __init__(self, iban: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Fps(_message.Message):
        __slots__ = ("sort_code", "account_number", "beneficiary_name", "reference")
        SORT_CODE_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        REFERENCE_FIELD_NUMBER: _ClassVar[int]
        sort_code: str
        account_number: str
        beneficiary_name: str
        reference: str
        def __init__(self, sort_code: _Optional[str] = ..., account_number: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., reference: _Optional[str] = ...) -> None: ...
    class MPesa(_message.Message):
        __slots__ = ("beneficiary_phone", "account_reference", "beneficiary_name")
        BENEFICIARY_PHONE_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        beneficiary_phone: str
        account_reference: str
        beneficiary_name: str
        def __init__(self, beneficiary_phone: _Optional[str] = ..., account_reference: _Optional[str] = ..., beneficiary_name: _Optional[str] = ...) -> None: ...
    class AfricanMobileMoney(_message.Message):
        __slots__ = ("network", "beneficiary_phone", "account_reference", "beneficiary_name")
        class Network(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            NETWORK_UNDEFINED: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_M_PESA: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_AIRTEL: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_MTN: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_VODACOM: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_ORANGE: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_VODAFONE: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_FREE: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
            NETWORK_ZAMTEL: _ClassVar[PaymentDetails.AfricanMobileMoney.Network]
        NETWORK_UNDEFINED: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_M_PESA: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_AIRTEL: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_MTN: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_VODACOM: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_ORANGE: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_VODAFONE: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_FREE: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_ZAMTEL: PaymentDetails.AfricanMobileMoney.Network
        NETWORK_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_PHONE_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        network: PaymentDetails.AfricanMobileMoney.Network
        beneficiary_phone: str
        account_reference: str
        beneficiary_name: str
        def __init__(self, network: _Optional[_Union[PaymentDetails.AfricanMobileMoney.Network, str]] = ..., beneficiary_phone: _Optional[str] = ..., account_reference: _Optional[str] = ..., beneficiary_name: _Optional[str] = ...) -> None: ...
    class GCash(_message.Message):
        __slots__ = ("beneficiary_name", "beneficiary_phone", "payment_reference")
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_PHONE_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        beneficiary_name: str
        beneficiary_phone: str
        payment_reference: str
        def __init__(self, beneficiary_name: _Optional[str] = ..., beneficiary_phone: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class IndianBankTransfer(_message.Message):
        __slots__ = ("account_ifsc", "imps", "beneficiary_name", "beneficiary_type", "payment_reference")
        class AccountIFSC(_message.Message):
            __slots__ = ("account_number", "ifsc")
            ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
            IFSC_FIELD_NUMBER: _ClassVar[int]
            account_number: str
            ifsc: str
            def __init__(self, account_number: _Optional[str] = ..., ifsc: _Optional[str] = ...) -> None: ...
        class IMPS(_message.Message):
            __slots__ = ("beneficiary_phone", "mmid")
            BENEFICIARY_PHONE_FIELD_NUMBER: _ClassVar[int]
            MMID_FIELD_NUMBER: _ClassVar[int]
            beneficiary_phone: str
            mmid: str
            def __init__(self, beneficiary_phone: _Optional[str] = ..., mmid: _Optional[str] = ...) -> None: ...
        ACCOUNT_IFSC_FIELD_NUMBER: _ClassVar[int]
        IMPS_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_TYPE_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        account_ifsc: PaymentDetails.IndianBankTransfer.AccountIFSC
        imps: PaymentDetails.IndianBankTransfer.IMPS
        beneficiary_name: str
        beneficiary_type: str
        payment_reference: str
        def __init__(self, account_ifsc: _Optional[_Union[PaymentDetails.IndianBankTransfer.AccountIFSC, _Mapping]] = ..., imps: _Optional[_Union[PaymentDetails.IndianBankTransfer.IMPS, _Mapping]] = ..., beneficiary_name: _Optional[str] = ..., beneficiary_type: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Swift(_message.Message):
        __slots__ = ("swift_code", "account_number", "beneficiary_name", "beneficiary_address", "payment_reference", "bank_name", "bank_country", "account_currency", "intermediary_bank")
        class IntermediaryBank(_message.Message):
            __slots__ = ("swift_code", "bank_name", "account_number")
            SWIFT_CODE_FIELD_NUMBER: _ClassVar[int]
            BANK_NAME_FIELD_NUMBER: _ClassVar[int]
            ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
            swift_code: str
            bank_name: str
            account_number: str
            def __init__(self, swift_code: _Optional[str] = ..., bank_name: _Optional[str] = ..., account_number: _Optional[str] = ...) -> None: ...
        SWIFT_CODE_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_ADDRESS_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        BANK_NAME_FIELD_NUMBER: _ClassVar[int]
        BANK_COUNTRY_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_CURRENCY_FIELD_NUMBER: _ClassVar[int]
        INTERMEDIARY_BANK_FIELD_NUMBER: _ClassVar[int]
        swift_code: str
        account_number: str
        beneficiary_name: str
        beneficiary_address: str
        payment_reference: str
        bank_name: str
        bank_country: str
        account_currency: str
        intermediary_bank: PaymentDetails.Swift.IntermediaryBank
        def __init__(self, swift_code: _Optional[str] = ..., account_number: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., beneficiary_address: _Optional[str] = ..., payment_reference: _Optional[str] = ..., bank_name: _Optional[str] = ..., bank_country: _Optional[str] = ..., account_currency: _Optional[str] = ..., intermediary_bank: _Optional[_Union[PaymentDetails.Swift.IntermediaryBank, _Mapping]] = ...) -> None: ...
    class Ach(_message.Message):
        __slots__ = ("routing_number", "account_number", "account_holder_name", "account_type", "payment_reference")
        class AchAccountType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            ACH_ACCOUNT_TYPE_UNSPECIFIED: _ClassVar[PaymentDetails.Ach.AchAccountType]
            ACH_ACCOUNT_TYPE_CHECKING: _ClassVar[PaymentDetails.Ach.AchAccountType]
            ACH_ACCOUNT_TYPE_SAVINGS: _ClassVar[PaymentDetails.Ach.AchAccountType]
        ACH_ACCOUNT_TYPE_UNSPECIFIED: PaymentDetails.Ach.AchAccountType
        ACH_ACCOUNT_TYPE_CHECKING: PaymentDetails.Ach.AchAccountType
        ACH_ACCOUNT_TYPE_SAVINGS: PaymentDetails.Ach.AchAccountType
        ROUTING_NUMBER_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_HOLDER_NAME_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_TYPE_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        routing_number: str
        account_number: str
        account_holder_name: str
        account_type: PaymentDetails.Ach.AchAccountType
        payment_reference: str
        def __init__(self, routing_number: _Optional[str] = ..., account_number: _Optional[str] = ..., account_holder_name: _Optional[str] = ..., account_type: _Optional[_Union[PaymentDetails.Ach.AchAccountType, str]] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class DomesticWire(_message.Message):
        __slots__ = ("bank_name", "bank_address", "routing_number", "account_number", "beneficiary_name", "beneficiary_address", "wire_reference")
        BANK_NAME_FIELD_NUMBER: _ClassVar[int]
        BANK_ADDRESS_FIELD_NUMBER: _ClassVar[int]
        ROUTING_NUMBER_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_ADDRESS_FIELD_NUMBER: _ClassVar[int]
        WIRE_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        bank_name: str
        bank_address: str
        routing_number: str
        account_number: str
        beneficiary_name: str
        beneficiary_address: str
        wire_reference: str
        def __init__(self, bank_name: _Optional[str] = ..., bank_address: _Optional[str] = ..., routing_number: _Optional[str] = ..., account_number: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., beneficiary_address: _Optional[str] = ..., wire_reference: _Optional[str] = ...) -> None: ...
    class Pesonet(_message.Message):
        __slots__ = ("recipient_financial_institution", "recipient_identifier", "recipient_account_name", "purpose_of_transfer", "recipient_address_email")
        RECIPIENT_FINANCIAL_INSTITUTION_FIELD_NUMBER: _ClassVar[int]
        RECIPIENT_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
        RECIPIENT_ACCOUNT_NAME_FIELD_NUMBER: _ClassVar[int]
        PURPOSE_OF_TRANSFER_FIELD_NUMBER: _ClassVar[int]
        RECIPIENT_ADDRESS_EMAIL_FIELD_NUMBER: _ClassVar[int]
        recipient_financial_institution: str
        recipient_identifier: str
        recipient_account_name: str
        purpose_of_transfer: str
        recipient_address_email: str
        def __init__(self, recipient_financial_institution: _Optional[str] = ..., recipient_identifier: _Optional[str] = ..., recipient_account_name: _Optional[str] = ..., purpose_of_transfer: _Optional[str] = ..., recipient_address_email: _Optional[str] = ...) -> None: ...
    class Instapay(_message.Message):
        __slots__ = ("recipient_institution", "recipient_identifier", "recipient_account_name", "purpose_of_transfer")
        RECIPIENT_INSTITUTION_FIELD_NUMBER: _ClassVar[int]
        RECIPIENT_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
        RECIPIENT_ACCOUNT_NAME_FIELD_NUMBER: _ClassVar[int]
        PURPOSE_OF_TRANSFER_FIELD_NUMBER: _ClassVar[int]
        recipient_institution: str
        recipient_identifier: str
        recipient_account_name: str
        purpose_of_transfer: str
        def __init__(self, recipient_institution: _Optional[str] = ..., recipient_identifier: _Optional[str] = ..., recipient_account_name: _Optional[str] = ..., purpose_of_transfer: _Optional[str] = ...) -> None: ...
    class PakistanBankTransfer(_message.Message):
        __slots__ = ("iban", "beneficiary_name", "beneficiary_cnic", "payment_reference")
        IBAN_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_CNIC_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        iban: str
        beneficiary_name: str
        beneficiary_cnic: str
        payment_reference: str
        def __init__(self, iban: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., beneficiary_cnic: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class PakistanMobileWallet(_message.Message):
        __slots__ = ("wallet_provider", "mobile_number", "cnic", "beneficiary_name", "payment_reference")
        class PakistanWalletProvider(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            PAKISTAN_WALLET_PROVIDER_UNSPECIFIED: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
            PAKISTAN_WALLET_PROVIDER_JAZZCASH: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
            PAKISTAN_WALLET_PROVIDER_EASYPAISA: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
            PAKISTAN_WALLET_PROVIDER_SADAPAY: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
            PAKISTAN_WALLET_PROVIDER_NAYAPAY: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
            PAKISTAN_WALLET_PROVIDER_OTHER: _ClassVar[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider]
        PAKISTAN_WALLET_PROVIDER_UNSPECIFIED: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        PAKISTAN_WALLET_PROVIDER_JAZZCASH: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        PAKISTAN_WALLET_PROVIDER_EASYPAISA: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        PAKISTAN_WALLET_PROVIDER_SADAPAY: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        PAKISTAN_WALLET_PROVIDER_NAYAPAY: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        PAKISTAN_WALLET_PROVIDER_OTHER: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        WALLET_PROVIDER_FIELD_NUMBER: _ClassVar[int]
        MOBILE_NUMBER_FIELD_NUMBER: _ClassVar[int]
        CNIC_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        wallet_provider: PaymentDetails.PakistanMobileWallet.PakistanWalletProvider
        mobile_number: str
        cnic: str
        beneficiary_name: str
        payment_reference: str
        def __init__(self, wallet_provider: _Optional[_Union[PaymentDetails.PakistanMobileWallet.PakistanWalletProvider, str]] = ..., mobile_number: _Optional[str] = ..., cnic: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Pix(_message.Message):
        __slots__ = ("key_type", "key_value", "beneficiary_name", "beneficiary_tax_id", "payment_reference")
        class KeyType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            KEY_TYPE_UNSPECIFIED: _ClassVar[PaymentDetails.Pix.KeyType]
            KEY_TYPE_CPF: _ClassVar[PaymentDetails.Pix.KeyType]
            KEY_TYPE_CNPJ: _ClassVar[PaymentDetails.Pix.KeyType]
            KEY_TYPE_EMAIL: _ClassVar[PaymentDetails.Pix.KeyType]
            KEY_TYPE_PHONE: _ClassVar[PaymentDetails.Pix.KeyType]
            KEY_TYPE_EVP: _ClassVar[PaymentDetails.Pix.KeyType]
        KEY_TYPE_UNSPECIFIED: PaymentDetails.Pix.KeyType
        KEY_TYPE_CPF: PaymentDetails.Pix.KeyType
        KEY_TYPE_CNPJ: PaymentDetails.Pix.KeyType
        KEY_TYPE_EMAIL: PaymentDetails.Pix.KeyType
        KEY_TYPE_PHONE: PaymentDetails.Pix.KeyType
        KEY_TYPE_EVP: PaymentDetails.Pix.KeyType
        KEY_TYPE_FIELD_NUMBER: _ClassVar[int]
        KEY_VALUE_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_TAX_ID_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        key_type: PaymentDetails.Pix.KeyType
        key_value: str
        beneficiary_name: str
        beneficiary_tax_id: str
        payment_reference: str
        def __init__(self, key_type: _Optional[_Union[PaymentDetails.Pix.KeyType, str]] = ..., key_value: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., beneficiary_tax_id: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Cnaps(_message.Message):
        __slots__ = ("account_number", "cnaps_code", "beneficiary_name_local", "beneficiary_name", "business", "person", "payment_reference")
        class Business(_message.Message):
            __slots__ = ("license_number",)
            LICENSE_NUMBER_FIELD_NUMBER: _ClassVar[int]
            license_number: str
            def __init__(self, license_number: _Optional[str] = ...) -> None: ...
        class Person(_message.Message):
            __slots__ = ("id_number",)
            ID_NUMBER_FIELD_NUMBER: _ClassVar[int]
            id_number: str
            def __init__(self, id_number: _Optional[str] = ...) -> None: ...
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        CNAPS_CODE_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_LOCAL_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        BUSINESS_FIELD_NUMBER: _ClassVar[int]
        PERSON_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        account_number: str
        cnaps_code: str
        beneficiary_name_local: str
        beneficiary_name: str
        business: PaymentDetails.Cnaps.Business
        person: PaymentDetails.Cnaps.Person
        payment_reference: str
        def __init__(self, account_number: _Optional[str] = ..., cnaps_code: _Optional[str] = ..., beneficiary_name_local: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., business: _Optional[_Union[PaymentDetails.Cnaps.Business, _Mapping]] = ..., person: _Optional[_Union[PaymentDetails.Cnaps.Person, _Mapping]] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Nip(_message.Message):
        __slots__ = ("bank_code", "account_number", "beneficiary_name", "payment_reference")
        BANK_CODE_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        BENEFICIARY_NAME_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        bank_code: str
        account_number: str
        beneficiary_name: str
        payment_reference: str
        def __init__(self, bank_code: _Optional[str] = ..., account_number: _Optional[str] = ..., beneficiary_name: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    class Rtp(_message.Message):
        __slots__ = ("routing_number", "account_number", "account_type", "bank_name", "payment_reference")
        class RtpAccountType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            RTP_ACCOUNT_TYPE_UNSPECIFIED: _ClassVar[PaymentDetails.Rtp.RtpAccountType]
            RTP_ACCOUNT_TYPE_CHECKING: _ClassVar[PaymentDetails.Rtp.RtpAccountType]
            RTP_ACCOUNT_TYPE_SAVINGS: _ClassVar[PaymentDetails.Rtp.RtpAccountType]
        RTP_ACCOUNT_TYPE_UNSPECIFIED: PaymentDetails.Rtp.RtpAccountType
        RTP_ACCOUNT_TYPE_CHECKING: PaymentDetails.Rtp.RtpAccountType
        RTP_ACCOUNT_TYPE_SAVINGS: PaymentDetails.Rtp.RtpAccountType
        ROUTING_NUMBER_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_NUMBER_FIELD_NUMBER: _ClassVar[int]
        ACCOUNT_TYPE_FIELD_NUMBER: _ClassVar[int]
        BANK_NAME_FIELD_NUMBER: _ClassVar[int]
        PAYMENT_REFERENCE_FIELD_NUMBER: _ClassVar[int]
        routing_number: str
        account_number: str
        account_type: PaymentDetails.Rtp.RtpAccountType
        bank_name: str
        payment_reference: str
        def __init__(self, routing_number: _Optional[str] = ..., account_number: _Optional[str] = ..., account_type: _Optional[_Union[PaymentDetails.Rtp.RtpAccountType, str]] = ..., bank_name: _Optional[str] = ..., payment_reference: _Optional[str] = ...) -> None: ...
    SEPA_FIELD_NUMBER: _ClassVar[int]
    SWIFT_FIELD_NUMBER: _ClassVar[int]
    ACH_FIELD_NUMBER: _ClassVar[int]
    DOMESTIC_WIRE_FIELD_NUMBER: _ClassVar[int]
    FPS_FIELD_NUMBER: _ClassVar[int]
    MPESA_FIELD_NUMBER: _ClassVar[int]
    GCASH_FIELD_NUMBER: _ClassVar[int]
    INDIAN_BANK_TRANSFER_FIELD_NUMBER: _ClassVar[int]
    PESONET_FIELD_NUMBER: _ClassVar[int]
    INSTAPAY_FIELD_NUMBER: _ClassVar[int]
    PAKISTAN_BANK_TRANSFER_FIELD_NUMBER: _ClassVar[int]
    PAKISTAN_MOBILE_WALLET_FIELD_NUMBER: _ClassVar[int]
    PIX_FIELD_NUMBER: _ClassVar[int]
    AFRICAN_MOBILE_MONEY_FIELD_NUMBER: _ClassVar[int]
    NAPS_FIELD_NUMBER: _ClassVar[int]
    NIP_FIELD_NUMBER: _ClassVar[int]
    RTP_FIELD_NUMBER: _ClassVar[int]
    sepa: PaymentDetails.Sepa
    swift: PaymentDetails.Swift
    ach: PaymentDetails.Ach
    domestic_wire: PaymentDetails.DomesticWire
    fps: PaymentDetails.Fps
    mpesa: PaymentDetails.MPesa
    gcash: PaymentDetails.GCash
    indian_bank_transfer: PaymentDetails.IndianBankTransfer
    pesonet: PaymentDetails.Pesonet
    instapay: PaymentDetails.Instapay
    pakistan_bank_transfer: PaymentDetails.PakistanBankTransfer
    pakistan_mobile_wallet: PaymentDetails.PakistanMobileWallet
    pix: PaymentDetails.Pix
    african_mobile_money: PaymentDetails.AfricanMobileMoney
    naps: PaymentDetails.Cnaps
    nip: PaymentDetails.Nip
    rtp: PaymentDetails.Rtp
    def __init__(self, sepa: _Optional[_Union[PaymentDetails.Sepa, _Mapping]] = ..., swift: _Optional[_Union[PaymentDetails.Swift, _Mapping]] = ..., ach: _Optional[_Union[PaymentDetails.Ach, _Mapping]] = ..., domestic_wire: _Optional[_Union[PaymentDetails.DomesticWire, _Mapping]] = ..., fps: _Optional[_Union[PaymentDetails.Fps, _Mapping]] = ..., mpesa: _Optional[_Union[PaymentDetails.MPesa, _Mapping]] = ..., gcash: _Optional[_Union[PaymentDetails.GCash, _Mapping]] = ..., indian_bank_transfer: _Optional[_Union[PaymentDetails.IndianBankTransfer, _Mapping]] = ..., pesonet: _Optional[_Union[PaymentDetails.Pesonet, _Mapping]] = ..., instapay: _Optional[_Union[PaymentDetails.Instapay, _Mapping]] = ..., pakistan_bank_transfer: _Optional[_Union[PaymentDetails.PakistanBankTransfer, _Mapping]] = ..., pakistan_mobile_wallet: _Optional[_Union[PaymentDetails.PakistanMobileWallet, _Mapping]] = ..., pix: _Optional[_Union[PaymentDetails.Pix, _Mapping]] = ..., african_mobile_money: _Optional[_Union[PaymentDetails.AfricanMobileMoney, _Mapping]] = ..., naps: _Optional[_Union[PaymentDetails.Cnaps, _Mapping]] = ..., nip: _Optional[_Union[PaymentDetails.Nip, _Mapping]] = ..., rtp: _Optional[_Union[PaymentDetails.Rtp, _Mapping]] = ...) -> None: ...
