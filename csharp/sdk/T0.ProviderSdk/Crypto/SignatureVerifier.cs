using Org.BouncyCastle.Crypto.EC;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Crypto.Signers;
using Org.BouncyCastle.Math;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// ECDSA signature verifier for secp256k1 curve.
/// Accepts 64-byte (r+s) or 65-byte (r+s+v) Ethereum-style signatures.
/// Thread-safe.
/// </summary>
public static class SignatureVerifier
{
    private static readonly Org.BouncyCastle.Asn1.X9.X9ECParameters CurveParams =
        CustomNamedCurves.GetByName("secp256k1");
    private static readonly ECDomainParameters DomainParams = new(
        CurveParams.Curve,
        CurveParams.G,
        CurveParams.N,
        CurveParams.H
    );

    private const int PublicKeyLength = 65;
    private const int DigestLength = 32;
    private const int RsLength = 32;

    /// <summary>
    /// Verifies an ECDSA signature against a public key.
    /// </summary>
    /// <param name="publicKey">65-byte uncompressed public key (0x04 + x[32] + y[32]).</param>
    /// <param name="digest">32-byte hash that was signed.</param>
    /// <param name="signature">64 or 65 byte signature (r[32] + s[32] [+ v[1]]).</param>
    /// <returns>True if the signature is valid.</returns>
    public static bool Verify(byte[] publicKey, byte[] digest, byte[] signature)
    {
        if (digest is null || digest.Length != DigestLength)
            return false;
        if (signature is null || (signature.Length != 64 && signature.Length != 65))
            return false;
        if (publicKey is null || publicKey.Length != PublicKeyLength)
            return false;

        try
        {
            var pubKeyPoint = DomainParams.Curve.DecodePoint(publicKey);
            var pubKeyParams = new ECPublicKeyParameters(pubKeyPoint, DomainParams);

            var r = new BigInteger(1, signature[..RsLength]);
            var s = new BigInteger(1, signature[RsLength..64]);

            var signer = new ECDsaSigner();
            signer.Init(false, pubKeyParams);
            return signer.VerifySignature(digest, r, s);
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Parses a hex-encoded public key.
    /// </summary>
    public static byte[] ParsePublicKeyHex(string hexPublicKey)
    {
        if (string.IsNullOrEmpty(hexPublicKey))
            throw new ArgumentException("public key must not be null or empty");

        var cleanHex = HexUtils.StripHexPrefix(hexPublicKey.ToLowerInvariant());
        if (cleanHex.Length != PublicKeyLength * 2)
            throw new ArgumentException("public key must be 65 bytes (130 hex characters)");

        return HexUtils.HexToBytes(cleanHex);
    }
}
