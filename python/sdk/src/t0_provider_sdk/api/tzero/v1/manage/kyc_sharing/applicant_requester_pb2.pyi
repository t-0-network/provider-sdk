from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.manage.kyc_sharing import common_pb2 as _common_pb2
from tzero.v1.manage.kyc_sharing import form_pb2 as _form_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CreateRequesterKybApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class CreateRequesterKybApplicantResponse(_message.Message):
    __slots__ = ("form_version", "state")
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    STATE_FIELD_NUMBER: _ClassVar[int]
    form_version: int
    state: _common_pb2.ApplicantState
    def __init__(self, form_version: _Optional[int] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ...) -> None: ...

class GetRequesterKybApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class GetRequesterKybApplicantResponse(_message.Message):
    __slots__ = ("form", "applicant", "ubos", "form_version", "state", "reject_reason", "version")
    FORM_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    UBOS_FIELD_NUMBER: _ClassVar[int]
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    STATE_FIELD_NUMBER: _ClassVar[int]
    REJECT_REASON_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KybForm
    applicant: _common_pb2.KybApplicant
    ubos: _containers.RepeatedCompositeFieldContainer[_common_pb2.KybUbo]
    form_version: int
    state: _common_pb2.ApplicantState
    reject_reason: str
    version: int
    def __init__(self, form: _Optional[_Union[_form_pb2.KybForm, _Mapping]] = ..., applicant: _Optional[_Union[_common_pb2.KybApplicant, _Mapping]] = ..., ubos: _Optional[_Iterable[_Union[_common_pb2.KybUbo, _Mapping]]] = ..., form_version: _Optional[int] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ..., reject_reason: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class CreateRequesterKycApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class CreateRequesterKycApplicantResponse(_message.Message):
    __slots__ = ("form_version", "state")
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    STATE_FIELD_NUMBER: _ClassVar[int]
    form_version: int
    state: _common_pb2.ApplicantState
    def __init__(self, form_version: _Optional[int] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ...) -> None: ...

class GetRequesterKycApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class GetRequesterKycApplicantResponse(_message.Message):
    __slots__ = ("form", "applicant", "form_version", "state", "reject_reason", "version")
    FORM_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    STATE_FIELD_NUMBER: _ClassVar[int]
    REJECT_REASON_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KycForm
    applicant: _common_pb2.KycApplicant
    form_version: int
    state: _common_pb2.ApplicantState
    reject_reason: str
    version: int
    def __init__(self, form: _Optional[_Union[_form_pb2.KycForm, _Mapping]] = ..., applicant: _Optional[_Union[_common_pb2.KycApplicant, _Mapping]] = ..., form_version: _Optional[int] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ..., reject_reason: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class SaveKybApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id", "applicant", "ubos")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    UBOS_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    applicant: _common_pb2.KybApplicant
    ubos: _containers.RepeatedCompositeFieldContainer[_common_pb2.KybUbo]
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ..., applicant: _Optional[_Union[_common_pb2.KybApplicant, _Mapping]] = ..., ubos: _Optional[_Iterable[_Union[_common_pb2.KybUbo, _Mapping]]] = ...) -> None: ...

class SaveKybApplicantResponse(_message.Message):
    __slots__ = ("form", "applicant", "ubos", "form_version", "version")
    FORM_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    UBOS_FIELD_NUMBER: _ClassVar[int]
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KybForm
    applicant: _common_pb2.KybApplicant
    ubos: _containers.RepeatedCompositeFieldContainer[_common_pb2.KybUbo]
    form_version: int
    version: int
    def __init__(self, form: _Optional[_Union[_form_pb2.KybForm, _Mapping]] = ..., applicant: _Optional[_Union[_common_pb2.KybApplicant, _Mapping]] = ..., ubos: _Optional[_Iterable[_Union[_common_pb2.KybUbo, _Mapping]]] = ..., form_version: _Optional[int] = ..., version: _Optional[int] = ...) -> None: ...

class SaveKycApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id", "applicant")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    applicant: _common_pb2.KycApplicant
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ..., applicant: _Optional[_Union[_common_pb2.KycApplicant, _Mapping]] = ...) -> None: ...

