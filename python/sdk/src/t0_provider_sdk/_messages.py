"""Every message the SDK raises itself, the same text in every SDK (`messages` in
cross_test/test_vectors.json). A ``{placeholder}`` is filled in with ``str.format`` where the
message is raised.
"""

# Keys
PRIVATE_KEY_EMPTY = "private key must not be null or empty"
PRIVATE_KEY_MALFORMED = "private key must be 32 bytes (64 hex characters)"
PRIVATE_KEY_OUT_OF_RANGE = "private key must be in range [1, n-1]"
PUBLIC_KEY_NOT_HEX = "must be hex, with an optional 0x or 0X prefix"
PUBLIC_KEY_NOT_A_POINT = "not a point on secp256k1"
NETWORK_PUBLIC_KEY_NOT_SET = "network public key is not set"
NETWORK_PUBLIC_KEY_INVALID = "invalid network public key: {reason}"

# Signer
SIGNER_NULL = "signer must not be null"
KEY_AND_SIGNER = "a private key and a signer must not both be given"
DIGEST_LENGTH = "digest must be 32 bytes"
SIGNER_SIGNATURE_INVALID = "signature must be 64 or 65 bytes"
SIGNER_PUBLIC_KEY_INVALID = "public key must be 65 bytes, uncompressed"

# Client
BASE_URL_NOT_SET = "base URL is not set"
BASE_URL_NOT_VALID = "base URL is not valid"
TIMEOUT_NOT_VALID = "timeout must be a positive duration of at most 2147483647 ms"
STREAM_TIMEOUT_NOT_VALID = "stream timeout must be a positive duration of at most 2147483647 ms"
SIGNING_FAILED = "signing the request failed: {cause}"
GET_NOT_SUPPORTED = "GET requests are not supported"
BIDI_NOT_SUPPORTED = "bidirectional streams are not supported"
CALL_DEADLINE_PASSED = "the operation timed out"
FIRST_MESSAGE_INCOMPLETE = "streaming request ends inside its first message"
FIRST_CHUNK_NOT_ONE_ENVELOPE = "the first request chunk is not one complete envelope"

# Server setup
SERVICE_NULL = "service must not be null"

# Server: signature verification
MISSING_HEADER = "missing required header: {header}"
INVALID_HEADER_ENCODING = "invalid header encoding: {header}"
TIMESTAMP_NOT_DECIMAL = "invalid timestamp header: not a decimal number"
TIMESTAMP_OUT_OF_RANGE = "invalid timestamp header: value out of range"
TIMESTAMP_OUTSIDE_WINDOW = "timestamp is outside the allowed time window"
UNKNOWN_PUBLIC_KEY = "request signed with unknown public key"
SIGNATURE_VERIFICATION_FAILED = "signature verification failed"
BODY_TOO_LARGE = "max payload size of {limit} bytes exceeded"
NO_SIGNATURE_RESULT = "no signature result in context"

# Server: services
UNKNOWN_SERVICE = "unknown service '{service}'"
RESPONSE_INVALID = "response validation failed: {violations}"
