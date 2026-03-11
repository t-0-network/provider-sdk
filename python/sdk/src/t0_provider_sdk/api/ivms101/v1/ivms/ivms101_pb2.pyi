from ivms101.v1.ivms import enum_pb2 as _enum_pb2
from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class Person(_message.Message):
    __slots__ = ("natural_person", "legal_person")
    NATURAL_PERSON_FIELD_NUMBER: _ClassVar[int]
    LEGAL_PERSON_FIELD_NUMBER: _ClassVar[int]
    natural_person: NaturalPerson
    legal_person: LegalPerson
    def __init__(self, natural_person: _Optional[_Union[NaturalPerson, _Mapping]] = ..., legal_person: _Optional[_Union[LegalPerson, _Mapping]] = ...) -> None: ...

class NaturalPerson(_message.Message):
    __slots__ = ("name", "geographic_addresses", "national_identification", "customer_identification", "date_and_place_of_birth", "country_of_residence")
    NAME_FIELD_NUMBER: _ClassVar[int]
    GEOGRAPHIC_ADDRESSES_FIELD_NUMBER: _ClassVar[int]
    NATIONAL_IDENTIFICATION_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_IDENTIFICATION_FIELD_NUMBER: _ClassVar[int]
    DATE_AND_PLACE_OF_BIRTH_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_OF_RESIDENCE_FIELD_NUMBER: _ClassVar[int]
    name: NaturalPersonName
    geographic_addresses: _containers.RepeatedCompositeFieldContainer[Address]
    national_identification: NationalIdentification
    customer_identification: str
    date_and_place_of_birth: DateAndPlaceOfBirth
    country_of_residence: str
    def __init__(self, name: _Optional[_Union[NaturalPersonName, _Mapping]] = ..., geographic_addresses: _Optional[_Iterable[_Union[Address, _Mapping]]] = ..., national_identification: _Optional[_Union[NationalIdentification, _Mapping]] = ..., customer_identification: _Optional[str] = ..., date_and_place_of_birth: _Optional[_Union[DateAndPlaceOfBirth, _Mapping]] = ..., country_of_residence: _Optional[str] = ...) -> None: ...

class NaturalPersonName(_message.Message):
    __slots__ = ("name_identifiers", "local_name_identifiers", "phonetic_name_identifiers")
    NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    LOCAL_NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    PHONETIC_NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    name_identifiers: _containers.RepeatedCompositeFieldContainer[NaturalPersonNameId]
    local_name_identifiers: _containers.RepeatedCompositeFieldContainer[LocalNaturalPersonNameId]
    phonetic_name_identifiers: _containers.RepeatedCompositeFieldContainer[LocalNaturalPersonNameId]
    def __init__(self, name_identifiers: _Optional[_Iterable[_Union[NaturalPersonNameId, _Mapping]]] = ..., local_name_identifiers: _Optional[_Iterable[_Union[LocalNaturalPersonNameId, _Mapping]]] = ..., phonetic_name_identifiers: _Optional[_Iterable[_Union[LocalNaturalPersonNameId, _Mapping]]] = ...) -> None: ...

class NaturalPersonNameId(_message.Message):
    __slots__ = ("primary_identifier", "secondary_identifier", "name_identifier_type")
    PRIMARY_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    SECONDARY_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    NAME_IDENTIFIER_TYPE_FIELD_NUMBER: _ClassVar[int]
    primary_identifier: str
    secondary_identifier: str
    name_identifier_type: _enum_pb2.NaturalPersonNameTypeCode
    def __init__(self, primary_identifier: _Optional[str] = ..., secondary_identifier: _Optional[str] = ..., name_identifier_type: _Optional[_Union[_enum_pb2.NaturalPersonNameTypeCode, str]] = ...) -> None: ...

