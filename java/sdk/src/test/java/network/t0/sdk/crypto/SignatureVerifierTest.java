package network.t0.sdk.crypto;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SignatureVerifier.
 */
class SignatureVerifierTest {

    // Test vectors
    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private static final String MESSAGE_KECCAK_HASH = "46d5cbf7d8477720c337b94a2fe332fe54205914619e7e4889595c3c944b646b";

    @Test
    void verify_validSignature_shouldReturnTrue() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                signResult.getSignature());

        assertThat(valid).isTrue();
    }

    @Test
    void verify_withDifferentKey_shouldReturnFalse() {
        // Sign with one key
        Signer signer1 = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer1.sign(digest);

        // Verify with a different key
        Signer signer2 = Signer.fromHex("0000000000000000000000000000000000000000000000000000000000000001");

        boolean valid = SignatureVerifier.verify(
                signer2.getPublicKey(), // Different public key
                digest,
                signResult.getSignature());

        assertThat(valid).isFalse();
    }

    @Test
    void verify_tamperedDigest_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        // Tamper with the digest
        byte[] tamperedDigest = digest.clone();
        tamperedDigest[0] ^= 0xFF;

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                tamperedDigest,
                signResult.getSignature());

        assertThat(valid).isFalse();
    }

    @Test
    void verify_tamperedSignature_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        // Tamper with the signature
        byte[] tamperedSignature = signResult.getSignature().clone();
        tamperedSignature[0] ^= 0xFF;

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                tamperedSignature);

        assertThat(valid).isFalse();
    }

    @Test
    void verify_64ByteSignature_shouldWork() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        // Extract just r+s (64 bytes), without v
        byte[] signature64 = new byte[64];
        System.arraycopy(signResult.getSignature(), 0, signature64, 0, 64);

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                digest,
                signature64);

        assertThat(valid).isTrue();
    }

    @Test
    void verify_wrongDigestLength_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                signResult.getPublicKey(),
                new byte[31], // Wrong length
                signResult.getSignature());

        assertThat(valid).isFalse();
    }

    @Test
    void verify_wrongSignatureLength_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        boolean valid = SignatureVerifier.verify(
                signer.getPublicKey(),
                digest,
                new byte[63]); // Wrong length

        assertThat(valid).isFalse();
    }

    @Test
    void verify_nullDigest_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        boolean valid = SignatureVerifier.verify(
                signer.getPublicKey(),
                null,
                new byte[65]);

        assertThat(valid).isFalse();
    }

    @Test
    void verify_nullSignature_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        boolean valid = SignatureVerifier.verify(
                signer.getPublicKey(),
                digest,
                null);

        assertThat(valid).isFalse();
    }

    @Test
    void verify_nullPublicKey_shouldReturnFalse() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult signResult = signer.sign(digest);

        boolean valid = SignatureVerifier.verify(
                null,
                digest,
                signResult.getSignature());

        assertThat(valid).isFalse();
    }

    @Test
    void parsePublicKeyHex_validKey_shouldWork() {
        byte[] publicKey = SignatureVerifier.parsePublicKeyHex(PUBLIC_KEY_HEX);

        assertThat(publicKey).hasSize(65);
        assertThat(publicKey[0]).isEqualTo((byte) 0x04);
    }

    @Test
    void parsePublicKeyHex_with0xPrefix_shouldWork() {
        byte[] publicKey = SignatureVerifier.parsePublicKeyHex("0x" + PUBLIC_KEY_HEX);

        assertThat(publicKey).hasSize(65);
    }

    @Test
    void parsePublicKeyHex_uppercase_shouldWork() {
        byte[] publicKey = SignatureVerifier.parsePublicKeyHex(PUBLIC_KEY_HEX.toUpperCase());

        assertThat(publicKey).hasSize(65);
    }

    @Test
    void parsePublicKeyHex_null_shouldThrow() {
        assertThatThrownBy(() -> SignatureVerifier.parsePublicKeyHex(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePublicKeyHex_wrongLength_shouldThrow() {
        assertThatThrownBy(() -> SignatureVerifier.parsePublicKeyHex("abcd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicKeysEqual_sameKey_shouldReturnTrue() {
        byte[] key1 = hexToBytes(PUBLIC_KEY_HEX);
        byte[] key2 = hexToBytes(PUBLIC_KEY_HEX);

        assertThat(SignatureVerifier.publicKeysEqual(key1, key2)).isTrue();
    }

    @Test
    void publicKeysEqual_differentKeys_shouldReturnFalse() {
        Signer signer1 = Signer.fromHex(PRIVATE_KEY_HEX);
        Signer signer2 = Signer.fromHex("0000000000000000000000000000000000000000000000000000000000000001");

        assertThat(SignatureVerifier.publicKeysEqual(
                signer1.getPublicKey(),
                signer2.getPublicKey())).isFalse();
    }

    @Test
    void publicKeysEqual_nullFirst_shouldReturnFalse() {
        byte[] key = hexToBytes(PUBLIC_KEY_HEX);
        assertThat(SignatureVerifier.publicKeysEqual(null, key)).isFalse();
    }

    @Test
    void publicKeysEqual_nullSecond_shouldReturnFalse() {
        byte[] key = hexToBytes(PUBLIC_KEY_HEX);
        assertThat(SignatureVerifier.publicKeysEqual(key, null)).isFalse();
    }

    // ==================== BC ECCurve.decodePoint exception path ====================
    // SignatureVerifier wraps decodePoint in try/catch(IllegalArgumentException);
    // these tests pass 65-byte buffers that BC must reject so the catch returns false.

    @Test
    void verify_offCurvePublicKey_shouldReturnFalse() {
        // 0x04 prefix with x=1, y=1 - not on secp256k1 (y^2 = x^3 + 7 mod p).
        byte[] offCurveKey = new byte[65];
        offCurveKey[0] = 0x04;
        offCurveKey[32] = 0x01; // x = 1
        offCurveKey[64] = 0x01; // y = 1

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult sig = signer.sign(digest);

        assertThat(SignatureVerifier.verify(offCurveKey, digest, sig.getSignature()))
                .isFalse();
    }

    @Test
    void verify_invalidPublicKeyPrefix_shouldReturnFalse() {
        // Valid x,y but with prefix 0x05 - BC rejects unknown encoding bytes.
        byte[] validKey = hexToBytes(PUBLIC_KEY_HEX);
        byte[] invalidPrefixKey = Arrays.copyOf(validKey, validKey.length);
        invalidPrefixKey[0] = 0x05;

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult sig = signer.sign(digest);

        assertThat(SignatureVerifier.verify(invalidPrefixKey, digest, sig.getSignature()))
                .isFalse();
    }

    @Test
    void verify_allZeroPublicKey_shouldReturnFalse() {
        byte[] zeroKey = new byte[65];

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult sig = signer.sign(digest);

        assertThat(SignatureVerifier.verify(zeroKey, digest, sig.getSignature()))
                .isFalse();
    }

    // ==================== BC ECDSASigner range-check coverage ====================
    // ECDSASigner.verifySignature must reject r/s = 0 and r/s >= n per the ECDSA spec.

    @Test
    void verify_zeroR_shouldReturnFalse() {
        byte[] sigZeroR = new byte[65];
        sigZeroR[63] = 0x01; // s = 1, r = 0

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        assertThat(SignatureVerifier.verify(signer.getPublicKey(), digest, sigZeroR))
                .isFalse();
    }

    @Test
    void verify_zeroS_shouldReturnFalse() {
        byte[] sigZeroS = new byte[65];
        sigZeroS[31] = 0x01; // r = 1, s = 0

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        assertThat(SignatureVerifier.verify(signer.getPublicKey(), digest, sigZeroS))
                .isFalse();
    }

    @Test
    void verify_rEqualToCurveOrder_shouldReturnFalse() {
        byte[] nBytes = hexToBytes("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141");
        byte[] sig = new byte[65];
        System.arraycopy(nBytes, 0, sig, 0, 32); // r = n
        sig[63] = 0x01; // s = 1

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        assertThat(SignatureVerifier.verify(signer.getPublicKey(), digest, sig))
                .isFalse();
    }

    @Test
    void verify_sEqualToCurveOrder_shouldReturnFalse() {
        byte[] nBytes = hexToBytes("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141");
        byte[] sig = new byte[65];
        sig[31] = 0x01; // r = 1
        System.arraycopy(nBytes, 0, sig, 32, 32); // s = n

        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        assertThat(SignatureVerifier.verify(signer.getPublicKey(), digest, sig))
                .isFalse();
    }

    // ==================== High-s signature acceptance (BC behavior lock-in) ====================
    // BC's ECDSASigner.verifySignature accepts both low-s and high-s forms (s and n - s
    // are mathematically equivalent ECDSA signatures). Signer.sign normalizes to low-s,
    // but the verifier must still accept externally-produced high-s signatures.

    @Test
    void verify_acceptsHighSSignature() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);
        SignResult result = signer.sign(digest);

        BigInteger n = new BigInteger(
                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
        BigInteger lowS = new BigInteger(1, result.getS());
        BigInteger highS = n.subtract(lowS);

        byte[] highSSig = new byte[65];
        System.arraycopy(result.getR(), 0, highSSig, 0, 32);
        System.arraycopy(bigIntegerTo32Bytes(highS), 0, highSSig, 32, 32);
        // v left at 0 (verify ignores it)

        assertThat(SignatureVerifier.verify(signer.getPublicKey(), digest, highSSig))
                .isTrue();
    }

    private static byte[] bigIntegerTo32Bytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) return raw;
        if (raw.length > 32) {
            return Arrays.copyOfRange(raw, raw.length - 32, raw.length);
        }
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
        return padded;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
