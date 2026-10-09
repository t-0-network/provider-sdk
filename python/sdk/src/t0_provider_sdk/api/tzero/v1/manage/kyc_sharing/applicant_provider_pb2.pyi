from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.manage.kyc_sharing import common_pb2 as _common_pb2
from tzero.v1.manage.kyc_sharing import form_pb2 as _form_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class GetProviderKybApplicantRequest(_message.Message):
    __slots__ = ("payout_requester_id", "client_id")
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_requester_id: int
    client_id: str
    def __init__(self, payout_requester_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class GetProviderKybApplicantResponse(_message.Message):
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

class GetProviderKycApplicantRequest(_message.Message):
    __slots__ = ("payout_requester_id", "client_id")
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    payout_requester_id: int
    client_id: str
    def __init__(self, payout_requester_id: _Optional[int] = ..., client_id: _Optional[str] = ...) -> None: ...

class GetProviderKycApplicantResponse(_message.Message):
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

class ApproveApplicantRequest(_message.Message):
    __slots__ = ("payout_requester_id", "client_id", "version")
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    payout_requester_id: int
    client_id: str
    version: int
    def __init__(self, payout_requester_id: _Optional[int] = ..., client_id: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class ApproveApplicantResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class RejectApplicantRequest(_message.Message):
    __slots__ = ("payout_requester_id", "client_id", "reason", "version")
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    payout_requester_id: int
    client_id: str
    reason: str
    version: int
    def __init__(self, payout_requester_id: _Optional[int] = ..., client_id: _Optional[str] = ..., reason: _Optional[str] = ..., version: _Optional[int] = ...) -> None: ...

class RejectApplicantResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ListProviderApplicantsRequest(_message.Message):
    __slots__ = ("form_type", "payout_requester_id", "states", "page")
    FORM_TYPE_FIELD_NUMBER: _ClassVar[int]
    PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
    STATES_FIELD_NUMBER: _ClassVar[int]
    PAGE_FIELD_NUMBER: _ClassVar[int]
    form_type: _common_pb2.FormType
    payout_requester_id: int
    states: _containers.RepeatedScalarFieldContainer[_common_pb2.ApplicantState]
    page: _common_pb2.Page
    def __init__(self, form_type: _Optional[_Union[_common_pb2.FormType, str]] = ..., payout_requester_id: _Optional[int] = ..., states: _Optional[_Iterable[_Union[_common_pb2.ApplicantState, str]]] = ..., page: _Optional[_Union[_common_pb2.Page, _Mapping]] = ...) -> None: ...

class ListProviderApplicantsResponse(_message.Message):
    __slots__ = ("applicants", "page_info")
    class Applicant(_message.Message):
        __slots__ = ("payout_requester_id", "payout_requester_name", "client_id", "state", "form_type", "form_version")
        PAYOUT_REQUESTER_ID_FIELD_NUMBER: _ClassVar[int]
        PAYOUT_REQUESTER_NAME_FIELD_NUMBER: _ClassVar[int]
        CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
        STATE_FIELD_NUMBER: _ClassVar[int]
        FORM_TYPE_FIELD_NUMBER: _ClassVar[int]
        FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
        payout_requester_id: int
        payout_requester_name: str
        client_id: str
        state: _common_pb2.ApplicantState
        form_type: _common_pb2.FormType
        form_version: int
        def __init__(self, payout_requester_id: _Optional[int] = ..., payout_requester_name: _Optional[str] = ..., client_id: _Optional[str] = ..., state: _Optional[_Union[_common_pb2.ApplicantState, str]] = ..., form_type: _Optional[_Union[_common_pb2.FormType, str]] = ..., form_version: _Optional[int] = ...) -> None: ...
    APPLICANTS_FIELD_NUMBER: _ClassVar[int]
    PAGE_INFO_FIELD_NUMBER: _ClassVar[int]
    applicants: _containers.RepeatedCompositeFieldContainer[ListProviderApplicantsResponse.Applicant]
    page_info: _common_pb2.PageInfo
    def __init__(self, applicants: _Optional[_Iterable[_Union[ListProviderApplicantsResponse.Applicant, _Mapping]]] = ..., page_info: _Optional[_Union[_common_pb2.PageInfo, _Mapping]] = ...) -> None: ...
