using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Result of a signing operation, containing the signature and public key.
/// </summary>
public sealed class SignResult
{
    private const int PublicKeyLength = 65;

    /// <summary>
    /// The signature: r[32] + s[32], or the 65-byte Ethereum-style r[32] + s[32] + v[1].
    /// </summary>
    public byte[] Signature { get; }

    /// <summary>
    /// The 65-byte uncompressed public key: 0x04 + x[32] + y[32].
    /// </summary>
    public byte[] PublicKey { get; }

    /// <exception cref="ArgumentException">The signature is not 64 or 65 bytes, or the public key is
    /// not 65 bytes starting with 0x04.</exception>
    public SignResult(byte[] signature, byte[] publicKey)
    {
        if (signature is null || signature.Length is not (64 or 65))
            throw new ArgumentException(Messages.SignerSignatureInvalid);
        if (publicKey is null || publicKey.Length != PublicKeyLength || publicKey[0] != 0x04)
            throw new ArgumentException(Messages.SignerPublicKeyInvalid);

        Signature = (byte[])signature.Clone();
        PublicKey = (byte[])publicKey.Clone();
    }

    /// <summary>Returns the signature as hex with 0x prefix.</summary>
    public string SignatureHex => "0x" + HexUtils.BytesToHex(Signature);

    /// <summary>Returns the public key as hex with 0x prefix.</summary>
    public string PublicKeyHex => "0x" + HexUtils.BytesToHex(PublicKey);
}
