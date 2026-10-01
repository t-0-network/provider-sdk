using System.Buffers;

namespace T0.ProviderSdk.Common;

/// <summary>
/// Utility class for hexadecimal encoding and decoding.
/// Thread-safe: all methods are stateless.
/// </summary>
public static class HexUtils
{
    /// <summary>
    /// Converts a hex string to bytes.
    /// </summary>
    /// <param name="hex">Hex string without 0x prefix.</param>
    /// <returns>Decoded bytes.</returns>
    public static byte[] HexToBytes(string hex)
    {
        ArgumentNullException.ThrowIfNull(hex);
        return Convert.FromHexString(hex);
    }

    /// <summary>
    /// Decodes an even-length ASCII hex string. Returns false for an odd length or a non-hex character.
    /// </summary>
    public static bool TryParseHex(ReadOnlySpan<char> hex, out byte[] bytes)
    {
        bytes = [];
        if ((hex.Length & 1) != 0)
            return false;

        var decoded = new byte[hex.Length / 2];
        if (Convert.FromHexString(hex, decoded, out var charsConsumed, out var bytesWritten) != OperationStatus.Done
            || charsConsumed != hex.Length
            || bytesWritten != decoded.Length)
            return false;

        bytes = decoded;
        return true;
    }

    /// <summary>
    /// Converts bytes to a lowercase hex string (without 0x prefix).
    /// </summary>
    public static string BytesToHex(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        return Convert.ToHexStringLower(bytes);
    }

    /// <summary>
    /// Removes the 0x prefix from a hex string if present.
    /// </summary>
    public static string StripHexPrefix(string hex)
    {
        ArgumentNullException.ThrowIfNull(hex);
        const string prefix = "0x";
        if (hex.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
            return hex[prefix.Length..];
        return hex;
    }

    /// <summary>
    /// Adds the 0x prefix to a hex string if not present.
    /// </summary>
    public static string AddHexPrefix(string hex)
    {
        ArgumentNullException.ThrowIfNull(hex);
        const string prefix = "0x";
        if (hex.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
            return hex;
        return "0x" + hex;
    }
}
