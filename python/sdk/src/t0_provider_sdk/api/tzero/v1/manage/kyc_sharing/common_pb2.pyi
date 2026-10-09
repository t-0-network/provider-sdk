import datetime

from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import descriptor_pb2 as _descriptor_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from tzero.v1.common import common_pb2 as _common_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ApplicantState(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    APPLICANT_STATE_UNSPECIFIED: _ClassVar[ApplicantState]
    APPLICANT_STATE_DRAFT: _ClassVar[ApplicantState]
    APPLICANT_STATE_REQUESTED: _ClassVar[ApplicantState]
    APPLICANT_STATE_APPROVED: _ClassVar[ApplicantState]
    APPLICANT_STATE_REJECTED: _ClassVar[ApplicantState]

class FormType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    FORM_TYPE_UNSPECIFIED: _ClassVar[FormType]
    FORM_TYPE_KYB: _ClassVar[FormType]
    FORM_TYPE_KYC: _ClassVar[FormType]

class StandardKybFieldType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    STANDARD_KYB_FIELD_TYPE_UNSPECIFIED: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_BUSINESS_NAME: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_BUSINESS_INDUSTRY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTERED_CITY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTERED_POSTAL_CODE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTERED_COUNTRY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_INCORPORATION_DATE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTRATION_NUMBER: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_EMAIL: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_WEBSITE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_LEGAL_PRESENCE_DOCUMENT: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OWNERSHIP_STRUCTURE_DOCUMENT: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_CONTROL_STRUCTURE_DOCUMENT: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_PROOF_OF_ADDRESS: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_BUSINESS_TYPE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_BUSINESS_DESCRIPTION: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_REGISTRATION_AUTHORITY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_TAX_AUTHORITY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OPERATIONAL_ADDRESS_LINE_1: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OPERATIONAL_CITY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OPERATIONAL_STATE_PROVINCE_REGION: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OPERATIONAL_POSTAL_CODE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_OPERATIONAL_COUNTRY: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_PHONE: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_PRIMARY_PURPOSE_OF_ACCOUNT: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_SOURCE_OF_FUNDS_DESCRIPTION: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_HIGH_RISK_ACTIVITIES: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_ESTIMATED_ANNUAL_REVENUE_USD: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_EXPECTED_MONTHLY_PAYMENTS_USD: _ClassVar[StandardKybFieldType]
    STANDARD_KYB_FIELD_TYPE_COMPANY_DETAILS_DOCUMENT: _ClassVar[StandardKybFieldType]

class StandardUboFieldType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    STANDARD_UBO_FIELD_TYPE_UNSPECIFIED: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_FIRST_NAME: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_LAST_NAME: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_EMAIL: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_DATE_OF_BIRTH: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_IDENTITY_DOCUMENT_FRONT: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_IDENTITY_DOCUMENT_BACK: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_ROLES: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_BUSINESS_TITLE: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_NATIONALITY: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_PHONE: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_REGISTERED_CITY: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_REGISTERED_POSTAL_CODE: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_REGISTERED_COUNTRY: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_SHARE_PROPORTION: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_RELATIONSHIP_ESTABLISHED_AT: _ClassVar[StandardUboFieldType]
    STANDARD_UBO_FIELD_TYPE_PROOF_OF_ADDRESS: _ClassVar[StandardUboFieldType]

class StandardKycFieldType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    STANDARD_KYC_FIELD_TYPE_UNSPECIFIED: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_FIRST_NAME: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_LAST_NAME: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_REGISTERED_CITY: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_REGISTERED_POSTAL_CODE: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_REGISTERED_COUNTRY: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_IDENTITY_DOCUMENT_FRONT: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_IDENTITY_DOCUMENT_BACK: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_EMAIL: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_PHONE: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_NATIONALITY: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_DATE_OF_BIRTH: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: _ClassVar[StandardKycFieldType]
    STANDARD_KYC_FIELD_TYPE_PROOF_OF_ADDRESS: _ClassVar[StandardKycFieldType]

class KybIndustry(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    KYB_INDUSTRY_UNSPECIFIED: _ClassVar[KybIndustry]
    KYB_INDUSTRY_AGRICULTURE: _ClassVar[KybIndustry]
    KYB_INDUSTRY_MINING_AND_ENERGY: _ClassVar[KybIndustry]
    KYB_INDUSTRY_CONSTRUCTION: _ClassVar[KybIndustry]
    KYB_INDUSTRY_MANUFACTURING: _ClassVar[KybIndustry]
    KYB_INDUSTRY_UTILITIES: _ClassVar[KybIndustry]
    KYB_INDUSTRY_WHOLESALE_AND_RETAIL_TRADE: _ClassVar[KybIndustry]
    KYB_INDUSTRY_TRANSPORTATION_AND_LOGISTICS: _ClassVar[KybIndustry]
    KYB_INDUSTRY_INFORMATION_AND_TECHNOLOGY: _ClassVar[KybIndustry]
    KYB_INDUSTRY_TELECOMMUNICATIONS_AND_MEDIA: _ClassVar[KybIndustry]
    KYB_INDUSTRY_FINANCIAL_SERVICES: _ClassVar[KybIndustry]
    KYB_INDUSTRY_REAL_ESTATE: _ClassVar[KybIndustry]
    KYB_INDUSTRY_PROFESSIONAL_SERVICES: _ClassVar[KybIndustry]
    KYB_INDUSTRY_HEALTHCARE: _ClassVar[KybIndustry]
    KYB_INDUSTRY_EDUCATION: _ClassVar[KybIndustry]
    KYB_INDUSTRY_HOSPITALITY_AND_FOOD_SERVICES: _ClassVar[KybIndustry]
    KYB_INDUSTRY_ARTS_AND_ENTERTAINMENT: _ClassVar[KybIndustry]
    KYB_INDUSTRY_GOVERNMENT_AND_PUBLIC_SECTOR: _ClassVar[KybIndustry]
    KYB_INDUSTRY_NON_PROFIT: _ClassVar[KybIndustry]

class KybHighRiskActivity(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    KYB_HIGH_RISK_ACTIVITY_UNSPECIFIED: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_TRANSMITS_OR_HOLDS_CUSTOMER_FUNDS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_CURRENCY_EXCHANGE: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_REMITTANCE_SERVICES: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_PAYMENT_PROCESSING: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_GAMBLING_AND_GAMING: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_CANNABIS_AND_REGULATED_SUBSTANCES: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_ADULT_ENTERTAINMENT: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_PRECIOUS_METALS_AND_STONES_DEALERS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_ART_AND_ANTIQUITIES_DEALERS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_ARMS_AND_DEFENSE: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_PAWNSHOPS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_HIGH_VALUE_REAL_ESTATE: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_PRIVATE_BANKING_AND_WEALTH_MANAGEMENT: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_TRUST_AND_COMPANY_SERVICE_PROVIDERS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_CASH_INTENSIVE_BUSINESSES: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_CHARITIES_AND_NGOS: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_PHARMACEUTICALS_AND_CONTROLLED_SUBSTANCES: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_TOBACCO_AND_ALCOHOL: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_SHIPPING_AND_IMPORT_EXPORT: _ClassVar[KybHighRiskActivity]
    KYB_HIGH_RISK_ACTIVITY_NONE_OF_THE_ABOVE: _ClassVar[KybHighRiskActivity]

class KybBusinessType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    KYB_BUSINESS_TYPE_UNSPECIFIED: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_SOLE_PROPRIETORSHIP: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_PARTNERSHIP: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_PRIVATE_LIMITED_COMPANY: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_PUBLIC_LIMITED_COMPANY: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_COOPERATIVE: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_NON_PROFIT_ORGANIZATION: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_TRUST: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_FOUNDATION: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_GOVERNMENT_ENTITY: _ClassVar[KybBusinessType]
    KYB_BUSINESS_TYPE_BRANCH_OR_REPRESENTATIVE_OFFICE: _ClassVar[KybBusinessType]

class KybUboRole(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    KYB_UBO_ROLE_UNSPECIFIED: _ClassVar[KybUboRole]
    KYB_UBO_ROLE_UBO: _ClassVar[KybUboRole]
    KYB_UBO_ROLE_DIRECTOR: _ClassVar[KybUboRole]
    KYB_UBO_ROLE_REPRESENTATIVE: _ClassVar[KybUboRole]
APPLICANT_STATE_UNSPECIFIED: ApplicantState
APPLICANT_STATE_DRAFT: ApplicantState
APPLICANT_STATE_REQUESTED: ApplicantState
APPLICANT_STATE_APPROVED: ApplicantState
APPLICANT_STATE_REJECTED: ApplicantState
FORM_TYPE_UNSPECIFIED: FormType
FORM_TYPE_KYB: FormType
FORM_TYPE_KYC: FormType
STANDARD_KYB_FIELD_TYPE_UNSPECIFIED: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_BUSINESS_NAME: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_BUSINESS_INDUSTRY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTERED_CITY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTERED_POSTAL_CODE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTERED_COUNTRY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_INCORPORATION_DATE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTRATION_NUMBER: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_EMAIL: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_WEBSITE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_LEGAL_PRESENCE_DOCUMENT: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OWNERSHIP_STRUCTURE_DOCUMENT: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_CONTROL_STRUCTURE_DOCUMENT: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_PROOF_OF_ADDRESS: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_BUSINESS_TYPE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_BUSINESS_DESCRIPTION: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_REGISTRATION_AUTHORITY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_TAX_AUTHORITY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OPERATIONAL_ADDRESS_LINE_1: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OPERATIONAL_CITY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OPERATIONAL_STATE_PROVINCE_REGION: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OPERATIONAL_POSTAL_CODE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_OPERATIONAL_COUNTRY: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_PHONE: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_PRIMARY_PURPOSE_OF_ACCOUNT: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_SOURCE_OF_FUNDS_DESCRIPTION: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_HIGH_RISK_ACTIVITIES: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_ESTIMATED_ANNUAL_REVENUE_USD: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_EXPECTED_MONTHLY_PAYMENTS_USD: StandardKybFieldType
STANDARD_KYB_FIELD_TYPE_COMPANY_DETAILS_DOCUMENT: StandardKybFieldType
STANDARD_UBO_FIELD_TYPE_UNSPECIFIED: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_FIRST_NAME: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_LAST_NAME: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_EMAIL: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_DATE_OF_BIRTH: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_IDENTITY_DOCUMENT_FRONT: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_IDENTITY_DOCUMENT_BACK: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_ROLES: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_BUSINESS_TITLE: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_NATIONALITY: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_PHONE: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_REGISTERED_CITY: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_REGISTERED_POSTAL_CODE: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_REGISTERED_COUNTRY: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_SHARE_PROPORTION: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_RELATIONSHIP_ESTABLISHED_AT: StandardUboFieldType
STANDARD_UBO_FIELD_TYPE_PROOF_OF_ADDRESS: StandardUboFieldType
STANDARD_KYC_FIELD_TYPE_UNSPECIFIED: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_FIRST_NAME: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_LAST_NAME: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_REGISTERED_ADDRESS_LINE_1: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_REGISTERED_CITY: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_REGISTERED_STATE_PROVINCE_REGION: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_REGISTERED_POSTAL_CODE: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_REGISTERED_COUNTRY: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_IDENTITY_DOCUMENT_FRONT: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_IDENTITY_DOCUMENT_BACK: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_EMAIL: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_PHONE: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_NATIONALITY: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_DATE_OF_BIRTH: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_TAX_IDENTIFICATION_NUMBER: StandardKycFieldType
STANDARD_KYC_FIELD_TYPE_PROOF_OF_ADDRESS: StandardKycFieldType
KYB_INDUSTRY_UNSPECIFIED: KybIndustry
KYB_INDUSTRY_AGRICULTURE: KybIndustry
KYB_INDUSTRY_MINING_AND_ENERGY: KybIndustry
KYB_INDUSTRY_CONSTRUCTION: KybIndustry
KYB_INDUSTRY_MANUFACTURING: KybIndustry
KYB_INDUSTRY_UTILITIES: KybIndustry
KYB_INDUSTRY_WHOLESALE_AND_RETAIL_TRADE: KybIndustry
KYB_INDUSTRY_TRANSPORTATION_AND_LOGISTICS: KybIndustry
KYB_INDUSTRY_INFORMATION_AND_TECHNOLOGY: KybIndustry
KYB_INDUSTRY_TELECOMMUNICATIONS_AND_MEDIA: KybIndustry
KYB_INDUSTRY_FINANCIAL_SERVICES: KybIndustry
KYB_INDUSTRY_REAL_ESTATE: KybIndustry
KYB_INDUSTRY_PROFESSIONAL_SERVICES: KybIndustry
KYB_INDUSTRY_HEALTHCARE: KybIndustry
KYB_INDUSTRY_EDUCATION: KybIndustry
KYB_INDUSTRY_HOSPITALITY_AND_FOOD_SERVICES: KybIndustry
KYB_INDUSTRY_ARTS_AND_ENTERTAINMENT: KybIndustry
KYB_INDUSTRY_GOVERNMENT_AND_PUBLIC_SECTOR: KybIndustry
KYB_INDUSTRY_NON_PROFIT: KybIndustry
KYB_HIGH_RISK_ACTIVITY_UNSPECIFIED: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_TRANSMITS_OR_HOLDS_CUSTOMER_FUNDS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_CURRENCY_EXCHANGE: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_REMITTANCE_SERVICES: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_PAYMENT_PROCESSING: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_GAMBLING_AND_GAMING: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_CANNABIS_AND_REGULATED_SUBSTANCES: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_ADULT_ENTERTAINMENT: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_PRECIOUS_METALS_AND_STONES_DEALERS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_ART_AND_ANTIQUITIES_DEALERS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_ARMS_AND_DEFENSE: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_PAWNSHOPS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_HIGH_VALUE_REAL_ESTATE: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_PRIVATE_BANKING_AND_WEALTH_MANAGEMENT: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_TRUST_AND_COMPANY_SERVICE_PROVIDERS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_CASH_INTENSIVE_BUSINESSES: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_CHARITIES_AND_NGOS: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_PHARMACEUTICALS_AND_CONTROLLED_SUBSTANCES: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_TOBACCO_AND_ALCOHOL: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_SHIPPING_AND_IMPORT_EXPORT: KybHighRiskActivity
KYB_HIGH_RISK_ACTIVITY_NONE_OF_THE_ABOVE: KybHighRiskActivity
KYB_BUSINESS_TYPE_UNSPECIFIED: KybBusinessType
KYB_BUSINESS_TYPE_SOLE_PROPRIETORSHIP: KybBusinessType
KYB_BUSINESS_TYPE_PARTNERSHIP: KybBusinessType
KYB_BUSINESS_TYPE_PRIVATE_LIMITED_COMPANY: KybBusinessType
KYB_BUSINESS_TYPE_PUBLIC_LIMITED_COMPANY: KybBusinessType
KYB_BUSINESS_TYPE_COOPERATIVE: KybBusinessType
KYB_BUSINESS_TYPE_NON_PROFIT_ORGANIZATION: KybBusinessType
KYB_BUSINESS_TYPE_TRUST: KybBusinessType
KYB_BUSINESS_TYPE_FOUNDATION: KybBusinessType
KYB_BUSINESS_TYPE_GOVERNMENT_ENTITY: KybBusinessType
KYB_BUSINESS_TYPE_BRANCH_OR_REPRESENTATIVE_OFFICE: KybBusinessType
KYB_UBO_ROLE_UNSPECIFIED: KybUboRole
KYB_UBO_ROLE_UBO: KybUboRole
KYB_UBO_ROLE_DIRECTOR: KybUboRole
KYB_UBO_ROLE_REPRESENTATIVE: KybUboRole
NETWORK_MANDATORY_FIELD_NUMBER: _ClassVar[int]
network_mandatory: _descriptor.FieldDescriptor

class FileValue(_message.Message):
    __slots__ = ("file_id",)
    FILE_ID_FIELD_NUMBER: _ClassVar[int]
    file_id: int
    def __init__(self, file_id: _Optional[int] = ...) -> None: ...

class KybApplicant(_message.Message):
    __slots__ = ("business_name", "business_industry", "registered_address_line_1", "registered_city", "registered_state_province_region", "registered_postal_code", "registered_country", "incorporation_date", "registration_number", "tax_identification_number", "email", "website", "legal_presence_document", "ownership_structure_document", "control_structure_document", "proof_of_address", "business_type", "business_description", "registration_authority", "tax_authority", "operational_address_line_1", "operational_city", "operational_state_province_region", "operational_postal_code", "operational_country", "phone", "primary_purpose_of_account", "source_of_funds_description", "high_risk_activities", "estimated_annual_revenue_usd", "expected_monthly_payments_usd", "company_details_document", "custom_fields")
    BUSINESS_NAME_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_INDUSTRY_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_ADDRESS_LINE_1_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_CITY_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_STATE_PROVINCE_REGION_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_COUNTRY_FIELD_NUMBER: _ClassVar[int]
    INCORPORATION_DATE_FIELD_NUMBER: _ClassVar[int]
    REGISTRATION_NUMBER_FIELD_NUMBER: _ClassVar[int]
    TAX_IDENTIFICATION_NUMBER_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    WEBSITE_FIELD_NUMBER: _ClassVar[int]
    LEGAL_PRESENCE_DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    OWNERSHIP_STRUCTURE_DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    CONTROL_STRUCTURE_DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    PROOF_OF_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_TYPE_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    REGISTRATION_AUTHORITY_FIELD_NUMBER: _ClassVar[int]
    TAX_AUTHORITY_FIELD_NUMBER: _ClassVar[int]
    OPERATIONAL_ADDRESS_LINE_1_FIELD_NUMBER: _ClassVar[int]
    OPERATIONAL_CITY_FIELD_NUMBER: _ClassVar[int]
    OPERATIONAL_STATE_PROVINCE_REGION_FIELD_NUMBER: _ClassVar[int]
    OPERATIONAL_POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    OPERATIONAL_COUNTRY_FIELD_NUMBER: _ClassVar[int]
    PHONE_FIELD_NUMBER: _ClassVar[int]
    PRIMARY_PURPOSE_OF_ACCOUNT_FIELD_NUMBER: _ClassVar[int]
    SOURCE_OF_FUNDS_DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    HIGH_RISK_ACTIVITIES_FIELD_NUMBER: _ClassVar[int]
    ESTIMATED_ANNUAL_REVENUE_USD_FIELD_NUMBER: _ClassVar[int]
    EXPECTED_MONTHLY_PAYMENTS_USD_FIELD_NUMBER: _ClassVar[int]
    COMPANY_DETAILS_DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
    business_name: str
    business_industry: KybIndustry
    registered_address_line_1: str
    registered_city: str
    registered_state_province_region: str
    registered_postal_code: str
    registered_country: str
    incorporation_date: _timestamp_pb2.Timestamp
    registration_number: str
    tax_identification_number: str
    email: str
    website: str
    legal_presence_document: FileValue
    ownership_structure_document: FileValue
    control_structure_document: FileValue
    proof_of_address: FileValue
    business_type: KybBusinessType
    business_description: str
    registration_authority: str
    tax_authority: str
    operational_address_line_1: str
    operational_city: str
    operational_state_province_region: str
    operational_postal_code: str
    operational_country: str
    phone: str
    primary_purpose_of_account: str
    source_of_funds_description: str
    high_risk_activities: _containers.RepeatedScalarFieldContainer[KybHighRiskActivity]
    estimated_annual_revenue_usd: _common_pb2.Decimal
    expected_monthly_payments_usd: _common_pb2.Decimal
    company_details_document: FileValue
    custom_fields: _containers.RepeatedCompositeFieldContainer[CustomFieldValue]
    def __init__(self, business_name: _Optional[str] = ..., business_industry: _Optional[_Union[KybIndustry, str]] = ..., registered_address_line_1: _Optional[str] = ..., registered_city: _Optional[str] = ..., registered_state_province_region: _Optional[str] = ..., registered_postal_code: _Optional[str] = ..., registered_country: _Optional[str] = ..., incorporation_date: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., registration_number: _Optional[str] = ..., tax_identification_number: _Optional[str] = ..., email: _Optional[str] = ..., website: _Optional[str] = ..., legal_presence_document: _Optional[_Union[FileValue, _Mapping]] = ..., ownership_structure_document: _Optional[_Union[FileValue, _Mapping]] = ..., control_structure_document: _Optional[_Union[FileValue, _Mapping]] = ..., proof_of_address: _Optional[_Union[FileValue, _Mapping]] = ..., business_type: _Optional[_Union[KybBusinessType, str]] = ..., business_description: _Optional[str] = ..., registration_authority: _Optional[str] = ..., tax_authority: _Optional[str] = ..., operational_address_line_1: _Optional[str] = ..., operational_city: _Optional[str] = ..., operational_state_province_region: _Optional[str] = ..., operational_postal_code: _Optional[str] = ..., operational_country: _Optional[str] = ..., phone: _Optional[str] = ..., primary_purpose_of_account: _Optional[str] = ..., source_of_funds_description: _Optional[str] = ..., high_risk_activities: _Optional[_Iterable[_Union[KybHighRiskActivity, str]]] = ..., estimated_annual_revenue_usd: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expected_monthly_payments_usd: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., company_details_document: _Optional[_Union[FileValue, _Mapping]] = ..., custom_fields: _Optional[_Iterable[_Union[CustomFieldValue, _Mapping]]] = ...) -> None: ...

class KybUbo(_message.Message):
    __slots__ = ("first_name", "last_name", "email", "date_of_birth", "identity_document_front", "identity_document_back", "roles", "business_title", "nationality", "phone", "registered_address_line_1", "registered_city", "registered_state_province_region", "registered_postal_code", "registered_country", "tax_identification_number", "share_proportion", "relationship_established_at", "proof_of_address", "custom_fields")
    FIRST_NAME_FIELD_NUMBER: _ClassVar[int]
    LAST_NAME_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    DATE_OF_BIRTH_FIELD_NUMBER: _ClassVar[int]
    IDENTITY_DOCUMENT_FRONT_FIELD_NUMBER: _ClassVar[int]
    IDENTITY_DOCUMENT_BACK_FIELD_NUMBER: _ClassVar[int]
    ROLES_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_TITLE_FIELD_NUMBER: _ClassVar[int]
    NATIONALITY_FIELD_NUMBER: _ClassVar[int]
    PHONE_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_ADDRESS_LINE_1_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_CITY_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_STATE_PROVINCE_REGION_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_COUNTRY_FIELD_NUMBER: _ClassVar[int]
    TAX_IDENTIFICATION_NUMBER_FIELD_NUMBER: _ClassVar[int]
    SHARE_PROPORTION_FIELD_NUMBER: _ClassVar[int]
    RELATIONSHIP_ESTABLISHED_AT_FIELD_NUMBER: _ClassVar[int]
    PROOF_OF_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
    first_name: str
    last_name: str
    email: str
    date_of_birth: _timestamp_pb2.Timestamp
    identity_document_front: FileValue
    identity_document_back: FileValue
    roles: _containers.RepeatedScalarFieldContainer[KybUboRole]
    business_title: str
    nationality: str
    phone: str
    registered_address_line_1: str
    registered_city: str
    registered_state_province_region: str
    registered_postal_code: str
    registered_country: str
    tax_identification_number: str
    share_proportion: _common_pb2.Decimal
    relationship_established_at: _timestamp_pb2.Timestamp
    proof_of_address: FileValue
    custom_fields: _containers.RepeatedCompositeFieldContainer[CustomFieldValue]
    def __init__(self, first_name: _Optional[str] = ..., last_name: _Optional[str] = ..., email: _Optional[str] = ..., date_of_birth: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., identity_document_front: _Optional[_Union[FileValue, _Mapping]] = ..., identity_document_back: _Optional[_Union[FileValue, _Mapping]] = ..., roles: _Optional[_Iterable[_Union[KybUboRole, str]]] = ..., business_title: _Optional[str] = ..., nationality: _Optional[str] = ..., phone: _Optional[str] = ..., registered_address_line_1: _Optional[str] = ..., registered_city: _Optional[str] = ..., registered_state_province_region: _Optional[str] = ..., registered_postal_code: _Optional[str] = ..., registered_country: _Optional[str] = ..., tax_identification_number: _Optional[str] = ..., share_proportion: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., relationship_established_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., proof_of_address: _Optional[_Union[FileValue, _Mapping]] = ..., custom_fields: _Optional[_Iterable[_Union[CustomFieldValue, _Mapping]]] = ...) -> None: ...

class KycApplicant(_message.Message):
    __slots__ = ("first_name", "last_name", "registered_address_line_1", "registered_city", "registered_state_province_region", "registered_postal_code", "registered_country", "identity_document_front", "identity_document_back", "email", "phone", "nationality", "date_of_birth", "tax_identification_number", "proof_of_address", "custom_fields")
    FIRST_NAME_FIELD_NUMBER: _ClassVar[int]
    LAST_NAME_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_ADDRESS_LINE_1_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_CITY_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_STATE_PROVINCE_REGION_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    REGISTERED_COUNTRY_FIELD_NUMBER: _ClassVar[int]
    IDENTITY_DOCUMENT_FRONT_FIELD_NUMBER: _ClassVar[int]
    IDENTITY_DOCUMENT_BACK_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    PHONE_FIELD_NUMBER: _ClassVar[int]
    NATIONALITY_FIELD_NUMBER: _ClassVar[int]
    DATE_OF_BIRTH_FIELD_NUMBER: _ClassVar[int]
    TAX_IDENTIFICATION_NUMBER_FIELD_NUMBER: _ClassVar[int]
    PROOF_OF_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
    first_name: str
    last_name: str
    registered_address_line_1: str
    registered_city: str
    registered_state_province_region: str
    registered_postal_code: str
    registered_country: str
    identity_document_front: FileValue
    identity_document_back: FileValue
    email: str
    phone: str
    nationality: str
    date_of_birth: _timestamp_pb2.Timestamp
    tax_identification_number: str
    proof_of_address: FileValue
    custom_fields: _containers.RepeatedCompositeFieldContainer[CustomFieldValue]
    def __init__(self, first_name: _Optional[str] = ..., last_name: _Optional[str] = ..., registered_address_line_1: _Optional[str] = ..., registered_city: _Optional[str] = ..., registered_state_province_region: _Optional[str] = ..., registered_postal_code: _Optional[str] = ..., registered_country: _Optional[str] = ..., identity_document_front: _Optional[_Union[FileValue, _Mapping]] = ..., identity_document_back: _Optional[_Union[FileValue, _Mapping]] = ..., email: _Optional[str] = ..., phone: _Optional[str] = ..., nationality: _Optional[str] = ..., date_of_birth: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., tax_identification_number: _Optional[str] = ..., proof_of_address: _Optional[_Union[FileValue, _Mapping]] = ..., custom_fields: _Optional[_Iterable[_Union[CustomFieldValue, _Mapping]]] = ...) -> None: ...

class CustomFieldValue(_message.Message):
    __slots__ = ("identifier", "string", "date", "number", "single_select", "multi_select", "file")
    class SingleSelect(_message.Message):
        __slots__ = ("key",)
        KEY_FIELD_NUMBER: _ClassVar[int]
        key: str
        def __init__(self, key: _Optional[str] = ...) -> None: ...
    class MultiSelect(_message.Message):
        __slots__ = ("keys",)
        KEYS_FIELD_NUMBER: _ClassVar[int]
        keys: _containers.RepeatedScalarFieldContainer[str]
        def __init__(self, keys: _Optional[_Iterable[str]] = ...) -> None: ...
    IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    STRING_FIELD_NUMBER: _ClassVar[int]
    DATE_FIELD_NUMBER: _ClassVar[int]
    NUMBER_FIELD_NUMBER: _ClassVar[int]
    SINGLE_SELECT_FIELD_NUMBER: _ClassVar[int]
    MULTI_SELECT_FIELD_NUMBER: _ClassVar[int]
    FILE_FIELD_NUMBER: _ClassVar[int]
    identifier: str
    string: str
    date: _timestamp_pb2.Timestamp
    number: _common_pb2.Decimal
    single_select: CustomFieldValue.SingleSelect
    multi_select: CustomFieldValue.MultiSelect
    file: FileValue
    def __init__(self, identifier: _Optional[str] = ..., string: _Optional[str] = ..., date: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., number: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., single_select: _Optional[_Union[CustomFieldValue.SingleSelect, _Mapping]] = ..., multi_select: _Optional[_Union[CustomFieldValue.MultiSelect, _Mapping]] = ..., file: _Optional[_Union[FileValue, _Mapping]] = ...) -> None: ...

class Page(_message.Message):
    __slots__ = ("page", "size")
    PAGE_FIELD_NUMBER: _ClassVar[int]
    SIZE_FIELD_NUMBER: _ClassVar[int]
    page: int
    size: int
    def __init__(self, page: _Optional[int] = ..., size: _Optional[int] = ...) -> None: ...

class PageInfo(_message.Message):
    __slots__ = ("current_page", "page_size", "has_next")
    CURRENT_PAGE_FIELD_NUMBER: _ClassVar[int]
    PAGE_SIZE_FIELD_NUMBER: _ClassVar[int]
    HAS_NEXT_FIELD_NUMBER: _ClassVar[int]
    current_page: int
    page_size: int
    has_next: bool
    def __init__(self, current_page: _Optional[int] = ..., page_size: _Optional[int] = ..., has_next: _Optional[bool] = ...) -> None: ...
