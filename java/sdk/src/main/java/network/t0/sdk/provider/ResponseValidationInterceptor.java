package network.t0.sdk.provider;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
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
 * <p>This class is thread-safe. The {@link Validator} instance is shared
 * with {@link Validate#check(Message)} via {@link Validators#shared()}.
 *
 * <p>The interceptor also handles {@link ResponseValidationException} that
 * propagates out of a handler (e.g. when the developer calls
 * {@link Validate#check(Message)} but does not catch the failure): the wire
 * shape stays {@code Status.INTERNAL} with description
 * {@code "response validation failed: <details>"}.
 */
public final class ResponseValidationInterceptor implements ServerInterceptor {

    /** Default logger name used when no logger is supplied via the {@link ProviderServer.Builder}. */
    static final String DEFAULT_LOGGER_NAME = ResponseValidationInterceptor.class.getName();

    private final Logger log;
    private final Validator validator;

    /** Backwards-compatible no-arg constructor (logs to the class logger). */
    public ResponseValidationInterceptor() {
        this(LoggerFactory.getLogger(DEFAULT_LOGGER_NAME));
    }

    /**
     * @param log SLF4J logger used for the safety-net error line on validation failure.
     *            Must not be {@code null}.
     */
    public ResponseValidationInterceptor(Logger log) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        this.log = log;
        this.validator = Validators.shared();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String rpcMethod = call.getMethodDescriptor() != null
                ? call.getMethodDescriptor().getFullMethodName()
                : "unknown";
        ServerCall<ReqT, RespT> validatingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendMessage(RespT message) {
                if (message instanceof Message protoMessage) {
                    String responseType = protoMessage.getDescriptorForType().getFullName();
                    try {
                        ValidationResult result = validator.validate(protoMessage);
                        if (!result.isSuccess()) {
                            String details = ValidationUtils.formatViolations(result);
                            logFailure(rpcMethod, responseType, details);
                            call.close(Status.INTERNAL.withDescription("response validation failed: " + details), new Metadata());
                            return;
                        }
                    } catch (ValidationException e) {
                        logFailure(rpcMethod, responseType, e.getMessage());
                        call.close(Status.INTERNAL.withDescription("response validation error: " + e.getMessage()), new Metadata());
                        return;
                    }
                }
                super.sendMessage(message);
            }
        };
        // Map ResponseValidationException thrown by a handler (e.g. via Validate.check)
        // to the same Status.INTERNAL wire shape the sendMessage path produces. Without
        // this, gRPC would translate the unchecked exception into Status.UNKNOWN.
        ServerCall.Listener<ReqT> delegate = next.startCall(validatingCall, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                runMappingValidationFailure(super::onHalfClose);
            }

            @Override
            public void onMessage(ReqT message) {
                runMappingValidationFailure(() -> super.onMessage(message));
            }

            private void runMappingValidationFailure(Runnable action) {
                try {
                    action.run();
                } catch (ResponseValidationException e) {
                    logFailure(rpcMethod, "unknown", e.getViolations());
                    call.close(Status.INTERNAL.withDescription(e.getMessage()), new Metadata());
                }
            }
        };
    }

    /**
     * Writes a single structured error line for a validation failure.
     *
     * <p>Fields included: {@code rpc_method}, {@code response_type}, {@code violations},
     * {@code sdk_version}. The default backend renders these as key-value tags after the
     * message; backends that understand SLF4J's {@code KeyValuePair} (e.g. logback's
     * JSON encoder) render them as structured fields.
     */
    private void logFailure(String rpcMethod, String responseType, String violations) {
        log.atError()
                .setMessage("response validation failed")
                .addKeyValue("rpc_method", rpcMethod)
                .addKeyValue("response_type", responseType)
                .addKeyValue("violations", violations)
                .addKeyValue("sdk_version", SystemServiceImpl.SDK_VERSION)
                .log();
    }

}
