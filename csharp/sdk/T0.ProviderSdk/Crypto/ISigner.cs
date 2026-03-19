namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Interface for ECDSA signing operations.
/// Enables mocking in tests and adheres to Dependency Inversion Principle.
/// </summary>
public interface ISigner
{
    /// <summary>
    /// Signs a 32-byte digest and returns the signature with public key.
    /// </summary>
    SignResult Sign(byte[] digest);

    /// <summary>
    /// Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).
    /// </summary>
    byte[] GetPublicKey();

    /// <summary>
    /// Returns the public key as hex string (without 0x prefix).
    /// </summary>
    string GetPublicKeyHex();

    /// <summary>
    /// Returns the public key as hex string with 0x prefix.
    /// </summary>
    string GetPublicKeyHexPrefixed();
}