class LocalNaturalPersonNameId(_message.Message):
    __slots__ = ("primary_identifier", "secondary_identifier", "name_identifier_type")
    PRIMARY_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    SECONDARY_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    NAME_IDENTIFIER_TYPE_FIELD_NUMBER: _ClassVar[int]
    primary_identifier: str
    secondary_identifier: str
    name_identifier_type: _enum_pb2.NaturalPersonNameTypeCode
    def __init__(self, primary_identifier: _Optional[str] = ..., secondary_identifier: _Optional[str] = ..., name_identifier_type: _Optional[_Union[_enum_pb2.NaturalPersonNameTypeCode, str]] = ...) -> None: ...

class Address(_message.Message):
    __slots__ = ("address_type", "department", "sub_department", "street_name", "building_number", "building_name", "floor", "post_box", "room", "post_code", "town_name", "town_location_name", "district_name", "country_sub_division", "address_line", "country")
    ADDRESS_TYPE_FIELD_NUMBER: _ClassVar[int]
    DEPARTMENT_FIELD_NUMBER: _ClassVar[int]
    SUB_DEPARTMENT_FIELD_NUMBER: _ClassVar[int]
    STREET_NAME_FIELD_NUMBER: _ClassVar[int]
    BUILDING_NUMBER_FIELD_NUMBER: _ClassVar[int]
    BUILDING_NAME_FIELD_NUMBER: _ClassVar[int]
    FLOOR_FIELD_NUMBER: _ClassVar[int]
    POST_BOX_FIELD_NUMBER: _ClassVar[int]
    ROOM_FIELD_NUMBER: _ClassVar[int]
    POST_CODE_FIELD_NUMBER: _ClassVar[int]
    TOWN_NAME_FIELD_NUMBER: _ClassVar[int]
    TOWN_LOCATION_NAME_FIELD_NUMBER: _ClassVar[int]
    DISTRICT_NAME_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_SUB_DIVISION_FIELD_NUMBER: _ClassVar[int]
    ADDRESS_LINE_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_FIELD_NUMBER: _ClassVar[int]
    address_type: _enum_pb2.AddressTypeCode
    department: str
    sub_department: str
    street_name: str
    building_number: str
    building_name: str
    floor: str
    post_box: str
    room: str
    post_code: str
    town_name: str
    town_location_name: str
    district_name: str
    country_sub_division: str
    address_line: _containers.RepeatedScalarFieldContainer[str]
    country: str
    def __init__(self, address_type: _Optional[_Union[_enum_pb2.AddressTypeCode, str]] = ..., department: _Optional[str] = ..., sub_department: _Optional[str] = ..., street_name: _Optional[str] = ..., building_number: _Optional[str] = ..., building_name: _Optional[str] = ..., floor: _Optional[str] = ..., post_box: _Optional[str] = ..., room: _Optional[str] = ..., post_code: _Optional[str] = ..., town_name: _Optional[str] = ..., town_location_name: _Optional[str] = ..., district_name: _Optional[str] = ..., country_sub_division: _Optional[str] = ..., address_line: _Optional[_Iterable[str]] = ..., country: _Optional[str] = ...) -> None: ...

class DateAndPlaceOfBirth(_message.Message):
    __slots__ = ("date_of_birth", "place_of_birth")
    DATE_OF_BIRTH_FIELD_NUMBER: _ClassVar[int]
    PLACE_OF_BIRTH_FIELD_NUMBER: _ClassVar[int]
    date_of_birth: str
    place_of_birth: str
    def __init__(self, date_of_birth: _Optional[str] = ..., place_of_birth: _Optional[str] = ...) -> None: ...

