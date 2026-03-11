package network.t0.sdk.crypto;

import network.t0.sdk.common.HexUtils;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ECDSA signature verifier for secp256k1 curve.
 *
 * <p>Verifies Ethereum-style signatures (65 bytes: r[32] + s[32] + v[1]) or
 * standard 64-byte signatures (r[32] + s[32]).
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All methods are stateless and can be
 * called concurrently from multiple threads without synchronization.
 */
public final class SignatureVerifier {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters DOMAIN_PARAMS = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH()
    );

    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final int PUBLIC_KEY_HEX_LENGTH = 130;
    private static final int DIGEST_LENGTH = 32;
    private static final int SIGNATURE_LENGTH_64 = 64;
    private static final int SIGNATURE_LENGTH_65 = 65;
    private static final int R_S_LENGTH = 32;

    private SignatureVerifier() {
        // Utility class
    }

    /**
     * Verifies an ECDSA signature against a public key.
     *
     * @param publicKey the 65-byte uncompressed public key (0x04 + x[32] + y[32])
     * @param digest    the 32-byte hash that was signed
     * @param signature the 64-byte or 65-byte signature (r[32] + s[32] [+ v[1]])
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verify(byte[] publicKey, byte[] digest, byte[] signature) {
        if (digest == null || digest.length != DIGEST_LENGTH) {
            return false;
        }

        if (signature == null || (signature.length != SIGNATURE_LENGTH_64 && signature.length != SIGNATURE_LENGTH_65)) {
            return false;
        }

        if (publicKey == null || publicKey.length != PUBLIC_KEY_LENGTH) {
            return false;
        }

        try {
            // Parse public key
            ECPoint pubKeyPoint = DOMAIN_PARAMS.getCurve().decodePoint(publicKey);
            ECPublicKeyParameters pubKeyParams = new ECPublicKeyParameters(pubKeyPoint, DOMAIN_PARAMS);

            // Extract R and S from signature (ignore V at position 64)
            byte[] rBytes = Arrays.copyOfRange(signature, 0, R_S_LENGTH);
            byte[] sBytes = Arrays.copyOfRange(signature, R_S_LENGTH, SIGNATURE_LENGTH_64);

            BigInteger r = new BigInteger(1, rBytes);
            BigInteger s = new BigInteger(1, sBytes);

            // Verify signature
            ECDSASigner signer = new ECDSASigner();
            signer.init(false, pubKeyParams);
            return signer.verifySignature(digest, r, s);

        } catch (IllegalArgumentException | ArithmeticException e) {
            // Expected for invalid signatures: point not on curve, invalid coordinates, etc.
            return false;
        }
    }

    /**
     * Parses a hex-encoded public key.
     *
     * @param hexPublicKey the public key in hex format (with or without 0x prefix)
     * @return the 65-byte uncompressed public key
     * @throws IllegalArgumentException if the key is invalid
     */
    public static byte[] parsePublicKeyHex(String hexPublicKey) {
        if (hexPublicKey == null || hexPublicKey.isEmpty()) {
            throw new IllegalArgumentException("public key must not be null or empty");
        }

        String cleanHex = HexUtils.stripHexPrefix(hexPublicKey.toLowerCase());

        if (cleanHex.length() != PUBLIC_KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("public key must be 65 bytes (130 hex characters)");
        }

        return HexUtils.hexToBytes(cleanHex);
    }

    /**
     * Checks if two public keys are equal.
     *
     * @param publicKey1 first public key
     * @param publicKey2 second public key
     * @return true if equal, false otherwise
     */
    public static boolean publicKeysEqual(byte[] publicKey1, byte[] publicKey2) {
        if (publicKey1 == null || publicKey2 == null) {
            return false;
        }
        return Arrays.equals(publicKey1, publicKey2);
    }
}
