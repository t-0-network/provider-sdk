from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.manage.kyc_sharing import form_pb2 as _form_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SaveKybFormRequest(_message.Message):
    __slots__ = ("form",)
    FORM_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KybForm
    def __init__(self, form: _Optional[_Union[_form_pb2.KybForm, _Mapping]] = ...) -> None: ...

class SaveKybFormResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class SaveKycFormRequest(_message.Message):
    __slots__ = ("form",)
    FORM_FIELD_NUMBER: _ClassVar[int]
    form: _form_pb2.KycForm
    def __init__(self, form: _Optional[_Union[_form_pb2.KycForm, _Mapping]] = ...) -> None: ...

class SaveKycFormResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class GetFormsRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class GetFormsResponse(_message.Message):
    __slots__ = ("kyb_form", "kyb_form_version", "kyc_form", "kyc_form_version")
    KYB_FORM_FIELD_NUMBER: _ClassVar[int]
    KYB_FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    KYC_FORM_FIELD_NUMBER: _ClassVar[int]
    KYC_FORM_VERSION_FIELD_NUMBER: _ClassVar[int]
    kyb_form: _form_pb2.KybForm
    kyb_form_version: int
    kyc_form: _form_pb2.KycForm
    kyc_form_version: int
    def __init__(self, kyb_form: _Optional[_Union[_form_pb2.KybForm, _Mapping]] = ..., kyb_form_version: _Optional[int] = ..., kyc_form: _Optional[_Union[_form_pb2.KycForm, _Mapping]] = ..., kyc_form_version: _Optional[int] = ...) -> None: ...
