namespace T0.ProviderSdk.Common;

/// <summary>
/// Every message the SDK raises itself, the same text in every SDK: <c>messages</c> in
/// cross_test/test_vectors.json, by name. A message with a placeholder is a method that fills it in.
/// </summary>
internal static class Messages
{
    // Keys and signers.
    internal const string PrivateKeyEmpty = "private key must not be null or empty";
    internal const string PrivateKeyMalformed = "private key must be 32 bytes (64 hex characters)";
    internal const string PrivateKeyOutOfRange = "private key must be in range [1, n-1]";
    internal const string PrivateKeyBytesLength = "private key must be 32 bytes";
    internal const string PublicKeyNotHex = "must be hex, with an optional 0x or 0X prefix";
    internal const string PublicKeyNotAPoint = "not a point on secp256k1";
    internal const string NetworkPublicKeyNotSet = "network public key is not set";
    internal static string NetworkPublicKeyInvalid(string reason) => $"invalid network public key: {reason}";
    internal const string SignerNull = "signer must not be null";
    internal const string DigestLength = "digest must be 32 bytes";
    internal const string SignerSignatureInvalid = "signature must be 64 or 65 bytes";
    internal const string SignerPublicKeyInvalid = "public key must be 65 bytes, uncompressed";
    internal const string RecoveryIdNotFound = "Could not determine recovery ID";

    // Client.
    internal const string BaseUrlNotSet = "base URL is not set";
    internal const string BaseUrlNotValid = "base URL is not valid";
    internal const string TimeoutNotValid = "timeout must be a positive duration of at most 2147483647 ms";
    internal const string StreamTimeoutNotValid = "stream timeout must be a positive duration of at most 2147483647 ms";
    internal static string SigningFailed(string cause) => $"signing the request failed: {cause}";
    internal const string BidiNotSupported = "bidirectional streams are not supported";
    internal const string FirstMessageIncomplete = "streaming request ends inside its first message";
    internal const string ClientStreamAborted =
        "The request body of a streaming call cannot be sent: the request was aborted.";
    internal const string ClientStreamResend =
        "The request body of a streaming call cannot be sent again once forwarding of its later messages has started.";

    // Setup.
    internal static string ArgumentNull(string argument) => $"{argument} must not be null";
    internal const string PortNotValid = "port must be between 0 and 65535";

    // Server.
    internal static string MissingHeader(string header) => $"missing required header: {header}";
    internal static string InvalidHeaderEncoding(string header) => $"invalid header encoding: {header}";
    internal const string TimestampNotDecimal = "invalid timestamp header: not a decimal number";
    internal const string TimestampOutOfRange = "invalid timestamp header: value out of range";
    internal const string TimestampOutsideWindow = "timestamp is outside the allowed time window";
    internal const string UnknownPublicKey = "request signed with unknown public key";
    internal const string SignatureVerificationFailed = "signature verification failed";
    internal static string BodyTooLarge(long limit) => $"max payload size of {limit} bytes exceeded";
    internal static string UnknownService(string service) => $"unknown service '{service}'";
    internal static string ResponseInvalid(string violations) => $"response validation failed: {violations}";
    internal static string ResponseValidationError(string cause) => $"response validation error: {cause}";
}