class NationalIdentification(_message.Message):
    __slots__ = ("national_identifier", "national_identifier_type", "country_of_issue", "registration_authority")
    NATIONAL_IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    NATIONAL_IDENTIFIER_TYPE_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_OF_ISSUE_FIELD_NUMBER: _ClassVar[int]
    REGISTRATION_AUTHORITY_FIELD_NUMBER: _ClassVar[int]
    national_identifier: str
    national_identifier_type: _enum_pb2.NationalIdentifierTypeCode
    country_of_issue: str
    registration_authority: str
    def __init__(self, national_identifier: _Optional[str] = ..., national_identifier_type: _Optional[_Union[_enum_pb2.NationalIdentifierTypeCode, str]] = ..., country_of_issue: _Optional[str] = ..., registration_authority: _Optional[str] = ...) -> None: ...

class LegalPerson(_message.Message):
    __slots__ = ("name", "geographic_addresses", "customer_number", "national_identification", "country_of_registration")
    NAME_FIELD_NUMBER: _ClassVar[int]
    GEOGRAPHIC_ADDRESSES_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_NUMBER_FIELD_NUMBER: _ClassVar[int]
    NATIONAL_IDENTIFICATION_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_OF_REGISTRATION_FIELD_NUMBER: _ClassVar[int]
    name: LegalPersonName
    geographic_addresses: _containers.RepeatedCompositeFieldContainer[Address]
    customer_number: str
    national_identification: NationalIdentification
    country_of_registration: str
    def __init__(self, name: _Optional[_Union[LegalPersonName, _Mapping]] = ..., geographic_addresses: _Optional[_Iterable[_Union[Address, _Mapping]]] = ..., customer_number: _Optional[str] = ..., national_identification: _Optional[_Union[NationalIdentification, _Mapping]] = ..., country_of_registration: _Optional[str] = ...) -> None: ...

class LegalPersonName(_message.Message):
    __slots__ = ("name_identifiers", "local_name_identifiers", "phonetic_name_identifiers")
    NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    LOCAL_NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    PHONETIC_NAME_IDENTIFIERS_FIELD_NUMBER: _ClassVar[int]
    name_identifiers: _containers.RepeatedCompositeFieldContainer[LegalPersonNameId]
    local_name_identifiers: _containers.RepeatedCompositeFieldContainer[LocalLegalPersonNameId]
    phonetic_name_identifiers: _containers.RepeatedCompositeFieldContainer[LocalLegalPersonNameId]
    def __init__(self, name_identifiers: _Optional[_Iterable[_Union[LegalPersonNameId, _Mapping]]] = ..., local_name_identifiers: _Optional[_Iterable[_Union[LocalLegalPersonNameId, _Mapping]]] = ..., phonetic_name_identifiers: _Optional[_Iterable[_Union[LocalLegalPersonNameId, _Mapping]]] = ...) -> None: ...

class LegalPersonNameId(_message.Message):
    __slots__ = ("legal_person_name", "legal_person_name_identifier_type")
    LEGAL_PERSON_NAME_FIELD_NUMBER: _ClassVar[int]
    LEGAL_PERSON_NAME_IDENTIFIER_TYPE_FIELD_NUMBER: _ClassVar[int]
    legal_person_name: str
    legal_person_name_identifier_type: _enum_pb2.LegalPersonNameTypeCode
    def __init__(self, legal_person_name: _Optional[str] = ..., legal_person_name_identifier_type: _Optional[_Union[_enum_pb2.LegalPersonNameTypeCode, str]] = ...) -> None: ...

class LocalLegalPersonNameId(_message.Message):
    __slots__ = ("legal_person_name", "legal_person_name_identifier_type")
    LEGAL_PERSON_NAME_FIELD_NUMBER: _ClassVar[int]
    LEGAL_PERSON_NAME_IDENTIFIER_TYPE_FIELD_NUMBER: _ClassVar[int]
    legal_person_name: str
    legal_person_name_identifier_type: _enum_pb2.LegalPersonNameTypeCode
    def __init__(self, legal_person_name: _Optional[str] = ..., legal_person_name_identifier_type: _Optional[_Union[_enum_pb2.LegalPersonNameTypeCode, str]] = ...) -> None: ...
