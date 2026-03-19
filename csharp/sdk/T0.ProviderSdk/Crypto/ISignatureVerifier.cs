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
    /// <param name="publicKey">Uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).</param>
    /// <param name="digest">32-byte hash to verify against.</param>
    /// <param name="signature">Signature bytes (64 or 65 bytes).</param>
    /// <returns>True if the signature is valid.</returns>
    bool Verify(byte[] publicKey, byte[] digest, byte[] signature);
}
