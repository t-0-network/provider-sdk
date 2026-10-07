package network.t0.sdk.crypto;

import network.t0.sdk.common.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a signer may return (rule V5 for the signature, V2's uncompressed form for the key). */
class SignResultTest {

    private static final Signer SIGNER =
            Signer.fromHex("6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8");
    private static final byte[] SIGNATURE = SIGNER.sign(new byte[32]).getSignature();
    private static final byte[] PUBLIC_KEY = SIGNER.getPublicKey();

    @Test
    @DisplayName("A 64-byte r‖s signature is kept as it is, with no v")
    void rsSignature() {
        byte[] rs = Arrays.copyOf(SIGNATURE, 64);
        SignResult result = new SignResult(rs, PUBLIC_KEY);
        assertThat(result.getSignature()).isEqualTo(rs);
        assertThatThrownBy(result::getV)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(Messages.SIGNATURE_HAS_NO_V);
    }

    @Test
    @DisplayName("A 65-byte signature is kept as it is, whatever its last byte")
    void anyLastByte() {
        byte[] v27 = SIGNATURE.clone();
        v27[64] += 27;
        SignResult result = new SignResult(v27, PUBLIC_KEY);
        assertThat(result.getSignature()).isEqualTo(v27);
        assertThat(result.getV()).isEqualTo(v27[64] & 0xFF);
    }

    @Test
    @DisplayName("A signature that is not 64 or 65 bytes is refused, before the key is checked")
    void signatureLength() {
        byte[] compressed = Arrays.copyOf(PUBLIC_KEY, 33);
        compressed[0] = 0x02;
        for (byte[] signature : new byte[][] {null, new byte[0], new byte[63], new byte[66]}) {
            assertThatThrownBy(() -> new SignResult(signature, compressed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(Messages.SIGNER_SIGNATURE_INVALID)
                    .hasMessage("signature must be 64 or 65 bytes");
        }
    }

    @Test
    @DisplayName("A public key that is not 65 bytes uncompressed is refused")
    void publicKeyForm() {
        byte[] compressed = Arrays.copyOf(PUBLIC_KEY, 33);
        compressed[0] = 0x02;
        byte[] hybrid = PUBLIC_KEY.clone();
        hybrid[0] = 0x06;
        for (byte[] publicKey : new byte[][] {null, compressed, hybrid, new byte[64], new byte[66]}) {
            assertThatThrownBy(() -> new SignResult(SIGNATURE, publicKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(Messages.SIGNER_PUBLIC_KEY_INVALID)
                    .hasMessage("public key must be 65 bytes, uncompressed");
        }
    }

    @Test
    @DisplayName("A lambda is a DigestSigner; its key comes from what sign returns")
    void lambdaSigner() {
        DigestSigner lambda = SIGNER::sign;
        assertThat(lambda.sign(new byte[32])).isEqualTo(SIGNER.sign(new byte[32]));
        // Not by signing data of the SDK's choosing.
        assertThatThrownBy(lambda::getPublicKey)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("use the public key returned by sign");
        assertThat(SIGNER.getPublicKey()).isEqualTo(PUBLIC_KEY);
    }
}
