using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Result of a signing operation, containing the signature and public key.
/// </summary>
public sealed class SignResult
{
    private const int SignatureLength = 65;
    private const int PublicKeyLength = 65;

    /// <summary>
    /// The 65-byte Ethereum-style signature: r[32] + s[32] + v[1].
    /// </summary>
    public byte[] Signature { get; }

    /// <summary>
    /// The 65-byte uncompressed public key: 0x04 + x[32] + y[32].
    /// </summary>
    public byte[] PublicKey { get; }

    public SignResult(byte[] signature, byte[] publicKey)
    {
        if (signature is null || signature.Length != SignatureLength)
            throw new ArgumentException("signature must be 65 bytes");
        if (publicKey is null || publicKey.Length != PublicKeyLength)
            throw new ArgumentException("publicKey must be 65 bytes");

        Signature = (byte[])signature.Clone();
        PublicKey = (byte[])publicKey.Clone();
    }

    /// <summary>Returns the signature as hex with 0x prefix.</summary>
    public string SignatureHex => "0x" + HexUtils.BytesToHex(Signature);

    /// <summary>Returns the public key as hex with 0x prefix.</summary>
    public string PublicKeyHex => "0x" + HexUtils.BytesToHex(PublicKey);
}
