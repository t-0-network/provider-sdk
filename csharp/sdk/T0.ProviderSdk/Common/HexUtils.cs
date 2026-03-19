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
    /// Converts bytes to a lowercase hex string (without 0x prefix).
    /// </summary>
    public static string BytesToHex(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }

    /// <summary>
    /// Removes the 0x prefix from a hex string if present.
    /// </summary>
    public static string StripHexPrefix(string hex)
    {
        ArgumentNullException.ThrowIfNull(hex);
        if (hex.Length >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X'))
            return hex[2..];
        return hex;
    }

    /// <summary>
    /// Adds the 0x prefix to a hex string if not present.
    /// </summary>
    public static string AddHexPrefix(string hex)
    {
        ArgumentNullException.ThrowIfNull(hex);
        if (hex.Length >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X'))
            return hex;
        return "0x" + hex;
    }
}
