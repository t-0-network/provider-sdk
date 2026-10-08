package network.t0.sdk.provider;

import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.MessageRules;
import build.buf.validate.Rule;
import build.buf.validate.ValidateProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

/**
 * A message with a protovalidate rule that cannot be evaluated: its message-level CEL rule
 * {@code 100 / this.divisor > 0} divides by zero when {@code divisor} is 0, so protovalidate
 * throws a {@link ValidationException} instead of reporting a violation.
 */
public final class UnevaluableRuleMessage {

    /** The fully qualified name of the message type. */
    public static final String TYPE_NAME = "t0.sdk.test.UnevaluableRule";

    private static final Descriptor DESCRIPTOR = buildDescriptor();

    private UnevaluableRuleMessage() {}

    /** @return a message whose rule protovalidate cannot evaluate */
    public static Message message() {
        return DynamicMessage.newBuilder(DESCRIPTOR)
                .setField(DESCRIPTOR.findFieldByName("divisor"), 0L)
                .build();
    }

    /** @return the message of the error protovalidate throws for {@link #message()} */
    public static String cause() {
        try {
            ValidatorFactory.newBuilder().build().validate(message());
        } catch (ValidationException e) {
            return e.getMessage();
        }
        throw new AssertionError("protovalidate evaluated the rule of " + TYPE_NAME);
    }

    private static Descriptor buildDescriptor() {
        MessageOptions options = MessageOptions.newBuilder()
                .setExtension(ValidateProto.message, MessageRules.newBuilder()
                        .addCel(Rule.newBuilder()
                                .setId("divisor_divides")
                                .setExpression("100 / this.divisor > 0"))
                        .build())
                .build();
        FileDescriptorProto file = FileDescriptorProto.newBuilder()
                .setName("t0/sdk/test/unevaluable_rule.proto")
                .setPackage("t0.sdk.test")
                .setSyntax("proto3")
                .addDependency(ValidateProto.getDescriptor().getName())
                .addMessageType(DescriptorProto.newBuilder()
                        .setName("UnevaluableRule")
                        .addField(FieldDescriptorProto.newBuilder()
                                .setName("divisor")
                                .setNumber(1)
                                .setType(FieldDescriptorProto.Type.TYPE_INT64)
                                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
                        .setOptions(options))
                .build();
        try {
            return FileDescriptor.buildFrom(file, new FileDescriptor[] {ValidateProto.getDescriptor()})
                    .findMessageTypeByName("UnevaluableRule");
        } catch (DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }
}
