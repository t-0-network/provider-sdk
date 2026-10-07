// Every message the SDK raises itself, the same in every SDK: `messages` in
// cross_test/test_vectors.json, by the same name. A message with a placeholder is a function of it.
// Not exported from the package.

export const PRIVATE_KEY_EMPTY = "private key must not be null or empty";
export const PRIVATE_KEY_MALFORMED = "private key must be 32 bytes (64 hex characters)";
export const PRIVATE_KEY_OUT_OF_RANGE = "private key must be in range [1, n-1]";
export const PRIVATE_KEY_BYTES_LENGTH = "private key must be 32 bytes";
export const PUBLIC_KEY_NOT_HEX = "must be hex, with an optional 0x or 0X prefix";
export const PUBLIC_KEY_NOT_A_POINT = "not a point on secp256k1";
export const NETWORK_PUBLIC_KEY_NOT_SET = "network public key is not set";
export const NETWORK_PUBLIC_KEY_INVALID = (reason: string) => `invalid network public key: ${reason}`;

export const SIGNER_NULL = "signer must not be null";
export const DIGEST_LENGTH = "digest must be 32 bytes";
export const SIGNER_SIGNATURE_INVALID = "signature must be 64 or 65 bytes";
export const SIGNER_PUBLIC_KEY_INVALID = "public key must be 65 bytes, uncompressed";

export const BASE_URL_NOT_SET = "base URL is not set";
export const BASE_URL_NOT_VALID = "base URL is not valid";
export const TIMEOUT_NOT_VALID = "timeout must be a positive duration of at most 2147483647 ms";
export const STREAM_TIMEOUT_NOT_VALID = "stream timeout must be a positive duration of at most 2147483647 ms";
export const SIGNING_FAILED = (cause: string) => `signing the request failed: ${cause}`;
export const BIDI_NOT_SUPPORTED = "bidirectional streams are not supported";
export const FIRST_MESSAGE_INCOMPLETE = "streaming request ends inside its first message";
export const FIRST_CHUNK_NOT_ONE_ENVELOPE = "the first request chunk is not one complete envelope";
export const UNARY_BODY_NOT_ONE_CHUNK = "a unary request body must be one chunk";
export const CALL_DEADLINE_PASSED = "the operation timed out";
export const STREAM_CLOSED_EARLY = "the stream was closed before its end";

export const SERVICE_NULL = "service must not be null";
export const GET_NOT_SUPPORTED = "GET requests are not supported";
export const MISSING_HEADER = (header: string) => `missing required header: ${header}`;
export const INVALID_HEADER_ENCODING = (header: string) => `invalid header encoding: ${header}`;
export const TIMESTAMP_NOT_DECIMAL = "invalid timestamp header: not a decimal number";
export const TIMESTAMP_OUT_OF_RANGE = "invalid timestamp header: value out of range";
export const TIMESTAMP_OUTSIDE_WINDOW = "timestamp is outside the allowed time window";
export const UNKNOWN_PUBLIC_KEY = "request signed with unknown public key";
export const SIGNATURE_VERIFICATION_FAILED = "signature verification failed";
export const BODY_TOO_LARGE = (limit: number) => `max payload size of ${limit} bytes exceeded`;
export const NO_SIGNATURE_RESULT = "no signature result in context";
export const UNKNOWN_SERVICE = (service: string) => `unknown service '${service}'`;
export const RESPONSE_INVALID = (violations: string) => `response validation failed: ${violations}`;
export const RESPONSE_VALIDATION_ERROR = (cause: string) => `response validation error: ${cause}`;
export const STREAMING_NOT_SUPPORTED = "streaming calls are not supported";

// createRequestDecoder's own answers.
export const UNSUPPORTED_CONTENT_TYPE = "Unsupported Content-Type";
export const MALFORMED_REQUEST_BODY = "Malformed request body";
export const REQUEST_INVALID = "Request validation failed";
export const REQUEST_VALIDATION_ERROR = (cause: string) => `Validation error: ${cause}`;
