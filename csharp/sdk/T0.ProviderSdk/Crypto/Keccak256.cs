using Org.BouncyCastle.Crypto.Digests;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Keccak-256 hash function (legacy Keccak, NOT NIST SHA-3).
/// This is the same algorithm used by Ethereum.
/// Thread-safe: all methods are stateless.
/// </summary>
public static class Keccak256
{
    /// <summary>
    /// Computes the Keccak-256 hash of the input data.
    /// </summary>
    /// <returns>32-byte Keccak-256 hash.</returns>
    public static byte[] Hash(byte[] data)
    {
        ArgumentNullException.ThrowIfNull(data);

        var digest = new KeccakDigest(256);
        digest.BlockUpdate(data, 0, data.Length);
        var result = new byte[32];
        digest.DoFinal(result, 0);
        return result;
    }

    /// <summary>
    /// Computes the Keccak-256 hash of multiple byte arrays concatenated.
    /// </summary>
    public static byte[] Hash(params byte[][] parts)
    {
        var digest = new KeccakDigest(256);
        foreach (var part in parts)
        {
            if (part is not null)
                digest.BlockUpdate(part, 0, part.Length);
        }

        var result = new byte[32];
        digest.DoFinal(result, 0);
        return result;
    }
}
