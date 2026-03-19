using System.Buffers.Binary;

namespace T0.ProviderSdk.Common;

/// <summary>
/// HTTP header names used for request signing and verification.
/// </summary>
public static class Headers
{
    public const string Signature = "X-Signature";
    public const string SignatureTimestamp = "X-Signature-Timestamp";
    public const string PublicKey = "X-Public-Key";

    /// <summary>
    /// The validity window for timestamp verification (60 seconds).
    /// </summary>
    public static readonly TimeSpan TimestampValidityWindow = TimeSpan.FromSeconds(60);

    /// <summary>
    /// Encodes a timestamp as 8-byte little-endian bytes.
    /// </summary>
    public static byte[] EncodeTimestamp(long timestampMs)
    {
        var bytes = new byte[8];
        BinaryPrimitives.WriteUInt64LittleEndian(bytes, (ulong)timestampMs);
        return bytes;
    }
}
