package network.t0.sdk.provider;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the server-side signature verification.
 *
 * <p>These tests verify that signatures are correctly verified using:
 * <ul>
 *   <li>Keccak-256 hash of (body + timestamp)</li>
 *   <li>secp256k1 ECDSA verification</li>
 *   <li>Public key matching</li>
 *   <li>Timestamp validation</li>
 * </ul>
 */
class SignatureVerificationInterceptorTest {

    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private static final String COMPRESSED_PUBLIC_KEY_HEX = "024fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567";
    private static final String OTHER_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001";
    // The generator point: the public key of OTHER_PRIVATE_KEY_HEX, compressed.
    private static final String OTHER_COMPRESSED_PUBLIC_KEY_HEX = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final long FIXED_TIMESTAMP_MS = 1706000000000L;
    private static final byte[] BODY = "test request body".getBytes();

    private Signer signer;
    private Signer otherSigner;
    private byte[] expectedPublicKey;

    @BeforeEach
    void setUp() {
        signer = Signer.fromHex(PRIVATE_KEY_HEX);
        otherSigner = Signer.fromHex(OTHER_PRIVATE_KEY_HEX);
        expectedPublicKey = SignatureVerificationInterceptor.parsePublicKey(PUBLIC_KEY_HEX);
    }

    // ==================== Valid Signature Tests ====================

