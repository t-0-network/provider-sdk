using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Crypto;

/// <summary>
/// Tests the ISignatureVerifier contract via DefaultSignatureVerifier.
/// </summary>
public class SignatureVerifierInterfaceTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public void DefaultSignatureVerifier_ImplementsInterface()
    {
        ISignatureVerifier verifier = new DefaultSignatureVerifier();
        Assert.NotNull(verifier);
    }

    [Fact]
    public void Verify_ValidSignature_ReturnsTrue()
    {
        ISignatureVerifier verifier = new DefaultSignatureVerifier();
        var signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("test"u8.ToArray());
        var result = signer.Sign(digest);

        Assert.True(verifier.Verify(signer.GetPublicKey(), digest, result.Signature));
    }

    [Fact]
    public void Verify_InvalidSignature_ReturnsFalse()
    {
        ISignatureVerifier verifier = new DefaultSignatureVerifier();
        var signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("test"u8.ToArray());
        var wrongDigest = Keccak256.Hash("wrong"u8.ToArray());
        var result = signer.Sign(wrongDigest);

        Assert.False(verifier.Verify(signer.GetPublicKey(), digest, result.Signature));
    }

    [Fact]
    public void Verify_64ByteSignature_ReturnsTrue()
    {
        ISignatureVerifier verifier = new DefaultSignatureVerifier();
        var signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("64 byte test"u8.ToArray());
        var result = signer.Sign(digest);
        var sig64 = result.Signature[..64]; // Strip recovery byte

        Assert.True(verifier.Verify(signer.GetPublicKey(), digest, sig64));
    }
}
