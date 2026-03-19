using System.Security.Cryptography;
using Org.BouncyCastle.Asn1.X9;
using Org.BouncyCastle.Crypto.EC;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Math;

namespace T0.ProviderStarter;

public static class KeyGenerator
{
    private static readonly X9ECParameters Curve = CustomNamedCurves.GetByName("secp256k1");
    private static readonly ECDomainParameters Domain = new(Curve.Curve, Curve.G, Curve.N, Curve.H);

    /// <summary>
    /// Generates a new secp256k1 keypair.
    /// Returns (privateKeyHex, publicKeyHex) without 0x prefix.
    /// Public key is uncompressed (65 bytes, 0x04 prefix).
    /// </summary>
    public static (string PrivateKeyHex, string PublicKeyHex) Generate()
    {
        var privateKeyBytes = new byte[32];
        BigInteger privateKeyInt;

        do
        {
            RandomNumberGenerator.Fill(privateKeyBytes);
            privateKeyInt = new BigInteger(1, privateKeyBytes);
        } while (privateKeyInt.CompareTo(BigInteger.One) <= 0 || privateKeyInt.CompareTo(Domain.N) >= 0);

        // Derive uncompressed public key
        var publicKeyPoint = Domain.G.Multiply(privateKeyInt).Normalize();
        var publicKeyBytes = publicKeyPoint.GetEncoded(false); // false = uncompressed (65 bytes)

        return (
            Convert.ToHexString(privateKeyBytes).ToLowerInvariant(),
            Convert.ToHexString(publicKeyBytes).ToLowerInvariant()
        );
    }
}
