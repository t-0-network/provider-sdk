using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Crypto;

/// <summary>
/// Tests the contract of Signer, of the SignFn it converts to, and of SignResult.
/// </summary>
public class SignerInterfaceTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    [Fact]
    public void Signer_ConvertsToASignFn_ThatSignsWithItsKey()
    {
        var signer = Signer.FromHex(TestPrivateKey);
        SignFn sign = signer;
        var digest = Keccak256.Hash("test data"u8.ToArray());

        var (signature, publicKey) = sign(digest);

        Assert.Equal(signer.Sign(digest).Signature, signature);
        Assert.Equal(signer.GetPublicKey(), publicKey);
    }

    [Fact]
    public void NullSigner_ConvertsToNull_AndTheClientRefusesIt()
    {
        Signer? signer = null;
        SignFn? sign = signer;

        Assert.Null(sign);
        var ex = Assert.Throws<ArgumentNullException>(
            () => T0.ProviderSdk.Network.NetworkClient.CreateNetworkServiceClient("http://localhost:1", signer!));
        Assert.Equal("signer must not be null", ex.Message);
    }

    [Fact]
    public void Sign_ReturnsValidSignResult()
    {
        var signer = Signer.FromHex(TestPrivateKey);
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
        var signer = Signer.FromHex(TestPrivateKey);
        var digest = Keccak256.Hash("verify me"u8.ToArray());

        var result = signer.Sign(digest);

        Assert.True(SignatureVerifier.Verify(signer.GetPublicKey(), digest, result.Signature));
    }

    [Fact]
    public void GetPublicKey_Returns65ByteUncompressedKey()
    {
        var signer = Signer.FromHex(TestPrivateKey);

        var pubKey = signer.GetPublicKey();

        Assert.Equal(65, pubKey.Length);
        Assert.Equal(0x04, pubKey[0]); // Uncompressed prefix
    }

    [Fact]
    public void GetPublicKeyHex_ReturnsLowercaseWithoutPrefix()
    {
        var signer = Signer.FromHex(TestPrivateKey);

        var hex = signer.GetPublicKeyHex();

        Assert.Equal(130, hex.Length); // 65 bytes = 130 hex chars
        Assert.DoesNotContain("0x", hex);
        Assert.Equal(hex, hex.ToLowerInvariant());
    }

    [Fact]
    public void GetPublicKeyHexPrefixed_Returns0xPrefix()
    {
        var signer = Signer.FromHex(TestPrivateKey);

        var hex = signer.GetPublicKeyHexPrefixed();

        Assert.StartsWith("0x", hex);
        Assert.Equal(132, hex.Length); // "0x" + 130 hex chars
    }

    [Fact]
    public void Sign_InvalidDigestLength_Throws()
    {
        var signer = Signer.FromHex(TestPrivateKey);

        Assert.Throws<ArgumentException>(() => signer.Sign(new byte[16]));
        Assert.Throws<ArgumentException>(() => signer.Sign(new byte[64]));
    }

    // Rule V5: r‖s, or r‖s‖v; v is not checked.
    [Theory]
    [InlineData(64, 0)]
    [InlineData(65, 0)]
    [InlineData(65, 1)]
    [InlineData(65, 27)]
    public void SignResult_AcceptsA64Or65ByteSignature(int length, byte last)
    {
        var signature = new byte[length];
        signature[^1] = last;
        var publicKey = Signer.FromHex(TestPrivateKey).GetPublicKey();

        Assert.Equal(signature, new SignResult(signature, publicKey).Signature);
    }

    [Theory]
    [InlineData(63, 0)]
    [InlineData(66, 0)]
    public void SignResult_RefusesOtherSignatures(int length, byte last)
    {
        var signature = new byte[length];
        signature[^1] = last;
        var publicKey = Signer.FromHex(TestPrivateKey).GetPublicKey();

        var ex = Assert.Throws<ArgumentException>(() => new SignResult(signature, publicKey));
        Assert.Equal("signature must be 64 or 65 bytes", ex.Message);
    }

    [Fact]
    public void SignResult_RefusesAPublicKeyThatIsNot65BytesUncompressed()
    {
        var uncompressed = Signer.FromHex(TestPrivateKey).GetPublicKey();
        byte[] compressed = [(byte)(2 + (uncompressed[^1] & 1)), .. uncompressed[1..33]];
        byte[] hybrid = [6, .. uncompressed[1..]];

        foreach (var publicKey in new[] { compressed, hybrid })
        {
            var ex = Assert.Throws<ArgumentException>(() => new SignResult(new byte[65], publicKey));
            Assert.Equal("public key must be 65 bytes, uncompressed", ex.Message);
        }
    }

    [Fact]
    public void GetPublicKey_ReturnsDifferentArrayInstance()
    {
        var signer = Signer.FromHex(TestPrivateKey);

        var key1 = signer.GetPublicKey();
        var key2 = signer.GetPublicKey();

        Assert.Equal(key1, key2);
        Assert.NotSame(key1, key2); // Defensive copy
    }
}
