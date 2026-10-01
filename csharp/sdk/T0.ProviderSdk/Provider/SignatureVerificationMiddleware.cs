using Grpc.Core;
using Microsoft.AspNetCore.Http;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// ASP.NET Core middleware that verifies incoming request signatures.
/// Works on raw HTTP body bytes (like Go's verify_signature.go middleware).
/// CRITICAL: operates before protobuf deserialization to use original wire bytes.
/// </summary>
public sealed class SignatureVerificationMiddleware
{
    private readonly RequestDelegate _next;
    private readonly byte[] _networkPublicKey;
    private readonly long _maxBodySize;
    private readonly TimeProvider _timeProvider;

    public SignatureVerificationMiddleware(
        RequestDelegate next,
        ProviderServerOptions options,
        TimeProvider? timeProvider = null)
    {
        _next = next;
        _maxBodySize = options.MaxBodySize;
        _timeProvider = timeProvider ?? TimeProvider.System;

        _networkPublicKey = ParseNetworkPublicKey(options.NetworkPublicKeyHex);
    }

    private static readonly Org.BouncyCastle.Math.EC.ECCurve Secp256k1 =
        Org.BouncyCastle.Crypto.EC.CustomNamedCurves.GetByName("secp256k1").Curve;

    /// <summary>
    /// Parses the configured network public key, so a missing or mistyped key fails at startup
    /// rather than on every request. Surrounding whitespace is trimmed.
    /// </summary>
    /// <exception cref="ArgumentException">"network public key is not set" for a null, empty or
    /// whitespace-only key; "invalid network public key: ..." for a malformed one.</exception>
    internal static byte[] ParseNetworkPublicKey(string? networkPublicKeyHex)
    {
        var key = networkPublicKeyHex?.Trim() ?? "";
        if (key.Length == 0)
            throw new ArgumentException("network public key is not set");
        try
        {
            return ParsePublicKey(key);
        }
        catch (Exception e) when (e is ArgumentException or FormatException or ArithmeticException)
        {
            throw new ArgumentException($"invalid network public key: {e.Message}", e);
        }
    }

    /// <summary>
    /// Parses a public key, the configured network key and the X-Public-Key header alike: hex with
    /// an optional 0x prefix of a 33-byte compressed (0x02/0x03) or 65-byte uncompressed (0x04)
    /// secp256k1 point. Returns the 65-byte uncompressed encoding, so both forms of a key compare equal.
    /// </summary>
    /// <exception cref="FormatException">The value is not hex.</exception>
    /// <exception cref="ArgumentException">The bytes are not a public key.</exception>
    internal static byte[] ParsePublicKey(string value) =>
        ParseHex(value) is { } encoded
            ? DecodePublicKey(encoded)
            : throw new FormatException("public key must be hex with an optional 0x prefix");

    /// <summary>
    /// Decodes the bytes of a public key (see <see cref="ParsePublicKey"/>) to its 65-byte
    /// uncompressed encoding.
    /// </summary>
    /// <exception cref="ArgumentException">The bytes are not a public key.</exception>
    private static byte[] DecodePublicKey(byte[] encoded)
    {
        // Checked here: DecodePoint also accepts the 65-byte hybrid encodings 0x06 and 0x07.
        if (!(encoded.Length == 33 && encoded[0] is 0x02 or 0x03) && !(encoded.Length == 65 && encoded[0] == 0x04))
            throw new ArgumentException(
                "public key must be 33 bytes compressed (0x02 or 0x03 prefix) or 65 bytes uncompressed (0x04 prefix)");
        // Throws for a point off the curve.
        return Secp256k1.DecodePoint(encoded).GetEncoded(false);
    }

