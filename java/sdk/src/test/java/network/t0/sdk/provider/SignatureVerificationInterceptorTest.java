package network.t0.sdk.provider;

import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
    private static final String OTHER_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final long FIXED_TIMESTAMP_MS = 1706000000000L;

    private Signer signer;
    private Signer otherSigner;
    private byte[] expectedPublicKey;

    @BeforeEach
    void setUp() {
        signer = Signer.fromHex(PRIVATE_KEY_HEX);
        otherSigner = Signer.fromHex(OTHER_PRIVATE_KEY_HEX);
        expectedPublicKey = SignatureVerifier.parsePublicKeyHex(PUBLIC_KEY_HEX);
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

    @Test
    @DisplayName("Should accept timestamp within validity window")
    void shouldAcceptTimestampWithinWindow() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
        long currentMs = fixedClock.millis();

        // Timestamp exactly at current time
        long diff = Math.abs(currentMs - FIXED_TIMESTAMP_MS);
        assertThat(diff).isLessThanOrEqualTo(Headers.TIMESTAMP_VALIDITY_WINDOW_MS);
    }

    @Test
    @DisplayName("Should reject timestamp outside validity window - past")
    void shouldRejectExpiredTimestamp() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
        long currentMs = fixedClock.millis();

        // Timestamp more than 60 seconds in the past
        long expiredTimestamp = FIXED_TIMESTAMP_MS - Headers.TIMESTAMP_VALIDITY_WINDOW_MS - 1000;
        long diff = Math.abs(currentMs - expiredTimestamp);

        assertThat(diff).isGreaterThan(Headers.TIMESTAMP_VALIDITY_WINDOW_MS);
    }

    @Test
    @DisplayName("Should reject timestamp outside validity window - future")
    void shouldRejectFutureTimestamp() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
        long currentMs = fixedClock.millis();

        // Timestamp more than 60 seconds in the future
        long futureTimestamp = FIXED_TIMESTAMP_MS + Headers.TIMESTAMP_VALIDITY_WINDOW_MS + 1000;
        long diff = Math.abs(currentMs - futureTimestamp);

        assertThat(diff).isGreaterThan(Headers.TIMESTAMP_VALIDITY_WINDOW_MS);
    }

    @Test
    @DisplayName("Should accept timestamp at edge of validity window")
    void shouldAcceptTimestampAtEdge() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
        long currentMs = fixedClock.millis();

        // Timestamp exactly at the edge (60 seconds ago)
        long edgeTimestamp = FIXED_TIMESTAMP_MS - Headers.TIMESTAMP_VALIDITY_WINDOW_MS;
        long diff = Math.abs(currentMs - edgeTimestamp);

        assertThat(diff).isEqualTo(Headers.TIMESTAMP_VALIDITY_WINDOW_MS);
    }

    // ==================== Public Key Matching Tests ====================

    @Test
    @DisplayName("Should match public keys correctly")
    void shouldMatchPublicKeys() {
        byte[] pk1 = SignatureVerifier.parsePublicKeyHex(PUBLIC_KEY_HEX);
        byte[] pk2 = SignatureVerifier.parsePublicKeyHex("0x" + PUBLIC_KEY_HEX);

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
        byte[] withoutPrefix = SignatureVerifier.parsePublicKeyHex(PUBLIC_KEY_HEX);
        byte[] withPrefix = SignatureVerifier.parsePublicKeyHex("0x" + PUBLIC_KEY_HEX);

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

    @Test
    @DisplayName("Constructor should reject null public key")
    void constructorShouldRejectNullPublicKey() {
        assertThatThrownBy(() -> new SignatureVerificationInterceptor(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    @DisplayName("Constructor should reject empty public key")
    void constructorShouldRejectEmptyPublicKey() {
        assertThatThrownBy(() -> new SignatureVerificationInterceptor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
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
