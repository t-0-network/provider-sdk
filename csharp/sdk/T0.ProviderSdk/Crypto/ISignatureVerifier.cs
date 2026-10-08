namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Interface for ECDSA signature verification.
/// Enables mocking in tests and adheres to Dependency Inversion Principle.
/// </summary>
public interface ISignatureVerifier
{
    /// <summary>
    /// Verifies an ECDSA signature against a public key and digest.
    /// </summary>
    /// <param name="publicKey">Public key: a 33-byte compressed (0x02/0x03 + x[32]) or 65-byte
    /// uncompressed (0x04 + x[32] + y[32]) point on secp256k1 (rule V2).</param>
    /// <param name="digest">32-byte hash to verify against.</param>
    /// <param name="signature">Signature bytes (64 or 65 bytes).</param>
    /// <returns>True if the signature is valid.</returns>
    bool Verify(byte[] publicKey, byte[] digest, byte[] signature);
}
