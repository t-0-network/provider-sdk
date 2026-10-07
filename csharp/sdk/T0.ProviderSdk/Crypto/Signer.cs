using System.Diagnostics.CodeAnalysis;
using Org.BouncyCastle.Asn1.X9;
using Org.BouncyCastle.Crypto.Digests;
using Org.BouncyCastle.Crypto.EC;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Crypto.Signers;
using Org.BouncyCastle.Math;
using Org.BouncyCastle.Math.EC;
using Org.BouncyCastle.Math.EC.Multiplier;
using Org.BouncyCastle.Utilities;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// ECDSA signer using secp256k1 curve, producing Ethereum-style signatures.
/// Signature format: 65 bytes = r[32] + s[32] + v[1] (recovery ID).
/// Uses RFC 6979 deterministic nonce generation with HMAC-SHA256.
/// Thread-safe. It converts to <see cref="SignFn"/>, and still implements the obsolete
/// <c>ISigner</c> for code written against v1.2.
/// </summary>
#pragma warning disable CS0618 // ISigner is obsolete; Signer keeps implementing it for v1.2 code.
public sealed class Signer : ISigner
#pragma warning restore CS0618
{
    private static readonly X9ECParameters CurveParams = CustomNamedCurves.GetByName("secp256k1");
    private static readonly ECDomainParameters DomainParams = new(
        CurveParams.Curve,
        CurveParams.G,
        CurveParams.N,
        CurveParams.H
    );

    private const int PrivateKeyLength = 32;
    private const int PrivateKeyHexLength = 64;

    private readonly BigInteger _privateKey;
    private readonly byte[] _publicKey;
    private readonly ECPrivateKeyParameters _privateKeyParams;

    private Signer(BigInteger privateKey, byte[] publicKey)
    {
        _privateKey = privateKey;
        _publicKey = publicKey;
        _privateKeyParams = new ECPrivateKeyParameters(privateKey, DomainParams);
    }

    /// <summary>
    /// Creates a new Signer from a hex-encoded private key.
    /// </summary>
    /// <param name="hexPrivateKey">Private key as 64 hex characters, with or without a 0x or 0X prefix.</param>
    public static Signer FromHex(string hexPrivateKey)
    {
        if (string.IsNullOrEmpty(hexPrivateKey))
            throw new ArgumentException(Messages.PrivateKeyEmpty);

        var cleanHex = HexUtils.StripHexPrefix(hexPrivateKey);
        if (cleanHex.Length != PrivateKeyHexLength || !HexUtils.TryParseHex(cleanHex, out var privateKeyBytes))
            throw new ArgumentException(Messages.PrivateKeyMalformed);
        var privateKeyInt = new BigInteger(1, privateKeyBytes);
        ValidatePrivateKeyRange(privateKeyInt);
        var publicKey = DerivePublicKey(privateKeyInt);
        return new Signer(privateKeyInt, publicKey);
    }

    /// <summary>
    /// Returns the public key of a hex-encoded private key as "0x" and 130 lowercase hex characters
    /// (the 65-byte uncompressed key). The key is parsed as in <see cref="FromHex"/>, with the same errors.
    /// </summary>
    /// <param name="hexPrivateKey">Private key as 64 hex characters, with or without a 0x or 0X prefix.</param>
    public static string PublicKeyFromPrivateKey(string hexPrivateKey) =>
        FromHex(hexPrivateKey).GetPublicKeyHexPrefixed();

    /// <summary>
    /// Creates a new Signer from raw private key bytes.
    /// </summary>
    public static Signer FromBytes(byte[] privateKeyBytes)
    {
        if (privateKeyBytes is null || privateKeyBytes.Length != PrivateKeyLength)
            throw new ArgumentException(Messages.PrivateKeyBytesLength);

        var privateKeyInt = new BigInteger(1, privateKeyBytes);
        ValidatePrivateKeyRange(privateKeyInt);
        var publicKey = DerivePublicKey(privateKeyInt);
        return new Signer(privateKeyInt, publicKey);
    }

    /// <summary>
    /// This signer as the <see cref="SignFn"/> a client takes; null for null.
    /// </summary>
    [return: NotNullIfNotNull(nameof(signer))]
    public static implicit operator SignFn?(Signer? signer) =>
        signer is null ? null : digest =>
        {
            var result = signer.Sign(digest);
            return (result.Signature, result.PublicKey);
        };

    /// <summary>
    /// Signs a 32-byte digest and returns the signature with public key.
    /// Uses RFC 6979 deterministic nonce generation.
    /// </summary>
    public SignResult Sign(byte[] digest)
    {
        if (digest is null || digest.Length != PrivateKeyLength)
            throw new ArgumentException(Messages.DigestLength);

        var signer = new ECDsaSigner(new HMacDsaKCalculator(new Sha256Digest()));
        signer.Init(true, _privateKeyParams);

        var components = signer.GenerateSignature(digest);
        var r = components[0];
        var s = components[1];

        // Ensure s is in the lower half of the curve order (canonical signature)
        var halfN = DomainParams.N.ShiftRight(1);
        if (s.CompareTo(halfN) > 0)
            s = DomainParams.N.Subtract(s);

        // Calculate recovery ID (v)
        var recId = CalculateRecoveryId(digest, r, s);

        // Build 65-byte signature: r[32] + s[32] + v[1]
        var signature = new byte[65];
        var rBytes = BigIntegerToBytes(r, PrivateKeyLength);
        var sBytes = BigIntegerToBytes(s, PrivateKeyLength);
        Array.Copy(rBytes, 0, signature, 0, PrivateKeyLength);
        Array.Copy(sBytes, 0, signature, PrivateKeyLength, PrivateKeyLength);
        signature[64] = (byte)recId;

        return new SignResult(signature, _publicKey);
    }

    /// <summary>
    /// Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).
    /// </summary>
    public byte[] GetPublicKey() => (byte[])_publicKey.Clone();

    /// <summary>
    /// Returns the public key as hex string (without 0x prefix).
    /// </summary>
    public string GetPublicKeyHex() => HexUtils.BytesToHex(_publicKey);

    /// <summary>
    /// Returns the public key as hex string with 0x prefix.
    /// </summary>
    public string GetPublicKeyHexPrefixed() => "0x" + HexUtils.BytesToHex(_publicKey);

    private static void ValidatePrivateKeyRange(BigInteger privateKey)
    {
        var n = DomainParams.N;
        if (privateKey.CompareTo(BigInteger.One) < 0 || privateKey.CompareTo(n) >= 0)
            throw new ArgumentException(Messages.PrivateKeyOutOfRange);
    }

    private static byte[] DerivePublicKey(BigInteger privateKey)
    {
        var point = new FixedPointCombMultiplier().Multiply(DomainParams.G, privateKey);
        return point.GetEncoded(false); // uncompressed: 0x04 + x + y
    }

    private int CalculateRecoveryId(byte[] digest, BigInteger r, BigInteger s)
    {
        for (var recId = 0; recId < 2; recId++)
        {
            var recoveredKey = RecoverPublicKey(digest, r, s, recId);
            if (recoveredKey is not null && recoveredKey.SequenceEqual(_publicKey))
                return recId;
        }

        throw new InvalidOperationException(Messages.RecoveryIdNotFound);
    }

    private static byte[]? RecoverPublicKey(byte[] digest, BigInteger r, BigInteger s, int recId)
    {
        var n = DomainParams.N;
        var x = r;

        if (recId >= 2)
            x = x.Add(n);

        // Check if x is valid (within field)
        if (x.CompareTo(CurveParams.Curve.Field.Characteristic) >= 0)
            return null;

        // Decompress the point
        ECPoint R;
        try
        {
            var encodedPoint = new byte[33];
            encodedPoint[0] = (byte)(0x02 | (recId & 1));
            var xBytes = BigIntegerToBytes(x, PrivateKeyLength);
            Array.Copy(xBytes, 0, encodedPoint, 1, PrivateKeyLength);
            R = DomainParams.Curve.DecodePoint(encodedPoint);
        }
        catch (ArgumentException)
        {
            return null;
        }

        if (!R.Multiply(n).IsInfinity)
            return null;

        // Calculate public key: Q = r^-1 * (s*R - e*G)
        var e = new BigInteger(1, digest);
        var rInv = r.ModInverse(n);
        var Q = R.Multiply(s).Subtract(DomainParams.G.Multiply(e)).Multiply(rInv);

        return Q.GetEncoded(false);
    }

    private static byte[] BigIntegerToBytes(BigInteger value, int length)
    {
        return BigIntegers.AsUnsignedByteArray(length, value);
    }
}
