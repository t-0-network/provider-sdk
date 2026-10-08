using Org.BouncyCastle.Crypto.EC;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Crypto.Signers;
using Org.BouncyCastle.Math;
using T0.ProviderSdk.Provider;

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

    private const int DigestLength = 32;
    private const int RsLength = 32;

    /// <summary>
    /// Verifies an ECDSA signature against a public key.
    /// </summary>
    /// <param name="publicKey">The public key, parsed as the provider server parses the network key:
    /// a 33-byte compressed (0x02/0x03 + x[32]) or 65-byte uncompressed (0x04 + x[32] + y[32])
    /// point on secp256k1. Any other key, a hybrid one (0x06/0x07) among them, does not verify.</param>
    /// <param name="digest">32-byte hash that was signed.</param>
    /// <param name="signature">64 or 65 byte signature (r[32] + s[32] [+ v[1]]).</param>
    /// <returns>True if the signature is valid.</returns>
    public static bool Verify(byte[] publicKey, byte[] digest, byte[] signature)
    {
        if (digest is null || digest.Length != DigestLength)
            return false;
        if (signature is null || (signature.Length != 64 && signature.Length != 65))
            return false;
        if (publicKey is null)
            return false;

        try
        {
            // Rule V2, by the server's parser: a refused key throws and does not verify.
            var encoded = SignatureVerificationMiddleware.ParsePublicKeyBytes(publicKey);
            var pubKeyPoint = DomainParams.Curve.DecodePoint(encoded);
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
    /// Parses a hex-encoded public key (optional 0x or 0X prefix) by the rule the server applies
    /// to the network key and X-Public-Key: an encoded secp256k1 point, such as 33 bytes
    /// compressed (0x02/0x03) or 65 bytes uncompressed (0x04). Returns the 65-byte uncompressed encoding.
    /// </summary>
    /// <exception cref="FormatException">The value is null, empty or not hex.</exception>
    /// <exception cref="ArgumentException">The bytes are not a point on the curve.</exception>
    [Obsolete("Not used by the SDK; will be removed in a future release.")]
    public static byte[] ParsePublicKeyHex(string hexPublicKey) =>
        SignatureVerificationMiddleware.ParsePublicKey(hexPublicKey);
}
