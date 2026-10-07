"""Public helper for validating provider responses against buf.validate rules.

Providers call ``validate(resp)`` before returning to surface validation
failures in the developer's own call frame. On success the input message is
returned unchanged; on failure a ``ConnectError(Code.INTERNAL, ...)`` is
raised with the same wording the SDK's response-validation interceptor
emits, so propagating the error preserves the on-wire shape.
"""

from __future__ import annotations

import json
from typing import Any, TypeVar

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from google.protobuf.message import Message

from t0_provider_sdk._messages import RESPONSE_INVALID, RESPONSE_VALIDATION_ERROR

T = TypeVar("T", bound=Message)

# What protovalidate raises for a rule it cannot compile or evaluate, as opposed to a message that
# breaks its rules (ValidationError): CompilationError in every version; EvaluationError from 2.0;
# before 2.0, cel-python's CELEvalError for a rule that fails while it is evaluated.
_RULE_ERRORS: tuple[type[Exception], ...] = (protovalidate.CompilationError,)
if hasattr(protovalidate, "EvaluationError"):
    _RULE_ERRORS += (protovalidate.EvaluationError,)
try:
    from celpy import CELEvalError
except ImportError:  # protovalidate 2.x does not use cel-python
    pass
else:
    _RULE_ERRORS += (CELEvalError,)

# Module-level validator instance, reused by both the helper and the
# response-validation interceptor. ``protovalidate.Validator`` is safe to
# share across threads/tasks.
_validator: protovalidate.Validator | None = None


def _get_validator() -> protovalidate.Validator:
    """Return the shared validator, constructing it on first use."""
    global _validator
    if _validator is None:
        _validator = protovalidate.Validator()
    return _validator


def _violations(error: protovalidate.ValidationError) -> str:
    """Each violation as ``<field path>: <message>``, joined by ``; `` (the {violations} of
    RESPONSE_INVALID)."""
    return "; ".join(f"{_field_path(v.proto.field)}: {v.proto.message}" for v in error.violations)


def _cause(error: Exception) -> str:
    """The message of an error in _RULE_ERRORS, with no type name in front (the {cause} of
    RESPONSE_VALIDATION_ERROR). cel-python's CELEvalError carries the message first, then the
    Python exception it stands for."""
    if len(error.args) > 1 and isinstance(error.args[0], str):
        return error.args[0]
    return str(error)


def _field_path(path: Any) -> str:
    """A buf.validate.FieldPath as ``a.b[0].c["key"]``, as protovalidate-go writes it."""
    if path is None:
        return ""
    parts = []
    for element in path.elements:
        subscript = _subscript(element)
        parts.append(element.field_name if subscript is None else f"{element.field_name}[{subscript}]")
    return ".".join(parts)


def _subscript(element: Any) -> str | None:
    """The repeated index or map key of a FieldPathElement, if it has one."""
    which_oneof = getattr(element, "WhichOneof", None)
    if which_oneof is not None:  # protovalidate 1.x: google.protobuf messages
        kind = which_oneof("subscript")
        if kind is None:
            return None
        value = getattr(element, kind)
    else:  # protovalidate 2.x: the oneof is a value of its own
        if element.subscript is None:
            return None
        kind, value = element.subscript.field, element.subscript.value
    if kind == "string_key":
        # Quoted as protovalidate-go and protovalidate-es write it: printable non-ASCII stays as is.
        return json.dumps(value, ensure_ascii=False)
    if kind == "bool_key":
        return "true" if value else "false"
    return str(value)


def validate(msg: T) -> T:
    """Validate ``msg`` against its proto rules.

    Args:
        msg: The response message to validate.

    Returns:
        The same ``msg`` instance on success.

    Raises:
        ConnectError: ``Code.INTERNAL`` with message
            ``"response validation failed: <field>: <message>[; ...]"`` when
            ``msg`` violates its proto rules, or ``"response validation error: <cause>"``
            when protovalidate cannot compile or evaluate one of them. This matches the
            SDK response-validation interceptor so propagating the error keeps wire
            behavior identical.
    """
    try:
        _get_validator().validate(msg)
    except protovalidate.ValidationError as e:
        raise ConnectError(Code.INTERNAL, RESPONSE_INVALID.format(violations=_violations(e))) from e
    except _RULE_ERRORS as e:
        raise ConnectError(Code.INTERNAL, RESPONSE_VALIDATION_ERROR.format(cause=_cause(e))) from e
    return msg
