package network.t0.sdk.crypto;

import network.t0.sdk.common.HexUtils;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ECDSA signer using secp256k1 curve, producing Ethereum-style signatures.
 *
 * <p>The signature format is 65 bytes: r[32] + s[32] + v[1], where:
 * <ul>
 *   <li>r: the R component of the ECDSA signature (32 bytes, big-endian)</li>
 *   <li>s: the S component of the ECDSA signature (32 bytes, big-endian)</li>
 *   <li>v: the recovery ID (0 or 1)</li>
 * </ul>
 *
 * <p><b>Deterministic Signing:</b> This class uses RFC 6979 deterministic nonce generation
 * with HMAC-SHA256. The same private key and message will always produce the same signature.
 *
 * <p><b>Thread Safety:</b> Instances of this class are thread-safe. The {@link #sign(byte[])}
 * method can be called concurrently from multiple threads.
 */
public final class Signer {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters DOMAIN_PARAMS = new ECDomainParameters(
            CURVE_PARAMS.getCurve(),
            CURVE_PARAMS.getG(),
            CURVE_PARAMS.getN(),
            CURVE_PARAMS.getH()
    );

    private static final int PRIVATE_KEY_LENGTH = 32;
    private static final int PRIVATE_KEY_HEX_LENGTH = 64;

    private final BigInteger privateKey;
    private final byte[] publicKey;
    private final ECPrivateKeyParameters privateKeyParams;

    private Signer(BigInteger privateKey, byte[] publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.privateKeyParams = new ECPrivateKeyParameters(privateKey, DOMAIN_PARAMS);
    }

    /**
     * Creates a new Signer from a hex-encoded private key.
     *
     * @param hexPrivateKey the private key in hex format (with or without 0x prefix)
     * @return a new Signer instance
     * @throws IllegalArgumentException if the key is invalid
     */
    public static Signer fromHex(String hexPrivateKey) {
        if (hexPrivateKey == null || hexPrivateKey.isEmpty()) {
            throw new IllegalArgumentException("private key must not be null or empty");
        }

        String cleanHex = HexUtils.stripHexPrefix(hexPrivateKey.toLowerCase());

        if (cleanHex.length() != PRIVATE_KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("private key must be 32 bytes (64 hex characters)");
        }

        try {
            byte[] privateKeyBytes = HexUtils.hexToBytes(cleanHex);
            BigInteger privateKeyInt = new BigInteger(1, privateKeyBytes);
            validatePrivateKeyRange(privateKeyInt);
            byte[] publicKey = derivePublicKey(privateKeyInt);
            return new Signer(privateKeyInt, publicKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid hex encoding: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new Signer from raw private key bytes.
     *
     * @param privateKeyBytes the 32-byte private key
     * @return a new Signer instance
     * @throws IllegalArgumentException if the key is invalid
     */
    public static Signer fromBytes(byte[] privateKeyBytes) {
        if (privateKeyBytes == null || privateKeyBytes.length != PRIVATE_KEY_LENGTH) {
            throw new IllegalArgumentException("private key must be 32 bytes");
        }

        BigInteger privateKeyInt = new BigInteger(1, privateKeyBytes);
        validatePrivateKeyRange(privateKeyInt);
        byte[] publicKey = derivePublicKey(privateKeyInt);
        return new Signer(privateKeyInt, publicKey);
    }

    /**
     * Signs a 32-byte digest (hash) and returns the signature with public key.
     *
     * <p>This method uses RFC 6979 deterministic nonce generation, ensuring that
     * the same digest always produces the same signature.
     *
     * @param digest the 32-byte hash to sign
     * @return SignResult containing signature and public key
     * @throws IllegalArgumentException if digest is not 32 bytes
     */
    public SignResult sign(byte[] digest) {
        if (digest == null || digest.length != PRIVATE_KEY_LENGTH) {
            throw new IllegalArgumentException("digest must be 32 bytes");
        }

        // Use RFC 6979 deterministic k-value generation with HMAC-SHA256
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, privateKeyParams);

        BigInteger[] components = signer.generateSignature(digest);
        BigInteger r = components[0];
        BigInteger s = components[1];

        // Ensure s is in the lower half of the curve order (canonical signature)
        BigInteger halfN = DOMAIN_PARAMS.getN().shiftRight(1);
        if (s.compareTo(halfN) > 0) {
            s = DOMAIN_PARAMS.getN().subtract(s);
        }

        // Calculate recovery ID (v)
        int recId = calculateRecoveryId(digest, r, s);

        // Build 65-byte signature: r[32] + s[32] + v[1]
        byte[] signature = new byte[65];
        byte[] rBytes = bigIntegerToBytes(r, PRIVATE_KEY_LENGTH);
        byte[] sBytes = bigIntegerToBytes(s, PRIVATE_KEY_LENGTH);
        System.arraycopy(rBytes, 0, signature, 0, PRIVATE_KEY_LENGTH);
        System.arraycopy(sBytes, 0, signature, PRIVATE_KEY_LENGTH, PRIVATE_KEY_LENGTH);
        signature[64] = (byte) recId;

        return new SignResult(signature, publicKey);
    }

    /**
     * Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).
     *
     * @return copy of the public key bytes
     */
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }

    /**
     * Returns the public key as hex string (without 0x prefix).
     *
     * @return hex-encoded public key
     */
    public String getPublicKeyHex() {
        return HexUtils.bytesToHex(publicKey);
    }

    /**
     * Returns the public key as hex string with 0x prefix.
     *
     * @return hex-encoded public key with 0x prefix
     */
    public String getPublicKeyHexPrefixed() {
        return "0x" + HexUtils.bytesToHex(publicKey);
    }

    /**
     * Validates that the private key is within the valid secp256k1 range [1, n-1].
     *
     * @param privateKey the private key to validate
     * @throws IllegalArgumentException if the key is outside the valid range
     */
    private static void validatePrivateKeyRange(BigInteger privateKey) {
        BigInteger n = DOMAIN_PARAMS.getN();
        if (privateKey.compareTo(BigInteger.ONE) < 0 || privateKey.compareTo(n) >= 0) {
            throw new IllegalArgumentException("private key must be in range [1, n-1]");
        }
    }

    private static byte[] derivePublicKey(BigInteger privateKey) {
        ECPoint point = new FixedPointCombMultiplier().multiply(DOMAIN_PARAMS.getG(), privateKey);
        return point.getEncoded(false); // uncompressed format: 0x04 + x + y
    }

    private int calculateRecoveryId(byte[] digest, BigInteger r, BigInteger s) {
        // Try recovery IDs 0 and 1
        for (int recId = 0; recId < 2; recId++) {
            byte[] recoveredKey = recoverPublicKey(digest, r, s, recId);
            if (recoveredKey != null && Arrays.equals(recoveredKey, publicKey)) {
                return recId;
            }
        }

        // This should never happen with a valid signature
        throw new IllegalStateException("Could not determine recovery ID");
    }

    private byte[] recoverPublicKey(byte[] digest, BigInteger r, BigInteger s, int recId) {
        BigInteger n = DOMAIN_PARAMS.getN();
        BigInteger x = r;

        if (recId >= 2) {
            x = x.add(n);
        }

        // Check if x is valid (within field)
        if (x.compareTo(CURVE_PARAMS.getCurve().getField().getCharacteristic()) >= 0) {
            return null;
        }

        // Decompress the point
        ECPoint R;
        try {
            byte[] encodedPoint = new byte[33];
            encodedPoint[0] = (byte) (0x02 | (recId & 1));
            byte[] xBytes = bigIntegerToBytes(x, PRIVATE_KEY_LENGTH);
            System.arraycopy(xBytes, 0, encodedPoint, 1, PRIVATE_KEY_LENGTH);
            R = DOMAIN_PARAMS.getCurve().decodePoint(encodedPoint);
        } catch (IllegalArgumentException e) {
            // Point not on curve - expected for wrong recId
            return null;
        }

        if (!R.multiply(n).isInfinity()) {
            return null;
        }

        // Calculate public key: Q = r^-1 * (s*R - e*G)
        BigInteger e = new BigInteger(1, digest);
        BigInteger rInv = r.modInverse(n);
        ECPoint Q = R.multiply(s).subtract(DOMAIN_PARAMS.getG().multiply(e)).multiply(rInv);

        return Q.getEncoded(false);
    }

    private static byte[] bigIntegerToBytes(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        } else if (bytes.length > length) {
            // Remove leading zeros
            return Arrays.copyOfRange(bytes, bytes.length - length, bytes.length);
        } else {
            // Pad with leading zeros
            byte[] result = new byte[length];
            System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
            return result;
        }
    }
}