class SaveKycApplicantResponse(_message.Message):
    __slots__ = ("form", "applicant", "form_version", "version")
    FORM_FIELD_NUMBER: _ClassVar[int]
    APPLICANT_FIELD_NUMBER: _ClassVar[int]
    FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KycForm
    applicant: _common_pb2.KycApplicant
    form_version: int
    version: int
    def __init__(self, form: _Optional[_Union[_form_pb2.KycForm, _Mapping]] = ..., applicant: _Optional[_Union[_common_pb2.KycApplicant, _Mapping]] = ..., form_version: _Optional[int] = ..., version: _Optional[int] = ...) -> None: ...

class DeleteApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class DeleteApplicantResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class SubmitApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id", "version")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    version: int
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class SubmitApplicantResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class WithdrawApplicantRequest(_message.Message):
    __slots__ = ("payout_provider_id", "client_id", "version")
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    payout_provider_id: int
    client_id: str
    version: int
    def __init__(self, payout_provider_id: _Optional[int] = ..., client_id: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class WithdrawApplicantResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ListRequesterApplicantsRequest(_message.Message):
    __slots__ = ("form_type", "payout_provider_id", "states", "page")
    FORM_TYPE_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
    STATES_FIELD_NUMBER: _ClassVar[int]
    PAGE_FIELD_NUMBER: _ClassVar[int]
    form_type: _common_pb2.FormType
    payout_provider_id: int
    states: _containers.RepeatedScalarFieldContainer[_common_pb2.ApplicantState]
    page: _common_pb2.Page
    def __init__(self, form_type: _Optional[_Union[_common_pb2.FormType, str]] = ..., payout_provider_id: _Optional[int] = ..., states: _Optional[_Iterable[_Union[_common_pb2.ApplicantState, str]]] = ..., page: _Optional[_Union[_common_pb2.Page, _Mapping]] = ...) -> None: ...

class ListRequesterApplicantsResponse(_message.Message):
    __slots__ = ("applicants", "page_info")
    class Applicant(_message.Message):
        __slots__ = ("payout_provider_id", "payout_provider_name", "client_id", "state", "form_type", "form_version")
        PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_PROVIDER_NAME_FIELD_NUMBER: _ClassVar[int]
        CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
        STATE_FIELD_NUMBER: _ClassVar[int]
        FORM_TYPE_FIELD_NUMBER: _ClassVar[int]
        FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
        payout_provider_id: int
        payout_provider_name: str
        client_id: str
        state: _common_pb2.ApplicantState
        form_type: _common_pb2.FormType
        form_version: int
        def __init__(self, payout_provider_id: _Optional[int] = ..., payout_provider_name: _Optional[str] = ..., client_id: _Optional[str] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ..., form_type: _Optional[_Union[_common_pb2.FormType, str]] = ..., form_version: _Optional[int] = ...) -> None: ...
    APPLICANTS_FIELD_NUMBER: _ClassVar[int]
    PAGE_INFO_FIELD_NUMBER: _ClassVar[int]
    applicants: _containers.RepeatedCompositeFieldContainer[ListRequesterApplicantsResponse.Applicant]
    page_info: _common_pb2.PageInfo
    def __init__(self, applicants: _Optional[_Iterable[_Union[ListRequesterApplicantsResponse.Applicant, _Mapping]]] = ..., page_info: _Optional[_Union[_common_pb2.PageInfo, _Mapping]] = ...) -> None: ...

class ListProviderFormsRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ListProviderFormsResponse(_message.Message):
    __slots__ = ("provider_forms",)
    class ProviderForms(_message.Message):
        __slots__ = ("form_types", "payout_provider_id", "payout_provider_name")
        FORM_TYPES_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_PROVIDER_ID_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_PROVIDER_NAME_FIELD_NUMBER: _ClassVar[int]
        form_types: _containers.RepeatedScalarFieldContainer[_common_pb2.FormType]
        payout_provider_id: int
        payout_provider_name: str
        def __init__(self, form_types: _Optional[_Iterable[_Union[_common_pb2.FormType, str]]] = ..., payout_provider_id: _Optional[int] = ..., payout_provider_name: _Optional[str] = ...) -> None: ...
    PROVIDER_FORMS_FIELD_NUMBER: _ClassVar[int]
    provider_forms: _containers.RepeatedCompositeFieldContainer[ListProviderFormsResponse.ProviderForms]
    def __init__(self, provider_forms: _Optional[_Iterable[_Union[ListProviderFormsResponse.ProviderForms, _Mapping]]] = ...) -> None: ...
