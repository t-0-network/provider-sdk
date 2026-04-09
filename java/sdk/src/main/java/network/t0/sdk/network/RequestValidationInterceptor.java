package network.t0.sdk.network;

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
 * gRPC client interceptor that validates outgoing requests against
 * buf.validate proto annotations before they are sent.
 *
 * <p>Invalid requests are rejected with {@link Status#INVALID_ARGUMENT}
 * before they leave the client.
 *
 * <p>This class is thread-safe. The {@link Validator} instance is created once
 * and reused across all calls.
 */
public final class RequestValidationInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestValidationInterceptor.class);

    private final Validator validator;

    public RequestValidationInterceptor() {
        this.validator = ValidatorFactory.newBuilder().build();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void sendMessage(ReqT message) {
                if (message instanceof Message protoMessage) {
                    try {
                        ValidationResult result = validator.validate(protoMessage);
                        if (!result.isSuccess()) {
                            String details = ValidationUtils.formatViolations(result);
                            log.error("Request validation failed for {}: {}", protoMessage.getDescriptorForType().getFullName(), details);
                            throw Status.INVALID_ARGUMENT.withDescription("request validation failed: " + details).asRuntimeException();
                        }
                    } catch (ValidationException e) {
                        log.error("Request validation error for {}: {}", protoMessage.getDescriptorForType().getFullName(), e.getMessage());
                        throw Status.INVALID_ARGUMENT.withDescription("request validation error: " + e.getMessage()).asRuntimeException();
                    }
                }
                super.sendMessage(message);
            }
        };
    }
}
