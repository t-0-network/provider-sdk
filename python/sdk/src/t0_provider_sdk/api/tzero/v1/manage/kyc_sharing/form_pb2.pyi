from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.manage.kyc_sharing import common_pb2 as _common_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class KybForm(_message.Message):
    __slots__ = ("standard_fields", "custom_fields", "ubo_form")
    class StandardField(_message.Message):
        __slots__ = ("field", "required")
        FIELD_FIELD_NUMBER: _ClassVar[int]
        REQUIRED_FIELD_NUMBER: _ClassVar[int]
        field: _common_pb2.StandardKybFieldType
        required: bool
        def __init__(self, field: _Optional[_Union[_common_pb2.StandardKybFieldType, str]] = ..., required: _Optional[bool] = ...) -> None: ...
    class UboForm(_message.Message):
        __slots__ = ("standard_fields", "custom_fields")
        class StandardField(_message.Message):
            __slots__ = ("field", "required")
            FIELD_FIELD_NUMBER: _ClassVar[int]
            REQUIRED_FIELD_NUMBER: _ClassVar[int]
            field: _common_pb2.StandardUboFieldType
            required: bool
            def __init__(self, field: _Optional[_Union[_common_pb2.StandardUboFieldType, str]] = ..., required: _Optional[bool] = ...) -> None: ...
        STANDARD_FIELDS_FIELD_NUMBER: _ClassVar[int]
        CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
        standard_fields: _containers.RepeatedCompositeFieldContainer[KybForm.UboForm.StandardField]
        custom_fields: _containers.RepeatedCompositeFieldContainer[FormField]
        def __init__(self, standard_fields: _Optional[_Iterable[_Union[KybForm.UboForm.StandardField, _Mapping]]] = ..., custom_fields: _Optional[_Iterable[_Union[FormField, _Mapping]]] = ...) -> None: ...
    STANDARD_FIELDS_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
    UBO_FORM_FIELD_NUMBER: _ClassVar[int]
    standard_fields: _containers.RepeatedCompositeFieldContainer[KybForm.StandardField]
    custom_fields: _containers.RepeatedCompositeFieldContainer[FormField]
    ubo_form: KybForm.UboForm
    def __init__(self, standard_fields: _Optional[_Iterable[_Union[KybForm.StandardField, _Mapping]]] = ..., custom_fields: _Optional[_Iterable[_Union[FormField, _Mapping]]] = ..., ubo_form: _Optional[_Union[KybForm.UboForm, _Mapping]] = ...) -> None: ...

class KycForm(_message.Message):
    __slots__ = ("standard_fields", "custom_fields")
    class StandardField(_message.Message):
        __slots__ = ("field", "required")
        FIELD_FIELD_NUMBER: _ClassVar[int]
        REQUIRED_FIELD_NUMBER: _ClassVar[int]
        field: _common_pb2.StandardKycFieldType
        required: bool
        def __init__(self, field: _Optional[_Union[_common_pb2.StandardKycFieldType, str]] = ..., required: _Optional[bool] = ...) -> None: ...
    STANDARD_FIELDS_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_FIELDS_FIELD_NUMBER: _ClassVar[int]
    standard_fields: _containers.RepeatedCompositeFieldContainer[KycForm.StandardField]
    custom_fields: _containers.RepeatedCompositeFieldContainer[FormField]
    def __init__(self, standard_fields: _Optional[_Iterable[_Union[KycForm.StandardField, _Mapping]]] = ..., custom_fields: _Optional[_Iterable[_Union[FormField, _Mapping]]] = ...) -> None: ...

class FormField(_message.Message):
    __slots__ = ("identifier", "title", "required", "number", "string", "date", "file", "single_select", "multi_select")
    class Number(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class String(_message.Message):
        __slots__ = ("max_length",)
        MAX_LENGTH_FIELD_NUMBER: _ClassVar[int]
        max_length: int
        def __init__(self, max_length: _Optional[int] = ...) -> None: ...
    class Date(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class File(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Select(_message.Message):
        __slots__ = ("options",)
        class Item(_message.Message):
            __slots__ = ("key", "title")
            KEY_FIELD_NUMBER: _ClassVar[int]
            TITLE_FIELD_NUMBER: _ClassVar[int]
            key: str
            title: str
            def __init__(self, key: _Optional[str] = ..., title: _Optional[str] = ...) -> None: ...
        OPTIONS_FIELD_NUMBER: _ClassVar[int]
        options: _containers.RepeatedCompositeFieldContainer[FormField.Select.Item]
        def __init__(self, options: _Optional[_Iterable[_Union[FormField.Select.Item, _Mapping]]] = ...) -> None: ...
    IDENTIFIER_FIELD_NUMBER: _ClassVar[int]
    TITLE_FIELD_NUMBER: _ClassVar[int]
    REQUIRED_FIELD_NUMBER: _ClassVar[int]
    NUMBER_FIELD_NUMBER: _ClassVar[int]
    STRING_FIELD_NUMBER: _ClassVar[int]
    DATE_FIELD_NUMBER: _ClassVar[int]
    FILE_FIELD_NUMBER: _ClassVar[int]
    SINGLE_SELECT_FIELD_NUMBER: _ClassVar[int]
    MULTI_SELECT_FIELD_NUMBER: _ClassVar[int]
    identifier: str
    title: str
    required: bool
    number: FormField.Number
    string: FormField.String
    date: FormField.Date
    file: FormField.File
    single_select: FormField.Select
    multi_select: FormField.Select
    def __init__(self, identifier: _Optional[str] = ..., title: _Optional[str] = ..., required: _Optional[bool] = ..., number: _Optional[_Union[FormField.Number, _Mapping]] = ..., string: _Optional[_Union[FormField.String, _Mapping]] = ..., date: _Optional[_Union[FormField.Date, _Mapping]] = ..., file: _Optional[_Union[FormField.File, _Mapping]] = ..., single_select: _Optional[_Union[FormField.Select, _Mapping]] = ..., multi_select: _Optional[_Union[FormField.Select, _Mapping]] = ...) -> None: ...
