using System.Buffers.Binary;
using System.Globalization;
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
    /// an optional 0x prefix of an encoded secp256k1 point, such as 33 bytes compressed (0x02/0x03)
    /// or 65 bytes uncompressed (0x04). Returns the 65-byte uncompressed encoding, so all forms of
    /// a key compare equal.
    /// </summary>
    /// <exception cref="FormatException">The value is not hex.</exception>
    /// <exception cref="ArgumentException">The bytes are not a point on the curve.</exception>
    internal static byte[] ParsePublicKey(string value) =>
        ParseHex(value) is { } encoded
            ? Secp256k1.DecodePoint(encoded).GetEncoded(false)
            : throw new FormatException("public key must be hex with an optional 0x prefix");

    public async Task InvokeAsync(HttpContext context)
    {
        // 1. Check the public key header is present (whether it is the network key: step 4)
        var publicKeyHex = context.Request.Headers[Headers.PublicKey].FirstOrDefault();
        if (string.IsNullOrEmpty(publicKeyHex))
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
        if (!TryParseTimestamp(context.Request.Headers[Headers.SignatureTimestamp].FirstOrDefault(), out var timestampMs))
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
            signerPublicKey = ParsePublicKey(publicKeyHex);
        }
        catch (Exception e) when (e is FormatException or ArgumentException or ArithmeticException)
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
            await WriteGrpcError(context, StatusCode.ResourceExhausted,
                $"max payload size of {_maxBodySize} bytes exceeded");
            return;
        }

        // Rewind body stream for downstream handlers
        context.Request.Body.Position = 0;

        // 6. Verify the signature over Keccak256(body || timestampBytes)
        if (!VerifyWithFramingFallback(signerPublicKey, body, timestampBytes, signature, context.Request.ContentType))
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated,
                "signature verification failed");
            return;
        }

        // 7. Proceed to next middleware/handler
        await _next(context);
    }

    /// <summary>
    /// Verifies the signature over the whole body; failing that, for a gRPC request whose body is
    /// exactly one uncompressed frame, over the message without its 5-byte prefix, which is what a
    /// signer above the gRPC framer covers (the Java SDK's NetworkClient). Same rule as Go's
    /// <c>signatureVerifier.verify</c>.
    /// </summary>
    private static bool VerifyWithFramingFallback(
        byte[] publicKey, byte[] body, byte[] timestampBytes, byte[] signature, string? contentType) =>
        SignatureVerifier.Verify(publicKey, Keccak256.Hash(body, timestampBytes), signature)
        || (contentType?.StartsWith("application/grpc", StringComparison.Ordinal) == true
            && body.Length >= 5
            && body[0] == 0
            && BinaryPrimitives.ReadUInt32BigEndian(body.AsSpan(1, 4)) == body.Length - 5
            && SignatureVerifier.Verify(publicKey, Keccak256.Hash(body[5..], timestampBytes), signature));

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
    /// Parses the timestamp header: decimal digits only (no sign, no spaces), at most
    /// <see cref="long.MaxValue"/>. Returns false on failure.
    /// </summary>
    internal static bool TryParseTimestamp(string? value, out long timestampMs)
    {
        timestampMs = 0;
        return !string.IsNullOrEmpty(value)
            && long.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out timestampMs);
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
