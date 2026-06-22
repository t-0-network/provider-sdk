package network.t0.sdk.provider;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;
import network.t0.sdk.common.ValidationUtils;

/**
 * Public helper that lets provider handlers explicitly validate a response
 * message against its {@code protovalidate} constraints before returning it
 * to the SDK.
 *
 * <p>The SDK's {@link ResponseValidationInterceptor} already validates every
 * outgoing response as a safety net, but by then the handler has already
 * returned, so the developer never sees the failure. Calling
 * {@link #check(Message)} surfaces the failure in the same call frame as
 * the handler, where it can be caught, logged, or converted into a domain-level
 * error.
 *
 * <p>Wire behavior is unchanged: if a {@link ResponseValidationException}
 * thrown by this helper propagates out of the handler, the SDK interceptor
 * closes the call with the same {@code Status.INTERNAL} + same description
 * it would produce on its own.
 *
 * <p>Example:
 * <pre>{@code
 * @Override
 * public void payOut(PayoutRequest req, StreamObserver<PayoutResponse> obs) {
 *     PayoutResponse resp = PayoutResponse.newBuilder()
 *             .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
 *             .build();
 *     obs.onNext(Validate.check(resp));
 *     obs.onCompleted();
 * }
 * }</pre>
 */
public final class Validate {

    private Validate() {}

    /**
     * Validates {@code msg} against its proto-level constraints.
     *
     * @param msg the message to validate
     * @param <T> the concrete message type
     * @return {@code msg} unchanged, on success
     * @throws ResponseValidationException if validation fails or the validator itself errors
     */
    public static <T extends Message> T check(T msg) {
        if (msg == null) {
            return null;
        }
        try {
            ValidationResult result = Validators.shared().validate(msg);
            if (!result.isSuccess()) {
                throw new ResponseValidationException(ValidationUtils.formatViolations(result));
            }
            return msg;
        } catch (ValidationException e) {
            throw new ResponseValidationException(e.getMessage(), e);
        }
    }
}
