package network.t0.sdk.crypto;

import org.junit.jupiter.api.Test;

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