    @Test
    @DisplayName("Should verify valid signature with correct public key")
    void shouldVerifyValidSignature() {
        byte[] body = "test request body".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                signResult.getSignature()
        );

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should verify signature for empty body")
    void shouldVerifyEmptyBody() {
        byte[] body = new byte[0];
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                signResult.getSignature()
        );

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should verify signature for large body")
    void shouldVerifyLargeBody() {
        byte[] body = new byte[100000];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i % 256);
        }
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                signResult.getSignature()
        );

        assertThat(valid).isTrue();
    }

    // ==================== Invalid Signature Tests ====================

    @Test
    @DisplayName("Should reject signature from wrong private key")
    void shouldRejectWrongPrivateKey() {
        byte[] body = "test".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        // Sign with different key
        SignResult signResult = otherSigner.sign(digest);

        // Verify against expected public key (should fail)
        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                signResult.getSignature()
        );

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered body")
    void shouldRejectTamperedBody() {
        byte[] originalBody = "original".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] originalDigest = Keccak256.hash(originalBody, timestampBytes);

        SignResult signResult = signer.sign(originalDigest);

        // Try to verify with tampered body
        byte[] tamperedBody = "tampered".getBytes();
        byte[] tamperedDigest = Keccak256.hash(tamperedBody, timestampBytes);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                tamperedDigest,
                signResult.getSignature()
        );

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered timestamp")
    void shouldRejectTamperedTimestamp() {
        byte[] body = "test".getBytes();
        byte[] originalTimestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] originalDigest = Keccak256.hash(body, originalTimestamp);

        SignResult signResult = signer.sign(originalDigest);

        // Try to verify with different timestamp
        byte[] tamperedTimestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS + 1);
        byte[] tamperedDigest = Keccak256.hash(body, tamperedTimestamp);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                tamperedDigest,
                signResult.getSignature()
        );

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered signature bytes")
    void shouldRejectTamperedSignature() {
        byte[] body = "test".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        SignResult signResult = signer.sign(digest);

        // Tamper with signature
        byte[] tamperedSig = signResult.getSignature().clone();
        tamperedSig[0] ^= 0x01;

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                tamperedSig
        );

        assertThat(valid).isFalse();
    }

    // ==================== Timestamp Validation Tests ====================
    // Valid signatures on a fake call with a fixed clock: only the timestamp decides.

    @Test
    @DisplayName("Should accept timestamp within validity window and pass the call on")
    void shouldAcceptTimestampWithinWindow() {
        CallOutcome outcome = interceptAt(FIXED_TIMESTAMP_MS - 30_000);

        assertThat(outcome.call.closeStatus).isNull();
        assertThat(outcome.handler.started).isTrue();
        assertThat(outcome.handler.messages).containsExactly(BODY);
    }

    @Test
    @DisplayName("Should accept timestamp at edge of validity window")
    void shouldAcceptTimestampAtEdge() {
        CallOutcome outcome = interceptAt(FIXED_TIMESTAMP_MS - ProviderServer.TIMESTAMP_WINDOW.toMillis());

        assertThat(outcome.call.closeStatus).isNull();
        assertThat(outcome.handler.messages).containsExactly(BODY);
    }

    @Test
    @DisplayName("Should reject timestamp outside validity window - past")
    void shouldRejectExpiredTimestamp() {
        CallOutcome outcome = interceptAt(FIXED_TIMESTAMP_MS - ProviderServer.TIMESTAMP_WINDOW.toMillis() - 1000);

        assertRejectedForTimeWindow(outcome);
    }

    @Test
    @DisplayName("Should reject timestamp outside validity window - future")
    void shouldRejectFutureTimestamp() {
        CallOutcome outcome = interceptAt(FIXED_TIMESTAMP_MS + ProviderServer.TIMESTAMP_WINDOW.toMillis() + 1000);

        assertRejectedForTimeWindow(outcome);
    }

    @Test
    @DisplayName("Should accept a timestamp with leading zeros")
    void shouldAcceptTimestampWithLeadingZeros() {
        CallOutcome outcome = intercept(signer, "0x" + PUBLIC_KEY_HEX, FIXED_TIMESTAMP_MS, "000" + FIXED_TIMESTAMP_MS);

        assertThat(outcome.call.closeStatus).isNull();
        assertThat(outcome.handler.messages).containsExactly(BODY);
    }

    @Test
    @DisplayName("Should reject a signed timestamp with a plus sign, which Long.parseLong accepts")
    void shouldRejectTimestampWithPlusSign() {
        CallOutcome outcome = intercept(signer, "0x" + PUBLIC_KEY_HEX, FIXED_TIMESTAMP_MS, "+" + FIXED_TIMESTAMP_MS);

        assertRejected(outcome, Status.Code.INVALID_ARGUMENT, "invalid timestamp header");
    }

    private static void assertRejectedForTimeWindow(CallOutcome outcome) {
        assertRejected(outcome, Status.Code.INVALID_ARGUMENT, "time window");
    }

    private static void assertRejected(CallOutcome outcome, Status.Code code, String description) {
        assertThat(outcome.call.closeStatus).isNotNull();
        assertThat(outcome.call.closeStatus.getCode()).isEqualTo(code);
        assertThat(outcome.call.closeStatus.getDescription()).contains(description);
        assertThat(outcome.handler.started).isFalse();
        assertThat(outcome.handler.messages).isEmpty();
    }

    // ==================== X-Public-Key Header Tests ====================
    // Valid signatures and timestamps on a fake call: only the X-Public-Key header decides.

    static List<Arguments> acceptedPublicKeyHeaders() {
        return List.of(
                Arguments.of("uncompressed", "0x" + PUBLIC_KEY_HEX),
                Arguments.of("uncompressed, 0X prefix", "0X" + PUBLIC_KEY_HEX),
                Arguments.of("compressed", "0x" + COMPRESSED_PUBLIC_KEY_HEX),
                Arguments.of("compressed, 0X prefix", "0X" + COMPRESSED_PUBLIC_KEY_HEX),
                Arguments.of("compressed, no prefix", COMPRESSED_PUBLIC_KEY_HEX));
    }

    @ParameterizedTest(name = "accepts the network key {0}")
    @MethodSource("acceptedPublicKeyHeaders")
    void shouldAcceptNetworkKeyInEitherForm(String name, String header) {
        CallOutcome outcome = intercept(signer, header, FIXED_TIMESTAMP_MS);

        assertThat(outcome.call.closeStatus).isNull();
        assertThat(outcome.handler.messages).containsExactly(BODY);
    }

    @Test
    @DisplayName("Should reject a request without X-Public-Key as INVALID_ARGUMENT")
    void shouldRejectMissingPublicKeyHeader() {
        assertRejected(intercept(signer, null, FIXED_TIMESTAMP_MS),
                Status.Code.INVALID_ARGUMENT, "missing required header");
    }

    static List<Arguments> unknownPublicKeyHeaders() {
        return List.of(
                // Not hex
                Arguments.of("prefix only", "0x"),
                Arguments.of("odd length", "0x" + PUBLIC_KEY_HEX.substring(1)),
                Arguments.of("whitespace inside", "0x" + PUBLIC_KEY_HEX.substring(0, 66) + " " + PUBLIC_KEY_HEX.substring(66)),
                Arguments.of("trailing junk", "0x" + PUBLIC_KEY_HEX + "zz"),
                Arguments.of("non-hex", "0xnot-a-key"),
                // Hex, but not a key
                Arguments.of("truncated", "0x" + PUBLIC_KEY_HEX.substring(0, 128)),
                Arguments.of("compressed prefix on 65 bytes", "0x02" + PUBLIC_KEY_HEX.substring(2)),
                Arguments.of("uncompressed prefix on 33 bytes", "0x04" + COMPRESSED_PUBLIC_KEY_HEX.substring(2)),
                Arguments.of("off-curve", "0x04" + "00".repeat(64)));
    }

    @ParameterizedTest(name = "rejects {0} as an unknown key")
    @MethodSource("unknownPublicKeyHeaders")
    void shouldRejectPublicKeyHeaderAsUnknown(String name, String header) {
        assertRejected(intercept(signer, header, FIXED_TIMESTAMP_MS),
                Status.Code.UNAUTHENTICATED, "request signed with unknown public key");
    }

    @Test
    @DisplayName("Should reject another key, compressed or uncompressed, as unknown")
    void shouldRejectRequestSignedByAnotherKey() {
        String otherPublicKeyHex = "0x" + HexUtils.bytesToHex(otherSigner.getPublicKey());

        assertRejected(intercept(otherSigner, otherPublicKeyHex, FIXED_TIMESTAMP_MS),
                Status.Code.UNAUTHENTICATED, "request signed with unknown public key");
        assertRejected(intercept(otherSigner, "0x" + OTHER_COMPRESSED_PUBLIC_KEY_HEX, FIXED_TIMESTAMP_MS),
                Status.Code.UNAUTHENTICATED, "request signed with unknown public key");
    }

    private record CallOutcome(RecordingServerCall call, RecordingHandler handler) {}

    private CallOutcome interceptAt(long timestampMs) {
        return intercept(signer, "0x" + PUBLIC_KEY_HEX, timestampMs);
    }

    private CallOutcome intercept(Signer signer, String publicKeyHeader, long timestampMs) {
        return intercept(signer, publicKeyHeader, timestampMs, String.valueOf(timestampMs));
    }

    /**
     * Runs a call signed by {@code signer} at {@code timestampMs} over {@link #BODY}, with
     * {@code publicKeyHeader} as X-Public-Key (none if null) and {@code timestampHeader} as
     * X-Signature-Timestamp, through an interceptor expecting {@link #PUBLIC_KEY_HEX} whose clock
     * reads {@link #FIXED_TIMESTAMP_MS}, then delivers the body if the call was let through.
     */
    private CallOutcome intercept(Signer signer, String publicKeyHeader, long timestampMs, String timestampHeader) {
        SignResult signResult = signer.sign(Keccak256.hash(BODY, Headers.encodeTimestamp(timestampMs)));
        Metadata headers = new Metadata();
        if (publicKeyHeader != null) {
            headers.put(Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER), publicKeyHeader);
        }
        headers.put(Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER), signResult.getSignatureHex());
        headers.put(Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER), timestampHeader);

        SignatureVerificationInterceptor interceptor = new SignatureVerificationInterceptor(PUBLIC_KEY_HEX,
                Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC));
        RecordingServerCall call = new RecordingServerCall();
        RecordingHandler handler = new RecordingHandler();

        ServerCall.Listener<InputStream> listener = interceptor.interceptCall(call, headers, handler);
        if (call.closeStatus == null) {
            listener.onMessage(new ByteArrayInputStream(BODY));
        }
        return new CallOutcome(call, handler);
    }

    /** Records how the interceptor closes the call; the rest is unused by it. */
    private static final class RecordingServerCall extends ServerCall<InputStream, InputStream> {
        Status closeStatus;

        @Override
        public void close(Status status, Metadata trailers) {
            closeStatus = status;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(InputStream message) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<InputStream, InputStream> getMethodDescriptor() {
            return null;
        }
    }

    /** Stands in for the service: records whether the call reached it and the bytes it received. */
    private static final class RecordingHandler implements ServerCallHandler<InputStream, InputStream> {
        boolean started;
        final List<byte[]> messages = new ArrayList<>();

        @Override
        public ServerCall.Listener<InputStream> startCall(ServerCall<InputStream, InputStream> call, Metadata headers) {
            started = true;
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(InputStream message) {
                    try {
                        messages.add(message.readAllBytes());
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                }
            };
        }
    }

    // ==================== Public Key Matching Tests ====================

    @Test
    @DisplayName("Should match public keys correctly")
    void shouldMatchPublicKeys() {
        byte[] pk1 = SignatureVerificationInterceptor.parsePublicKey(PUBLIC_KEY_HEX);
        byte[] pk2 = SignatureVerificationInterceptor.parsePublicKey("0x" + PUBLIC_KEY_HEX);

        assertThat(SignatureVerifier.publicKeysEqual(pk1, pk2)).isTrue();
    }

    @Test
    @DisplayName("Should reject different public keys")
    void shouldRejectDifferentPublicKeys() {
        byte[] pk1 = signer.getPublicKey();
        byte[] pk2 = otherSigner.getPublicKey();

        assertThat(SignatureVerifier.publicKeysEqual(pk1, pk2)).isFalse();
    }

    @Test
    @DisplayName("Should handle 0x prefix in public key hex")
    void shouldHandle0xPrefixInPublicKey() {
        byte[] withoutPrefix = SignatureVerificationInterceptor.parsePublicKey(PUBLIC_KEY_HEX);
        byte[] withPrefix = SignatureVerificationInterceptor.parsePublicKey("0x" + PUBLIC_KEY_HEX);

        assertThat(withoutPrefix).isEqualTo(withPrefix);
    }

    // ==================== Digest Computation Tests ====================

    @Test
    @DisplayName("Digest should use Keccak-256")
    void digestShouldUseKeccak256() {
        byte[] body = "test request body".getBytes();
        long timestampMs = 1706000000000L;
        byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);

        byte[] digest = Keccak256.hash(body, timestampBytes);

        // Keccak-256 produces 32 bytes
        assertThat(digest).hasSize(32);
    }

    @Test
    @DisplayName("Digest computation should match expected values")
    void digestShouldMatchExpectedValues() {
        // Test vector from SignerTest
        byte[] body = "test request body".getBytes();
        long timestampMs = 1706000000000L;
        byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);

        byte[] digest = Keccak256.hash(body, timestampBytes);

        // Expected hash
        assertThat(HexUtils.bytesToHex(digest))
                .isEqualTo("49a567a359bf25d9652b24acc5567bc38c93139467c8fcf798f059ada585697e");
    }

    @Test
    @DisplayName("Digest should be different for different timestamps")
    void digestShouldDifferForDifferentTimestamps() {
        byte[] body = "same body".getBytes();

        byte[] ts1 = Headers.encodeTimestamp(1000L);
        byte[] ts2 = Headers.encodeTimestamp(2000L);

        byte[] digest1 = Keccak256.hash(body, ts1);
        byte[] digest2 = Keccak256.hash(body, ts2);

        assertThat(digest1).isNotEqualTo(digest2);
    }

    @Test
    @DisplayName("Digest should be different for different bodies")
    void digestShouldDifferForDifferentBodies() {
        byte[] body1 = "body one".getBytes();
        byte[] body2 = "body two".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);

        byte[] digest1 = Keccak256.hash(body1, timestamp);
        byte[] digest2 = Keccak256.hash(body2, timestamp);

        assertThat(digest1).isNotEqualTo(digest2);
    }

    // ==================== Constructor Validation Tests ====================

    @ParameterizedTest(name = "rejects missing key {0}")
    @NullSource
    @ValueSource(strings = {"", "  \n"})
    void constructorShouldRejectMissingPublicKey(String key) {
        assertThatThrownBy(() -> new SignatureVerificationInterceptor(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("network public key is not set");
    }

    static List<Arguments> malformedPublicKeys() {
        return List.of(
                Arguments.of("non-hex", "0xnot-a-key"),
                Arguments.of("prefix only", "0x"),
                Arguments.of("truncated", PUBLIC_KEY_HEX.substring(0, 128)),
                Arguments.of("compressed prefix on 65 bytes", "02" + PUBLIC_KEY_HEX.substring(2)),
                Arguments.of("uncompressed prefix on 33 bytes", "04" + COMPRESSED_PUBLIC_KEY_HEX.substring(2)),
                Arguments.of("off-curve", "04" + "00".repeat(64)));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("malformedPublicKeys")
    void constructorShouldRejectMalformedPublicKey(String name, String key) {
        assertThatThrownBy(() -> new SignatureVerificationInterceptor(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("invalid network public key: ");
    }

    @Test
    @DisplayName("Constructor should accept public key with surrounding whitespace")
    void constructorShouldAcceptPaddedPublicKey() {
        SignatureVerificationInterceptor interceptor = new SignatureVerificationInterceptor("  0x" + PUBLIC_KEY_HEX + "\n");
        assertThat(interceptor).isNotNull();
    }

    @Test
    @DisplayName("Constructor should accept valid public key")
    void constructorShouldAcceptValidPublicKey() {
        SignatureVerificationInterceptor interceptor = new SignatureVerificationInterceptor(PUBLIC_KEY_HEX);
        assertThat(interceptor).isNotNull();
    }

    @Test
    @DisplayName("Constructor should accept public key with 0x prefix")
    void constructorShouldAcceptPublicKeyWith0xPrefix() {
        SignatureVerificationInterceptor interceptor = new SignatureVerificationInterceptor("0x" + PUBLIC_KEY_HEX);
        assertThat(interceptor).isNotNull();
    }

    static List<Arguments> compressedPublicKeys() {
        return List.of(
                Arguments.of("0x" + COMPRESSED_PUBLIC_KEY_HEX, PUBLIC_KEY_HEX),
                Arguments.of("0x03fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556",
                        "04fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556"
                                + "ae12777aacfbb620f3be96017f45c560de80f0f6518fe4a03c870c36b075f297"));
    }

    @ParameterizedTest(name = "accepts compressed {0}")
    @MethodSource("compressedPublicKeys")
    void constructorShouldAcceptCompressedPublicKey(String key, String uncompressed) {
        assertThat(new SignatureVerificationInterceptor(key)).isNotNull();
        assertThat(HexUtils.bytesToHex(SignatureVerificationInterceptor.parseNetworkPublicKey(key)))
                .isEqualTo(uncompressed);
    }

    // ==================== CRITICAL: Raw Bytes Verification Tests ====================

    @Test
    @DisplayName("CRITICAL: Signature must be verified against exact raw bytes")
    void signatureMustBeVerifiedAgainstRawBytes() {
        // This test verifies the critical requirement from CLAUDE.md:
        // Signature verification MUST use raw payload bytes, not re-serialized bytes

        byte[] rawBytes = new byte[] { 0x08, (byte) 0x96, 0x01 }; // Simple protobuf: field 1, varint 150
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(rawBytes, timestampBytes);

        SignResult signResult = signer.sign(digest);

        // Verification with exact same bytes should succeed
        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                digest,
                signResult.getSignature()
        );
        assertThat(valid).isTrue();

        // Any different bytes should fail verification
        byte[] differentBytes = new byte[] { 0x08, (byte) 0x96, 0x02 }; // Different value
        byte[] differentDigest = Keccak256.hash(differentBytes, timestampBytes);

        boolean invalidVerify = SignatureVerifier.verify(
                expectedPublicKey,
                differentDigest,
                signResult.getSignature()
        );
        assertThat(invalidVerify).isFalse();
    }

    @Test
    @DisplayName("CRITICAL: Single byte difference should fail verification")
    void singleByteDifferenceShouldFailVerification() {
        byte[] body = "test body with some content".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        SignResult signResult = signer.sign(digest);

        // Change a single byte
        byte[] modifiedBody = body.clone();
        modifiedBody[5] ^= 0x01;

        byte[] modifiedDigest = Keccak256.hash(modifiedBody, timestampBytes);

        boolean valid = SignatureVerifier.verify(
                expectedPublicKey,
                modifiedDigest,
                signResult.getSignature()
        );

        assertThat(valid).isFalse();
    }
}