    public async Task InvokeAsync(HttpContext context)
    {
        // 1. Parse public key header
        var publicKey = ParseHexHeader(context, Headers.PublicKey);
        if (publicKey is null)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument,
                $"missing or invalid header: {Headers.PublicKey}");
            return;
        }

        // 2. Parse signature header
        var signature = ParseHexHeader(context, Headers.Signature);
        if (signature is null)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument,
                $"missing or invalid header: {Headers.Signature}");
            return;
        }

        // 3. Parse and validate timestamp
        if (!TryParseTimestamp(context, out var timestampMs))
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument,
                $"missing or invalid header: {Headers.SignatureTimestamp}");
            return;
        }

        var timestampBytes = Headers.EncodeTimestamp(timestampMs);

        var now = _timeProvider.GetUtcNow().ToUnixTimeMilliseconds();
        if (Math.Abs(now - timestampMs) > (long)Headers.TimestampValidityWindow.TotalMilliseconds)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument,
                "timestamp is outside the allowed time window");
            return;
        }

        // 4. Verify the public key is the expected network key, compressed or uncompressed
        byte[]? signerPublicKey;
        try
        {
            signerPublicKey = DecodePublicKey(publicKey);
        }
        catch (Exception e) when (e is ArgumentException or ArithmeticException)
        {
            signerPublicKey = null;
        }
        if (signerPublicKey is null || !signerPublicKey.AsSpan().SequenceEqual(_networkPublicKey))
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated, "unknown public key");
            return;
        }

        // 5. Read raw body bytes (with size cap)
        context.Request.EnableBuffering();
        var body = await ReadBodyWithCap(context.Request, _maxBodySize);
        if (body is null)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument,
                $"max payload size of {_maxBodySize} bytes exceeded");
            return;
        }

        // Rewind body stream for downstream handlers
        context.Request.Body.Position = 0;

        // 6. Compute digest = Keccak256(body || timestampBytes)
        var digest = Keccak256.Hash(body, timestampBytes);

        // 7. Verify signature
        if (!SignatureVerifier.Verify(signerPublicKey, digest, signature))
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated,
                "signature verification failed");
            return;
        }

        // 8. Proceed to next middleware/handler
        await _next(context);
    }

    /// <summary>
    /// Parses a hex-encoded header value (0x prefix optional). Returns null on failure.
    /// </summary>
    private static byte[]? ParseHexHeader(HttpContext context, string headerName) =>
        ParseHex(context.Request.Headers[headerName].FirstOrDefault());

    /// <summary>
    /// Decodes at least one byte of even-length hex (0x prefix optional). Returns null on failure.
    /// </summary>
    private static byte[]? ParseHex(string? value)
    {
        if (string.IsNullOrEmpty(value))
            return null;

        const string prefix = "0x";
        var hex = value.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)
            ? value[prefix.Length..]
            : value;

        if (hex.Length == 0 || !HexUtils.TryParseHex(hex, out var bytes))
            return null;

        return bytes;
    }

    /// <summary>
    /// Parses the timestamp header. Returns false on failure.
    /// </summary>
    private static bool TryParseTimestamp(HttpContext context, out long timestampMs)
    {
        timestampMs = 0;
        var tsValue = context.Request.Headers[Headers.SignatureTimestamp].FirstOrDefault();
        return !string.IsNullOrEmpty(tsValue) && long.TryParse(tsValue, out timestampMs);
    }

    private static async Task<byte[]?> ReadBodyWithCap(HttpRequest request, long maxSize)
    {
        using var ms = new MemoryStream();
        var buffer = new byte[8192];
        long totalRead = 0;

        while (true)
        {
            var bytesRead = await request.Body.ReadAsync(buffer);
            if (bytesRead == 0) break;

            totalRead += bytesRead;
            if (totalRead > maxSize) return null;

            ms.Write(buffer, 0, bytesRead);
        }

        return ms.ToArray();
    }

    private static async Task WriteGrpcError(HttpContext context, StatusCode code, string message)
    {
        context.Response.Headers.Append("grpc-status", ((int)code).ToString());
        context.Response.Headers.Append("grpc-message", message);
        context.Response.StatusCode = 200; // gRPC always returns 200 at HTTP level
        context.Response.ContentType = "application/grpc";
        await context.Response.CompleteAsync();
    }
}
