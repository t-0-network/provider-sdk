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

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ValidationUtils() {}

    /**
     * Formats validation violations into a human-readable semicolon-separated string:
     * {@code <field path>: <message>} for each, the path as every SDK writes it
     * (for example {@code items[0].amount}).
     */
    public static String formatViolations(ValidationResult result) {
        return result.getViolations().stream()
                .map(Violation::toProto)
                .map(v -> fieldPath(v.getField()) + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
    }

    /**
     * The field path of a violation as every SDK writes it (field_path_cases in
     * cross_test/test_vectors.json): field names joined by {@code .}, each list index or map key in
     * brackets after its field name, and a string key in double quotes with JSON string escaping.
     * An empty path is {@code ""}.
     */
    static String fieldPath(FieldPath path) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (FieldPathElement element : path.getElementsList()) {
            if (!first) {
                out.append('.');
            }
            first = false;
            out.append(element.getFieldName());
            switch (element.getSubscriptCase()) {
                case INDEX -> out.append('[').append(Long.toUnsignedString(element.getIndex())).append(']');
                case BOOL_KEY -> out.append('[').append(element.getBoolKey()).append(']');
                case INT_KEY -> out.append('[').append(element.getIntKey()).append(']');
                case UINT_KEY -> out.append('[').append(Long.toUnsignedString(element.getUintKey())).append(']');
                case STRING_KEY -> appendJsonString(out.append('['), element.getStringKey()).append(']');
                case SUBSCRIPT_NOT_SET -> { }
            }
        }
        return out.toString();
    }

    /**
     * Appends {@code s} in double quotes with JSON string escaping (RFC 8259): {@code \"} and
     * {@code \\}, {@code \b}, {@code \f}, {@code \n}, {@code \r} and {@code \t}, every other
     * character below U+0020 as {@code \}{@code u00xx}, and every other character as it is (DEL and
     * all non-ASCII included).
     */
    private static StringBuilder appendJsonString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xf]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"');
    }
}
