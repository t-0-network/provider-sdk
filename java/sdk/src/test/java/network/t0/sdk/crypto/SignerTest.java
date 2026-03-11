package network.t0.sdk.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Signer using secp256k1.
 */
class SignerTest {

    // Test vectors
    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String EXPECTED_PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private static final String MESSAGE = "please sign me!";
    private static final String MESSAGE_KECCAK_HASH = "46d5cbf7d8477720c337b94a2fe332fe54205914619e7e4889595c3c944b646b";

    @Test
    void fromHex_shouldDeriveCorrectPublicKey() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        assertThat(signer.getPublicKeyHex()).isEqualTo(EXPECTED_PUBLIC_KEY_HEX);
    }

    @Test
    void fromHex_with0xPrefix_shouldWork() {
        Signer signer = Signer.fromHex("0x" + PRIVATE_KEY_HEX);

        assertThat(signer.getPublicKeyHex()).isEqualTo(EXPECTED_PUBLIC_KEY_HEX);
    }

    @Test
    void fromHex_uppercase_shouldWork() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX.toUpperCase());

        assertThat(signer.getPublicKeyHex()).isEqualTo(EXPECTED_PUBLIC_KEY_HEX);
    }

    @Test
    void getPublicKey_shouldReturn65Bytes() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        assertThat(signer.getPublicKey()).hasSize(65);
        assertThat(signer.getPublicKey()[0]).isEqualTo((byte) 0x04); // uncompressed format prefix
    }

    @Test
    void getPublicKeyHexPrefixed_shouldHave0xPrefix() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        assertThat(signer.getPublicKeyHexPrefixed()).isEqualTo("0x" + EXPECTED_PUBLIC_KEY_HEX);
    }

    @Test
    void sign_shouldProduceValidSignature() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        SignResult result = signer.sign(digest);

        assertThat(result.getSignature()).hasSize(65);
        assertThat(result.getPublicKey()).isEqualTo(hexToBytes(EXPECTED_PUBLIC_KEY_HEX));
    }

    @Test
    void sign_shouldProduceVerifiableSignature() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        SignResult result = signer.sign(digest);

        // Verify the signature
        boolean valid = SignatureVerifier.verify(
                result.getPublicKey(),
                digest,
                result.getSignature());

        assertThat(valid).isTrue();
    }

    @Test
    void sign_isDeterministic() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);
        byte[] digest = hexToBytes(MESSAGE_KECCAK_HASH);

        SignResult result = signer.sign(digest);

        // RFC 6979 deterministic signing with HMAC-SHA256.
        // The same private key and digest will always produce the same signature.
        assertThat(result.getSignature()).hasSize(65);
        assertThat(result.getV()).isIn(0, 1); // Recovery ID should be 0 or 1

        // Verify determinism: signing the same digest should produce the same signature
        SignResult result2 = signer.sign(digest);
        assertThat(result2.getSignature()).isEqualTo(result.getSignature());
    }

    @Test
    void sign_requestBody_producesExpectedHash() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        // Simulating the request signing flow
        byte[] body = "test request body".getBytes();
        long timestampMs = 1706000000000L;

        // Encode timestamp as 8-byte little-endian
        byte[] timestampBytes = new byte[8];
        long temp = timestampMs;
        for (int i = 0; i < 8; i++) {
            timestampBytes[i] = (byte) (temp & 0xFF);
            temp >>= 8;
        }

        // Compute digest: Keccak256(body + timestampBytes)
        byte[] digest = Keccak256.hash(body, timestampBytes);

        // Expected hash
        assertThat(bytesToHex(digest))
                .isEqualTo("49a567a359bf25d9652b24acc5567bc38c93139467c8fcf798f059ada585697e");

        // Sign and verify
        SignResult result = signer.sign(digest);
        boolean valid = SignatureVerifier.verify(result.getPublicKey(), digest, result.getSignature());
        assertThat(valid).isTrue();
    }

    @Test
    void fromHex_nullKey_shouldThrow() {
        assertThatThrownBy(() -> Signer.fromHex(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("private key must not be null or empty");
    }

    @Test
    void fromHex_emptyKey_shouldThrow() {
        assertThatThrownBy(() -> Signer.fromHex(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("private key must not be null or empty");
    }

    @Test
    void fromHex_wrongLength_shouldThrow() {
        assertThatThrownBy(() -> Signer.fromHex("abcd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("private key must be 32 bytes (64 hex characters)");
    }

    @Test
    void fromHex_invalidHex_shouldThrow() {
        assertThatThrownBy(() -> Signer.fromHex("ZZZZ" + PRIVATE_KEY_HEX.substring(4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid hex encoding");
    }

    @Test
    void fromHex_zeroKey_shouldThrow() {
        // Private key of 0 is invalid for secp256k1
        String zeroKey = "0000000000000000000000000000000000000000000000000000000000000000";
        assertThatThrownBy(() -> Signer.fromHex(zeroKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private key must be in range [1, n-1]");
    }

    @Test
    void fromHex_keyEqualToCurveOrder_shouldThrow() {
        // secp256k1 curve order n = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
        // Key >= n is invalid
        String keyAtCurveOrder = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";
        assertThatThrownBy(() -> Signer.fromHex(keyAtCurveOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private key must be in range [1, n-1]");
    }

    @Test
    void fromHex_keyJustBelowCurveOrder_shouldSucceed() {
        // Key = n - 1 is valid (the largest valid private key)
        String keyJustBelowN = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140";
        Signer signer = Signer.fromHex(keyJustBelowN);
        assertThat(signer.getPublicKey()).hasSize(65);
    }

    @Test
    void sign_wrongDigestLength_shouldThrow() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        assertThatThrownBy(() -> signer.sign(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("digest must be 32 bytes");
    }

    @Test
    void sign_nullDigest_shouldThrow() {
        Signer signer = Signer.fromHex(PRIVATE_KEY_HEX);

        assertThatThrownBy(() -> signer.sign(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("digest must be 32 bytes");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
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
