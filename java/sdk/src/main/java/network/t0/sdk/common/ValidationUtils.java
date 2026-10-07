package network.t0.sdk.common;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Violation;
import build.buf.validate.FieldPath;
import build.buf.validate.FieldPathElement;

import java.util.stream.Collectors;

/**
 * Shared utilities for protovalidate violation formatting.
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Formats validation violations into a human-readable semicolon-separated string:
     * {@code <field path>: <message>} for each, the path as protovalidate writes it
     * (for example {@code items[0].amount}).
     */
    public static String formatViolations(ValidationResult result) {
        return result.getViolations().stream()
                .map(Violation::toProto)
                .map(v -> fieldPath(v.getField()) + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
    }

    // protovalidate's own rendering of a field path, which its FieldPathUtils keeps package-private.
    private static String fieldPath(FieldPath path) {
        StringBuilder out = new StringBuilder();
        for (FieldPathElement element : path.getElementsList()) {
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(element.getFieldName());
            switch (element.getSubscriptCase()) {
                case INDEX -> out.append('[').append(Long.toUnsignedString(element.getIndex())).append(']');
                case BOOL_KEY -> out.append('[').append(element.getBoolKey()).append(']');
                case INT_KEY -> out.append('[').append(element.getIntKey()).append(']');
                case UINT_KEY -> out.append('[').append(Long.toUnsignedString(element.getUintKey())).append(']');
                case STRING_KEY -> out.append("[\"")
                        .append(element.getStringKey().replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"]");
                case SUBSCRIPT_NOT_SET -> { }
            }
        }
        return out.toString();
    }
}
