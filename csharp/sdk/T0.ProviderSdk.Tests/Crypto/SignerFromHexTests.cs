using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Crypto;

public class SignerFromHexTests
{
    private const string Key = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private const string OrderN = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";

    [Theory]
    [InlineData("0x" + Key)]
    [InlineData("0X" + Key)]
    [InlineData(Key)]
    public void ValidKey_IsAccepted(string key)
    {
        Assert.Equal(PublicKey, Signer.FromHex(key).GetPublicKeyHexPrefixed());
    }

    [Theory]
    [InlineData("")]
    [InlineData(null)]
    public void EmptyKey_IsRefused(string? key)
    {
        var ex = Assert.Throws<ArgumentException>(() => Signer.FromHex(key!));
        Assert.Equal("private key must not be null or empty", ex.Message);
    }

    [Theory]
    [InlineData(62)] // too short
    [InlineData(66)] // too long
    public void KeyOfAnotherLength_IsRefused(int length)
    {
        var key = string.Concat(Enumerable.Repeat(Key, 2))[..length];

        var ex = Assert.Throws<ArgumentException>(() => Signer.FromHex(key));
        Assert.Equal("private key must be 32 bytes (64 hex characters)", ex.Message);
    }

    [Theory]
    [InlineData(Key62 + "  ")] // whitespace
    [InlineData("zz" + Key62)] // not hex
    public void KeyThatIsNotHex_IsRefused(string key)
    {
        var ex = Assert.Throws<ArgumentException>(() => Signer.FromHex(key));
        Assert.Equal("private key must be 32 bytes (64 hex characters)", ex.Message);
    }

    [Theory]
    [InlineData("0000000000000000000000000000000000000000000000000000000000000000")]
    [InlineData(OrderN)]
    public void KeyOutOfRange_IsRefused(string key)
    {
        var ex = Assert.Throws<ArgumentException>(() => Signer.FromHex(key));
        Assert.Equal("private key must be in range [1, n-1]", ex.Message);
    }

    private const string Key62 = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9b";
}
