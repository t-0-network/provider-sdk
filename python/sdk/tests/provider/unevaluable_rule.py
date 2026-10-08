"""A message with a protovalidate rule that cannot be evaluated: its message-level CEL rule
``100 / this.divisor > 0`` divides by zero when ``divisor`` is 0, so protovalidate raises an error
instead of reporting a violation."""

from __future__ import annotations

# Import SDK first to ensure api/ is on sys.path (needed for buf.validate stubs)
import t0_provider_sdk  # noqa: F401

# isort: split
import protovalidate
from buf.validate import validate_pb2
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory

TYPE_NAME = "t0.sdk.test.UnevaluableRule"


def _message_class() -> type:
    rules = validate_pb2.MessageRules()
    rules.cel.add(id="divisor_divides", expression="100 / this.divisor > 0")
    file = descriptor_pb2.FileDescriptorProto(
        name="t0/sdk/test/unevaluable_rule.proto",
        package="t0.sdk.test",
        syntax="proto3",
        dependency=[validate_pb2.DESCRIPTOR.name],
    )
    message = file.message_type.add(name="UnevaluableRule")
    message.field.add(
        name="divisor",
        number=1,
        type=descriptor_pb2.FieldDescriptorProto.TYPE_INT64,
        label=descriptor_pb2.FieldDescriptorProto.LABEL_OPTIONAL,
    )
    message.options.Extensions[validate_pb2.message].CopyFrom(rules)
    pool = descriptor_pool.Default()
    pool.Add(file)
    return message_factory.GetMessageClass(pool.FindMessageTypeByName(TYPE_NAME))


UnevaluableRule = _message_class()


def cause() -> str:
    """The message of the error protovalidate raises for an UnevaluableRule with divisor 0."""
    try:
        protovalidate.Validator().validate(UnevaluableRule())
    except protovalidate.ValidationError as e:
        raise AssertionError(f"protovalidate reported a violation for {TYPE_NAME}") from e
    except Exception as e:
        return str(e)
    raise AssertionError(f"protovalidate evaluated the rule of {TYPE_NAME}")
