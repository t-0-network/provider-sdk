package network.t0.sdk.provider;

import build.buf.protovalidate.exceptions.ValidationException;
import network.t0.sdk.common.Messages;
/**
 * Thrown when a response message fails {@code protovalidate} checks.
 *
 * <p>This exception is produced by {@link Validate#check(com.google.protobuf.Message)}
 * when a provider-built response message violates its proto-level constraints, or
 * when {@code protovalidate} cannot evaluate one of its rules.
 *
 * <p>The {@linkplain #getMessage() exception message} is the description the SDK's
 * response-validation interceptor ({@link ResponseValidationInterceptor}) gives the
 * same response on the response path: {@code "response validation failed: <violations>"}
 * for violations, and {@code "response validation error: <cause>"} for a rule that
 * cannot be evaluated.
 *
 * <p>Providers may catch this exception and convert it into a domain-level
 * failure (e.g. the {@code Failed} arm of a payment-result {@code oneof}).
 * If it propagates out of the handler, the interceptor closes the call with
 * {@code Status.INTERNAL} and the same description string.
 */
public final class ResponseValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String violations;
    private final String responseType;

    /**
     * Creates a new exception describing one or more protovalidate violations.
     *
     * @param responseType the proto message type's fully qualified name
     * @param violations   human-readable violation list (as produced by
     *                     {@link network.t0.sdk.common.ValidationUtils#formatViolations})
     */
    public ResponseValidationException(String responseType, String violations) {
        super(String.format(Messages.RESPONSE_INVALID, violations));
        this.responseType = responseType;
        this.violations = violations;
    }

    /**
     * Creates a new exception describing one or more protovalidate violations, with a cause.
     *
     * @param responseType the proto message type's fully qualified name
     * @param violations   human-readable violation list
     * @param cause        the underlying cause
     */
    public ResponseValidationException(String responseType, String violations, Throwable cause) {
        super(String.format(Messages.RESPONSE_INVALID, violations), cause);
        this.responseType = responseType;
        this.violations = violations;
    }

    private ResponseValidationException(String responseType, ValidationException cause) {
        super(String.format(Messages.RESPONSE_VALIDATION_ERROR, cause.getMessage()), cause);
        this.responseType = responseType;
        this.violations = cause.getMessage();
    }

    /**
     * The exception for a rule that {@code protovalidate} cannot evaluate: its message is
     * {@code "response validation error: <cause>"}, as the interceptor's own.
     *
     * @param responseType the proto message type's fully qualified name
     * @param cause        protovalidate's error
     */
    static ResponseValidationException validationError(String responseType, ValidationException cause) {
        return new ResponseValidationException(responseType, cause);
    }

    /**
     * @return the formatted violation list without the {@code "response validation failed: "} prefix,
     *         or, for a rule that cannot be evaluated, the message of protovalidate's error without the
     *         {@code "response validation error: "} prefix
     */
    public String getViolations() {
        return violations;
    }

    /**
     * @return the fully qualified proto message type name
     */
    public String getResponseType() {
        return responseType;
    }
}
