package network.t0.sdk.provider;

import io.grpc.*;
import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

/**
 * gRPC server interceptor that verifies request signatures from the T-0 Network.
 *
 * <p>This interceptor works with raw request bytes by using
 * {@link ServerInterceptors#useInputStreamMessages(ServerServiceDefinition)}.
 * When used this way, the request message is received as an InputStream,
 * allowing access to the raw protobuf bytes for signature verification.
 *
 * <p>The verification process:
 * <ol>
 *   <li>Extract headers: X-Signature, X-Public-Key, X-Signature-Timestamp</li>
 *   <li>Validate all required headers are present and properly encoded</li>
 *   <li>Validate timestamp is within the allowed window (60 seconds)</li>
 *   <li>Read the raw request body from InputStream</li>
 *   <li>Compute: digest = Keccak256(body + timestampBytes)</li>
 *   <li>Verify the signature against the expected network public key, accepting either
 *       unframed (raw protobuf) or gRPC-framed (5-byte prefix + protobuf) framings —
 *       see {@link #verifySignature} for the rationale and {@code docs/java/SIGNATURE_VERIFICATION.md}
 *       for the precise signing-payload definitions per caller and transport.</li>
 * </ol>
 *
 * <p>Error handling:
 * <ul>
 *   <li>Missing/invalid headers → INVALID_ARGUMENT</li>
 *   <li>Timestamp outside window → INVALID_ARGUMENT</li>
 *   <li>Wrong public key → UNAUTHENTICATED</li>
 *   <li>Invalid signature → UNAUTHENTICATED</li>
 * </ul>
 *
 * <p>This class is thread-safe. Each call to {@link #interceptCall} creates
 * independent state for that specific call.
 */
