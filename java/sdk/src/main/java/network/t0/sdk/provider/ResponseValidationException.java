package network.t0.sdk.provider;

/**
 * Thrown when a response message fails {@code protovalidate} checks.
 *
 * <p>This exception is produced by {@link Validate#check(com.google.protobuf.Message)}
 * when a provider-built response message violates its proto-level constraints.
 *
 * <p>The {@linkplain #getMessage() exception message} is always prefixed with
 * {@code "response validation failed: "} so that the SDK's response-validation
 * interceptor and any catch-all error mapping produce the same wire-level
 * description as the interceptor that runs on the response path
 * ({@link ResponseValidationInterceptor}).
 *
 * <p>Providers may catch this exception and convert it into a domain-level
 * failure (e.g. the {@code Failed} arm of a payment-result {@code oneof}).
 * If it propagates out of the handler, the interceptor closes the call with
 * {@code Status.INTERNAL} and the same description string.
 */
public final class ResponseValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String violations;

    /**
     * Creates a new exception describing one or more protovalidate violations.
     *
     * @param violations human-readable violation list (as produced by
     *                   {@link network.t0.sdk.common.ValidationUtils#formatViolations})
     */
    public ResponseValidationException(String violations) {
        super("response validation failed: " + violations);
        this.violations = violations;
    }

    /**
     * Creates a new exception wrapping an underlying validator error.
     *
     * @param violations human-readable violation list
     * @param cause      the underlying cause (e.g. a {@code ValidationException} from protovalidate)
     */
    public ResponseValidationException(String violations, Throwable cause) {
        super("response validation failed: " + violations, cause);
        this.violations = violations;
    }

    /**
     * @return the formatted violation list without the {@code "response validation failed: "} prefix
     */
    public String getViolations() {
        return violations;
    }
}
