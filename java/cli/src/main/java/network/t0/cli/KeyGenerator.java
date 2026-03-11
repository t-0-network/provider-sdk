package network.t0.cli;

import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Signer;

import java.security.SecureRandom;

/**
 * Generates secp256k1 keypairs for provider initialization.
 */
public final class KeyGenerator {

    private static final int PRIVATE_KEY_LENGTH = 32;

    private KeyGenerator() {
        // Utility class
    }

    /**
     * A keypair containing private and public keys in hex format.
     */
    public record KeyPair(String privateKeyHex, String publicKeyHex) {}

    /**
     * Generates a new random secp256k1 keypair.
     *
     * @return a new keypair
     */
    public static KeyPair generate() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] privateKeyBytes = new byte[PRIVATE_KEY_LENGTH];
        secureRandom.nextBytes(privateKeyBytes);

        // Use SDK's Signer to derive public key
        Signer signer = Signer.fromBytes(privateKeyBytes);

        String privateKeyHex = HexUtils.bytesToHex(privateKeyBytes);
        String publicKeyHex = signer.getPublicKeyHex();

        return new KeyPair(privateKeyHex, publicKeyHex);
    }
}