public final class SignatureVerificationInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerificationInterceptor.class);

    /**
     * Size of the gRPC frame header in bytes.
     * Format: 1 byte compressed flag + 4 bytes length (big-endian).
     */
    private static final int GRPC_FRAME_HEADER_SIZE = 5;

    private static final Metadata.Key<String> SIGNATURE_KEY =
            Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PUBLIC_KEY_KEY =
            Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TIMESTAMP_KEY =
            Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expectedNetworkPublicKey;
    private final Clock clock;

    /**
     * Creates a new signature verification interceptor.
     *
     * @param networkPublicKeyHex the expected T-0 Network public key in hex format
     */
    public SignatureVerificationInterceptor(String networkPublicKeyHex) {
        this(networkPublicKeyHex, Clock.systemUTC());
    }

    /**
     * Creates a new signature verification interceptor with a custom clock (for testing).
     *
     * @param networkPublicKeyHex the expected T-0 Network public key in hex format
     * @param clock               the clock to use for timestamp validation
     */
    public SignatureVerificationInterceptor(String networkPublicKeyHex, Clock clock) {
        if (networkPublicKeyHex == null || networkPublicKeyHex.isEmpty()) {
            throw new IllegalArgumentException("networkPublicKeyHex must not be null or empty");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.expectedNetworkPublicKey = SignatureVerifier.parsePublicKeyHex(networkPublicKeyHex);
        this.clock = clock;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Extract and validate headers upfront
        String signatureHex = headers.get(SIGNATURE_KEY);
        String publicKeyHex = headers.get(PUBLIC_KEY_KEY);
        String timestampStr = headers.get(TIMESTAMP_KEY);

        // Validate public key header
        ValidationResult publicKeyResult = validatePublicKeyHeader(publicKeyHex);
        if (publicKeyResult instanceof ValidationResult.Invalid invalid) {
            return rejectCall(call, invalid.status(), invalid.message());
        }
        byte[] publicKey = ((ValidationResult.ValidBytes) publicKeyResult).bytes();

        // Validate signature header
        ValidationResult signatureResult = validateSignatureHeader(signatureHex);
        if (signatureResult instanceof ValidationResult.Invalid invalid) {
            return rejectCall(call, invalid.status(), invalid.message());
        }
        byte[] signature = ((ValidationResult.ValidBytes) signatureResult).bytes();

        // Validate timestamp header
        ValidationResult timestampResult = validateTimestampHeader(timestampStr);
        if (timestampResult instanceof ValidationResult.Invalid invalid) {
            return rejectCall(call, invalid.status(), invalid.message());
        }
        long timestampMs = ((ValidationResult.ValidTimestamp) timestampResult).timestamp();

        // Verify public key matches expected network public key
        if (!SignatureVerifier.publicKeysEqual(publicKey, expectedNetworkPublicKey)) {
            log.warn("Request signed with unknown public key");
            return rejectCall(call, Status.UNAUTHENTICATED, "request signed with unknown public key");
        }

        // Return a listener that will verify the message when received
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                next.startCall(call, headers)) {

            @Override
            @SuppressWarnings("unchecked")
            public void onMessage(ReqT message) {
                // When using useInputStreamMessages, message is an InputStream
                if (message instanceof InputStream inputStream) {
                    try {
                        // Read the raw bytes
                        byte[] bodyBytes = inputStream.readAllBytes();

                        // Verify signature
                        if (!verifySignature(publicKey, bodyBytes, timestampMs, signature)) {
                            log.warn("Signature verification failed");
                            call.close(Status.UNAUTHENTICATED.withDescription("signature verification failed"), new Metadata());
                            return;
                        }

                        // Reconstruct InputStream for downstream processing
                        ReqT reconstructed = (ReqT) new ByteArrayInputStream(bodyBytes);
                        super.onMessage(reconstructed);

                    } catch (IOException e) {
                        log.error("Error reading request body", e);
                        call.close(Status.INTERNAL.withDescription("error reading request body"), new Metadata());
                    }
                } else {
                    // CRITICAL: Cannot verify signature without raw bytes.
                    // Server MUST use ServerInterceptors.useInputStreamMessages().
                    log.error("SignatureVerificationInterceptor requires useInputStreamMessages() configuration");
                    call.close(Status.INTERNAL.withDescription(
                            "server misconfiguration: signature verification requires raw bytes"), new Metadata());
                }
            }
        };
    }

    /**
     * Verifies the signature against the message body.
     *
     * <p><b>DUAL-PATH IS LOAD-BEARING — DO NOT REMOVE EITHER PATH.</b>
     *
     * <p>Both verification paths below are exercised by real production traffic. The asymmetry
     * exists because different signers sit at different layers relative to the gRPC framer:
     *
     * <ul>
     *   <li><b>Path 1 — unframed (raw protobuf):</b> Used by callers whose signing interceptor
     *       sits <i>above</i> the gRPC framer. This includes the Java SDK's own
     *       {@code NetworkClient} (signs bytes pulled from the marshaller stream before framing)
     *       and any caller whose transport is Connect protocol (where no gRPC frame exists at
     *       all). The T-0 Network signs unframed bytes when configured to call this provider via
     *       Connect protocol.</li>
     *   <li><b>Path 2 — gRPC-framed:</b> Used when the signer sits <i>below</i> the gRPC framer
     *       and signs the on-wire body. The signed payload then includes the 5-byte gRPC frame
     *       prefix (1 byte compressed flag + 4 bytes big-endian length) followed by the protobuf
     *       message bytes. The T-0 Network signs framed bodies when configured to call this
     *       provider via gRPC protocol.</li>
     * </ul>
     *
     * <p>Removing path 2 silently breaks all traffic from the T-0 Network whenever it is
     * configured to call this provider via gRPC protocol. Removing path 1 silently breaks the
     * Java SDK's own {@code NetworkClient}, plus T-0 Network traffic configured to use Connect
     * protocol. Both failures surface only as {@code UNAUTHENTICATED} responses for one class of
     * caller but not the other — exactly the kind of stealth breakage that looks like an
     * unrelated bug.
     *
     * <p>For the full architectural rationale, the network's signing behavior in detail, and
     * conditions under which simplification would be safe, see
     * {@code docs/java/SIGNATURE_VERIFICATION.md}.
     *
     * @param publicKey the public key to verify against
     * @param bodyBytes the raw protobuf message bytes (without gRPC frame, as delivered by
     *                  {@link ServerInterceptors#useInputStreamMessages})
     * @param timestampMs the request timestamp in milliseconds
     * @param signature the signature to verify
     * @return true if signature is valid for either framing
     */
    private boolean verifySignature(byte[] publicKey, byte[] bodyBytes, long timestampMs, byte[] signature) {
        byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);

        // Path 1: unframed (raw protobuf). Matches the Java SDK's NetworkClient, Connect-
        // protocol callers (no frame exists), and the T-0 Network when configured to call
        // this provider via Connect protocol.
        byte[] digestRaw = Keccak256.hash(bodyBytes, timestampBytes);
        if (SignatureVerifier.verify(publicKey, digestRaw, signature)) {
            log.debug("Signature verified against unframed body");
            return true;
        }

        // Path 2: gRPC-framed (5-byte prefix + protobuf). Matches the T-0 Network when
        // configured to call this provider via gRPC protocol. The signer sits below the
        // gRPC framer, so the signed payload includes the on-wire frame.
        // gRPC frame format: 1 byte compressed flag (0) + 4 bytes length (big-endian).
        byte[] framedBytes = reconstructGrpcFrame(bodyBytes);
        byte[] digestFramed = Keccak256.hash(framedBytes, timestampBytes);
        if (SignatureVerifier.verify(publicKey, digestFramed, signature)) {
            log.debug("Signature verified against gRPC-framed body");
            return true;
        }

        log.debug("Signature verification failed for both unframed and gRPC-framed payloads");
        return false;
    }

    /**
     * Reconstructs the gRPC frame prefix for the given message bytes.
     *
     * <p>gRPC frame format (5 bytes):
     * <ul>
     *   <li>Byte 0: Compressed flag (0 = uncompressed)</li>
     *   <li>Bytes 1-4: Message length in big-endian format</li>
     * </ul>
     *
     * @param messageBytes the protobuf message bytes
     * @return the message bytes with gRPC frame prefix prepended
     */
    private byte[] reconstructGrpcFrame(byte[] messageBytes) {
        byte[] framed = new byte[GRPC_FRAME_HEADER_SIZE + messageBytes.length];

        // Byte 0: Compressed flag (0 = not compressed)
        framed[0] = 0;

        // Bytes 1-4: Message length in big-endian
        int length = messageBytes.length;
        framed[1] = (byte) ((length >> 24) & 0xFF);
        framed[2] = (byte) ((length >> 16) & 0xFF);
        framed[3] = (byte) ((length >> 8) & 0xFF);
        framed[4] = (byte) (length & 0xFF);

        // Copy message bytes
        System.arraycopy(messageBytes, 0, framed, GRPC_FRAME_HEADER_SIZE, messageBytes.length);

        return framed;
    }

    private ValidationResult validatePublicKeyHeader(String publicKeyHex) {
        if (publicKeyHex == null || publicKeyHex.isEmpty()) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "missing required header: " + Headers.PUBLIC_KEY);
        }

        if (publicKeyHex.length() < 2) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "invalid header encoding: " + Headers.PUBLIC_KEY);
        }

        try {
            String hex = HexUtils.stripHexPrefix(publicKeyHex);
            byte[] publicKey = HexUtils.hexToBytes(hex);
            return new ValidationResult.ValidBytes(publicKey);
        } catch (IllegalArgumentException e) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "invalid header encoding: " + Headers.PUBLIC_KEY);
        }
    }

    private ValidationResult validateSignatureHeader(String signatureHex) {
        if (signatureHex == null || signatureHex.isEmpty()) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "missing required header: " + Headers.SIGNATURE);
        }

        if (signatureHex.length() < 2) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "invalid header encoding: " + Headers.SIGNATURE);
        }

        try {
            String hex = HexUtils.stripHexPrefix(signatureHex);
            byte[] signature = HexUtils.hexToBytes(hex);
            return new ValidationResult.ValidBytes(signature);
        } catch (IllegalArgumentException e) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "invalid header encoding: " + Headers.SIGNATURE);
        }
    }

    private ValidationResult validateTimestampHeader(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "missing required header: " + Headers.SIGNATURE_TIMESTAMP);
        }

        long timestampMs;
        try {
            timestampMs = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "invalid timestamp header: " + e.getMessage());
        }

        // Validate timestamp is within the allowed window
        long currentMs = clock.millis();
        long diff = Math.abs(currentMs - timestampMs);
        if (diff > Headers.TIMESTAMP_VALIDITY_WINDOW_MS) {
            return new ValidationResult.Invalid(Status.INVALID_ARGUMENT,
                    "timestamp is outside the allowed time window");
        }

        return new ValidationResult.ValidTimestamp(timestampMs);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> rejectCall(
            ServerCall<ReqT, RespT> call, Status status, String message) {
        call.close(status.withDescription(message), new Metadata());
        return new ServerCall.Listener<ReqT>() {
            // No-op listener for rejected calls
        };
    }

    /**
     * Sealed interface for validation results.
     */
    private sealed interface ValidationResult {
        record Invalid(Status status, String message) implements ValidationResult {}
        record ValidBytes(byte[] bytes) implements ValidationResult {}
        record ValidTimestamp(long timestamp) implements ValidationResult {}
    }
}
