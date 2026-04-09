package network.t0.sdk.provider;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;
import io.grpc.*;
import network.t0.sdk.common.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC server interceptor that validates outgoing responses
 * against buf.validate proto annotations.
 *
 * <p>Invalid responses are rejected with {@link Status#INTERNAL} (provider implementation bug).
 *
 * <p>This class is thread-safe. The {@link Validator} instance is created once
 * and reused across all calls.
 */
public final class ResponseValidationInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidationInterceptor.class);

    private final Validator validator;

    public ResponseValidationInterceptor() {
        this.validator = ValidatorFactory.newBuilder().build();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendMessage(RespT message) {
                if (message instanceof Message protoMessage) {
                    try {
                        ValidationResult result = validator.validate(protoMessage);
                        if (!result.isSuccess()) {
                            String details = ValidationUtils.formatViolations(result);
                            log.error("Response validation failed for {}: {}", protoMessage.getDescriptorForType().getFullName(), details);
                            call.close(Status.INTERNAL.withDescription("response validation failed: " + details), new Metadata());
                            return;
                        }
                    } catch (ValidationException e) {
                        log.error("Response validation error for {}: {}", protoMessage.getDescriptorForType().getFullName(), e.getMessage());
                        call.close(Status.INTERNAL.withDescription("response validation error: " + e.getMessage()), new Metadata());
                        return;
                    }
                }
                super.sendMessage(message);
            }
        }, headers);
    }

}
