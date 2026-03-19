using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Crypto;

/// <summary>
/// Tests that ISigner contract is correctly fulfilled by Signer.
/// Tests focus on the interface contract, not implementation details.
/// </summary>
public class SignerInterfaceTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public void Signer_ImplementsISigner()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);
        Assert.NotNull(signer);
    }

    [Fact]
    public void Sign_ReturnsValidSignResult()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("test data"u8.ToArray());

        var result = signer.Sign(digest);

        Assert.Equal(65, result.Signature.Length);
        Assert.Equal(65, result.PublicKey.Length);
        Assert.StartsWith("0x", result.SignatureHex);
        Assert.StartsWith("0x", result.PublicKeyHex);
    }

    [Fact]
    public void Sign_ProducesVerifiableSignature()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("verify me"u8.ToArray());

        var result = signer.Sign(digest);

        Assert.True(SignatureVerifier.Verify(signer.GetPublicKey(), digest, result.Signature));
    }

    [Fact]
    public void GetPublicKey_Returns65ByteUncompressedKey()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);

        var pubKey = signer.GetPublicKey();

        Assert.Equal(65, pubKey.Length);
        Assert.Equal(0x04, pubKey[0]); // Uncompressed prefix
    }

    [Fact]
    public void GetPublicKeyHex_ReturnsLowercaseWithoutPrefix()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);

        var hex = signer.GetPublicKeyHex();

        Assert.Equal(130, hex.Length); // 65 bytes = 130 hex chars
        Assert.DoesNotContain("0x", hex);
        Assert.Equal(hex, hex.ToLowerInvariant());
    }

    [Fact]
    public void GetPublicKeyHexPrefixed_Returns0xPrefix()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);

        var hex = signer.GetPublicKeyHexPrefixed();

        Assert.StartsWith("0x", hex);
        Assert.Equal(132, hex.Length); // "0x" + 130 hex chars
    }

    [Fact]
    public void Sign_InvalidDigestLength_Throws()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);

        Assert.Throws<ArgumentException>(() => signer.Sign(new byte[16]));
        Assert.Throws<ArgumentException>(() => signer.Sign(new byte[64]));
    }

    [Fact]
    public void GetPublicKey_ReturnsDifferentArrayInstance()
    {
        ISigner signer = Signer.FromHex(TestPrivateKey);

        var key1 = signer.GetPublicKey();
        var key2 = signer.GetPublicKey();

        Assert.Equal(key1, key2);
        Assert.NotSame(key1, key2); // Defensive copy
    }
}
