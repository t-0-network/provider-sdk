package network.t0.sdk.crypto;

import network.t0.sdk.common.Messages;
import network.t0.sdk.common.HexUtils;

/**
 * Signs a request: takes the 32-byte Keccak-256 digest and returns the signature (64 bytes r‖s, or
 * 65 bytes r‖s‖v) and the 65-byte uncompressed public key it verifies against. A lambda is one:
 * <pre>{@code
 * DigestSigner signer = digest -> new SignResult(hsm.sign(digest), hsm.publicKey());
 * }</pre>
 * {@link Signer#fromHex(String)} gives one for a private key held in memory.
 *
 * <p>{@link SignResult} refuses a signature that is not 64 or 65 bytes, then a public key that is not
 * 65 bytes uncompressed, so a client checks the output before it sends anything; a signer that throws
 * fails the call with INTERNAL "signing the request failed: &lt;message&gt;", and nothing is sent.
 * {@link #sign(byte[])} runs while the call's lock is held, so a signer of your own must return quickly
 * and must not block on network I/O. It must be thread-safe: calls on one client may sign at the same time.
 */
@FunctionalInterface
public interface DigestSigner {

    /**
     * Signs a 32-byte digest.
     *
     * @param digest the 32-byte digest to sign
     * @return the signature (64 or 65 bytes) and the 65-byte uncompressed public key
     */
    SignResult sign(byte[] digest);

    /**
     * Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]). {@link Signer} returns the
     * key it holds. The SDK never calls it: a client takes the key from each {@link SignResult}.
     *
     * @return the public key bytes
     * @throws UnsupportedOperationException for a signer that does not override it, such as a lambda
     */
    default byte[] getPublicKey() {
        throw new UnsupportedOperationException(Messages.SIGNER_PUBLIC_KEY_UNSUPPORTED);
    }

    /**
     * Returns the public key as hex string (without 0x prefix).
     *
     * @return hex-encoded public key
     */
    default String getPublicKeyHex() {
        return HexUtils.bytesToHex(getPublicKey());
    }

    /**
     * Returns the public key as hex string with 0x prefix.
     *
     * @return hex-encoded public key with 0x prefix
     */
    default String getPublicKeyHexPrefixed() {
        return "0x" + getPublicKeyHex();
    }
}
