namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Default implementation of <see cref="ISignatureVerifier"/> that delegates
/// to the static <see cref="SignatureVerifier"/> methods.
/// </summary>
public sealed class DefaultSignatureVerifier : ISignatureVerifier
{
    public bool Verify(byte[] publicKey, byte[] digest, byte[] signature)
        => SignatureVerifier.Verify(publicKey, digest, signature);
}
