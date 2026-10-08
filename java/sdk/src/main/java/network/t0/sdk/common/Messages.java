package network.t0.sdk.common;

/**
 * Every message the SDK raises itself, the same text in every SDK: {@code messages} in
 * cross_test/test_vectors.json, by name. A message with a placeholder is a format string with one
 * {@code %s}, filled in with {@link String#format}.
 */
public final class Messages {

    // Keys and signers.
    public static final String PRIVATE_KEY_EMPTY = "private key must not be null or empty";
    public static final String PRIVATE_KEY_MALFORMED = "private key must be 32 bytes (64 hex characters)";
    public static final String PRIVATE_KEY_OUT_OF_RANGE = "private key must be in range [1, n-1]";
    public static final String PRIVATE_KEY_BYTES_LENGTH = "private key must be 32 bytes";
    public static final String PUBLIC_KEY_NOT_HEX = "must be hex, with an optional 0x or 0X prefix";
    public static final String PUBLIC_KEY_NOT_A_POINT = "not a point on secp256k1";
    public static final String NETWORK_PUBLIC_KEY_NOT_SET = "network public key is not set";
    /** {@code %s}: {@link #PUBLIC_KEY_NOT_HEX} or {@link #PUBLIC_KEY_NOT_A_POINT}. */
    public static final String NETWORK_PUBLIC_KEY_INVALID = "invalid network public key: %s";
    public static final String SIGNER_NULL = "signer must not be null";
    public static final String DIGEST_LENGTH = "digest must be 32 bytes";
    public static final String SIGNER_SIGNATURE_INVALID = "signature must be 64 or 65 bytes";
    public static final String SIGNER_PUBLIC_KEY_INVALID = "public key must be 65 bytes, uncompressed";
    public static final String SIGNER_PUBLIC_KEY_UNSUPPORTED = "use the public key returned by sign";
    public static final String SIGNATURE_HAS_NO_V = "a 64-byte signature has no v";
    public static final String RECOVERY_ID_NOT_FOUND = "Could not determine recovery ID";

    // Client.
    public static final String BASE_URL_NOT_SET = "base URL is not set";
    public static final String BASE_URL_NOT_VALID = "base URL is not valid";
    public static final String TIMEOUT_NOT_VALID = "timeout must be a positive duration of at most 2147483647 ms";
    public static final String STREAM_TIMEOUT_NOT_VALID =
            "stream timeout must be a positive duration of at most 2147483647 ms";
    /** {@code %s}: the message of the signer's error. */
    public static final String SIGNING_FAILED = "signing the request failed: %s";
    public static final String BIDI_NOT_SUPPORTED = "bidirectional streams are not supported";
    public static final String KYC_DOWNLOAD_SHAPE =
            "download stream must be one metadata message followed by chunks";
    public static final String COMPRESSED_NOT_SUPPORTED = "compressed requests are not supported";
    public static final String MESSAGE_SERIALIZATION_FAILED = "Failed to serialize message for signing";
    public static final String STREAM_READ_FAILED = "Failed to read bytes from stream";

    // Setup.
    /** {@code %s}: the parameter's name. */
    public static final String ARGUMENT_NULL = "%s must not be null";
    public static final String SERVICE_NULL = "service must not be null";
    public static final String PORT_NOT_VALID = "port must be between 0 and 65535";
    public static final String NO_SERVICE = "at least one service must be added with withService()";
    public static final String MAX_INBOUND_METADATA_SIZE_NOT_VALID = "maxInboundMetadataSize must be positive";
    public static final String HANDSHAKE_TIMEOUT_NOT_VALID = "timeout must be positive";

    // Server.
    /** {@code %s}: the header's name. */
    public static final String MISSING_HEADER = "missing required header: %s";
    /** {@code %s}: the header's name. */
    public static final String INVALID_HEADER_ENCODING = "invalid header encoding: %s";
    public static final String TIMESTAMP_NOT_DECIMAL = "invalid timestamp header: not a decimal number";
    public static final String TIMESTAMP_OUT_OF_RANGE = "invalid timestamp header: value out of range";
    public static final String TIMESTAMP_OUTSIDE_WINDOW = "timestamp is outside the allowed time window";
    public static final String UNKNOWN_PUBLIC_KEY = "request signed with unknown public key";
    public static final String SIGNATURE_VERIFICATION_FAILED = "signature verification failed";
    public static final String NO_SIGNATURE_RESULT = "no signature result in context";
    /** {@code %s}: the message of the read error. */
    public static final String REQUEST_BODY_READ_FAILED = "reading request body: %s";
    /** {@code %s}: the service asked for. */
    public static final String UNKNOWN_SERVICE = "unknown service '%s'";
    /** {@code %s}: each violation as {@code <field path>: <message>}, joined by {@code "; "}. */
    public static final String RESPONSE_INVALID = "response validation failed: %s";
    /** {@code %s}: the message of protovalidate's error, when it cannot evaluate a rule. */
    public static final String RESPONSE_VALIDATION_ERROR = "response validation error: %s";

    private Messages() {
        // Constants class
    }
}
