"""Public helper for validating provider responses against buf.validate rules.

Providers call ``validate(resp)`` before returning to surface validation
failures in the developer's own call frame. On success the input message is
returned unchanged; on failure a ``ConnectError(Code.INTERNAL, ...)`` is
raised with the same wording the SDK's response-validation interceptor
emits, so propagating the error preserves the on-wire shape.

Go equivalent: ``provider.Validate[T]`` (planned).
"""

from __future__ import annotations

from typing import TypeVar

import protovalidate
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from google.protobuf.message import Message

T = TypeVar("T", bound=Message)

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


def validate(msg: T) -> T:
    """Validate ``msg`` against its proto rules.

    Args:
        msg: The response message to validate.

    Returns:
        The same ``msg`` instance on success.

    Raises:
        ConnectError: ``Code.INTERNAL`` with message
            ``"response validation failed: <details>"`` when ``msg`` violates
            its proto rules. This matches the SDK response-validation
            interceptor so propagating the error keeps wire behavior
            identical.
    """
    try:
        _get_validator().validate(msg)
    except protovalidate.ValidationError as e:
        raise ConnectError(Code.INTERNAL, f"response validation failed: {e}") from e
    return msg
