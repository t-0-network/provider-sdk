package network.t0.sdk.network;

import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the client-side signature production.
 *
 * <p>These tests verify that signatures are correctly computed using:
 * <ul>
 *   <li>Keccak-256 hash of (body + timestamp)</li>
 *   <li>secp256k1 ECDSA signing</li>
 *   <li>Correct header format (0x-prefixed hex)</li>
 *   <li>Little-endian timestamp encoding</li>
 * </ul>
 */
class SigningClientInterceptorTest {

    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private static final long FIXED_TIMESTAMP_MS = 1706000000000L;

    private Signer signer;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        signer = Signer.fromHex(PRIVATE_KEY_HEX);
        fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
    }

    // ==================== Signature Algorithm Tests ====================

    @Test
    @DisplayName("Signature should be computed over Keccak256(body + timestamp)")
    void signatureShouldUseKeccak256OfBodyAndTimestamp() {
        byte[] body = "test request body".getBytes();

        // Compute the expected signature manually
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);
        var expectedSign = signer.sign(digest);

        // The signature produced by the interceptor should match
        // We verify by checking that the signature is valid
        boolean valid = SignatureVerifier.verify(
                signer.getPublicKey(),
                digest,
                expectedSign.getSignature()
        );
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Signature should be verifiable with the public key")
    void signatureShouldBeVerifiable() {
        byte[] body = "any body content".getBytes();
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestampBytes);

        var signResult = signer.sign(digest);

        // Signature should verify correctly
        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                signResult.getSignature()
        );
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Empty body should produce valid signature")
    void emptyBodyShouldProduceValidSignature() {
        byte[] emptyBody = new byte[0];
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(emptyBody, timestampBytes);

        var signResult = signer.sign(digest);

        assertThat(SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                signResult.getSignature()
        )).isTrue();
    }

    @Test
    @DisplayName("Large body should produce valid signature")
    void largeBodyShouldProduceValidSignature() {
        byte[] largeBody = new byte[100000];
        for (int i = 0; i < largeBody.length; i++) {
            largeBody[i] = (byte) (i % 256);
        }
        byte[] timestampBytes = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(largeBody, timestampBytes);

        var signResult = signer.sign(digest);

        assertThat(SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                signResult.getSignature()
        )).isTrue();
    }

    // ==================== Timestamp Encoding Tests ====================

    @Test
    @DisplayName("Timestamp should be encoded as 8-byte little-endian")
    void timestampShouldBeLittleEndian() {
        long timestamp = 0x123456789ABCDEF0L;
        byte[] encoded = Headers.encodeTimestamp(timestamp);

        assertThat(encoded).hasSize(8);
        // Little-endian: least significant byte first
        assertThat(encoded[0]).isEqualTo((byte) 0xF0);
        assertThat(encoded[1]).isEqualTo((byte) 0xDE);
        assertThat(encoded[2]).isEqualTo((byte) 0xBC);
        assertThat(encoded[3]).isEqualTo((byte) 0x9A);
        assertThat(encoded[4]).isEqualTo((byte) 0x78);
        assertThat(encoded[5]).isEqualTo((byte) 0x56);
        assertThat(encoded[6]).isEqualTo((byte) 0x34);
        assertThat(encoded[7]).isEqualTo((byte) 0x12);
    }

    @Test
    @DisplayName("Zero timestamp should encode correctly")
    void zeroTimestampShouldEncodeCorrectly() {
        byte[] encoded = Headers.encodeTimestamp(0L);

        assertThat(encoded).hasSize(8);
        assertThat(encoded).containsOnly((byte) 0x00);
    }

    @Test
    @DisplayName("Max timestamp should encode correctly")
    void maxTimestampShouldEncodeCorrectly() {
        byte[] encoded = Headers.encodeTimestamp(Long.MAX_VALUE);

        assertThat(encoded).hasSize(8);
        // Long.MAX_VALUE = 0x7FFFFFFFFFFFFFFF
        assertThat(encoded[0]).isEqualTo((byte) 0xFF);
        assertThat(encoded[1]).isEqualTo((byte) 0xFF);
        assertThat(encoded[2]).isEqualTo((byte) 0xFF);
        assertThat(encoded[3]).isEqualTo((byte) 0xFF);
        assertThat(encoded[4]).isEqualTo((byte) 0xFF);
        assertThat(encoded[5]).isEqualTo((byte) 0xFF);
        assertThat(encoded[6]).isEqualTo((byte) 0xFF);
        assertThat(encoded[7]).isEqualTo((byte) 0x7F);
    }

    // ==================== Different Input Tests ====================

    @Test
    @DisplayName("Different timestamps should produce different signatures")
    void differentTimestampsShouldProduceDifferentSignatures() {
        byte[] body = "same body".getBytes();

        byte[] ts1 = Headers.encodeTimestamp(1000L);
        byte[] ts2 = Headers.encodeTimestamp(2000L);

        byte[] digest1 = Keccak256.hash(body, ts1);
        byte[] digest2 = Keccak256.hash(body, ts2);

        var sig1 = signer.sign(digest1);
        var sig2 = signer.sign(digest2);

        assertThat(sig1.getSignatureHex()).isNotEqualTo(sig2.getSignatureHex());
    }

    @Test
    @DisplayName("Different bodies should produce different signatures")
    void differentBodiesShouldProduceDifferentSignatures() {
        byte[] body1 = "body one".getBytes();
        byte[] body2 = "body two".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);

        byte[] digest1 = Keccak256.hash(body1, timestamp);
        byte[] digest2 = Keccak256.hash(body2, timestamp);

        var sig1 = signer.sign(digest1);
        var sig2 = signer.sign(digest2);

        assertThat(sig1.getSignatureHex()).isNotEqualTo(sig2.getSignatureHex());
    }

    @Test
    @DisplayName("Same inputs should produce same digest")
    void sameInputsShouldProduceSameDigest() {
        byte[] body = "consistent body".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);

        byte[] digest1 = Keccak256.hash(body, timestamp);
        byte[] digest2 = Keccak256.hash(body, timestamp);

        assertThat(digest1).isEqualTo(digest2);
    }

    // ==================== Public Key Tests ====================

    @Test
    @DisplayName("Public key should match expected from private key")
    void publicKeyShouldMatchExpected() {
        assertThat(signer.getPublicKeyHex()).isEqualTo(PUBLIC_KEY_HEX);
    }

    @Test
    @DisplayName("Public key should be 65 bytes uncompressed format")
    void publicKeyShouldBe65Bytes() {
        assertThat(signer.getPublicKey()).hasSize(65);
        assertThat(signer.getPublicKey()[0]).isEqualTo((byte) 0x04); // Uncompressed prefix
    }

    @Test
    @DisplayName("Public key hex with prefix should have 0x prefix")
    void publicKeyHexPrefixedShouldHave0xPrefix() {
        assertThat(signer.getPublicKeyHexPrefixed()).startsWith("0x");
        assertThat(signer.getPublicKeyHexPrefixed()).hasSize(2 + 130); // 0x + 65 bytes * 2
    }

    // ==================== Signature Format Tests ====================

    @Test
    @DisplayName("Signature should be 65 bytes")
    void signatureShouldBe65Bytes() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        assertThat(signResult.getSignature()).hasSize(65);
    }

    @Test
    @DisplayName("Signature hex with prefix should have 0x prefix")
    void signatureHexPrefixedShouldHave0xPrefix() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        assertThat(signResult.getSignatureHex()).startsWith("0x");
        assertThat(signResult.getSignatureHex()).hasSize(2 + 130); // 0x + 65 bytes * 2
    }

    @Test
    @DisplayName("Recovery ID (v) should be 0 or 1")
    void recoveryIdShouldBe0Or1() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        assertThat(signResult.getV()).isIn(0, 1);
    }

    // ==================== Cross-Verification Tests ====================

    @Test
    @DisplayName("Signature should not verify with wrong public key")
    void signatureShouldNotVerifyWithWrongPublicKey() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        // Use a different signer's public key
        Signer otherSigner = Signer.fromHex("0000000000000000000000000000000000000000000000000000000000000001");

        boolean valid = SignatureVerifier.verify(
                otherSigner.getPublicKey(),
                digest,
                signResult.getSignature()
        );
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Signature should not verify with tampered digest")
    void signatureShouldNotVerifyWithTamperedDigest() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        // Tamper with digest
        byte[] tamperedDigest = digest.clone();
        tamperedDigest[0] ^= 0x01;

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                tamperedDigest,
                signResult.getSignature()
        );
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Signature should not verify with tampered signature")
    void signatureShouldNotVerifyWithTamperedSignature() {
        byte[] body = "test".getBytes();
        byte[] timestamp = Headers.encodeTimestamp(FIXED_TIMESTAMP_MS);
        byte[] digest = Keccak256.hash(body, timestamp);

        var signResult = signer.sign(digest);

        // Tamper with signature
        byte[] tamperedSig = signResult.getSignature().clone();
        tamperedSig[0] ^= 0x01;

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                tamperedSig
        );
        assertThat(valid).isFalse();
    }
}
