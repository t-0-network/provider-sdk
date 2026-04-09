package network.t0.sdk.common;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Violation;

import java.util.stream.Collectors;

/**
 * Shared utilities for protovalidate violation formatting.
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Formats validation violations into a human-readable semicolon-separated string.
     */
    public static String formatViolations(ValidationResult result) {
        return result.getViolations().stream()
                .map(Violation::toProto)
                .map(v -> {
                    String fieldName = v.getField().getElementsCount() > 0
                            ? v.getField().getElements(0).getFieldName()
                            : v.getField().toString();
                    return fieldName + ": " + v.getMessage();
                })
                .collect(Collectors.joining("; "));
    }
}
