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
        if (options is null)
            throw new ArgumentNullException(null, Messages.ArgumentNull("options"));
        _next = next;
        _maxBodySize = options.MaxBodySize > 0 ? options.MaxBodySize : ProviderServerOptions.DefaultMaxBodySize;
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
            throw new ArgumentException(Messages.NetworkPublicKeyNotSet);
        try
        {
            return ParsePublicKey(key);
        }
        catch (Exception e) when (e is ArgumentException or FormatException or ArithmeticException)
        {
            throw new ArgumentException(Messages.NetworkPublicKeyInvalid(e.Message), e);
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
    internal static byte[] ParsePublicKey(string value)
    {
        var encoded = ParseHex(value) ?? throw new FormatException(Messages.PublicKeyNotHex);
        return ParsePublicKeyBytes(encoded);
    }

    /// <summary>
    /// The bytes of a public key, by rule V2: a compressed (33 bytes, 02 or 03) or uncompressed
    /// (65 bytes, 04) point on secp256k1. Returns the 65-byte uncompressed encoding. The public
    /// <see cref="SignatureVerifier.Verify"/> parses its key with this too.
    /// </summary>
    /// <exception cref="ArgumentException">The bytes are not a point on the curve.</exception>
    internal static byte[] ParsePublicKeyBytes(byte[] encoded)
    {
        // DecodePoint also accepts the hybrid forms (06, 07) and the point at infinity (00).
        var compressed = encoded.Length == 33 && (encoded[0] == 0x02 || encoded[0] == 0x03);
        var uncompressed = encoded.Length == 65 && encoded[0] == 0x04;
        if (!compressed && !uncompressed)
            throw new ArgumentException(Messages.PublicKeyNotAPoint);
        try
        {
            return Secp256k1.DecodePoint(encoded).GetEncoded(false);
        }
        catch (Exception e) when (e is ArgumentException or ArithmeticException)
        {
            throw new ArgumentException(Messages.PublicKeyNotAPoint);
        }
    }

    // For tests: the body limit in effect.
    internal long MaxBodySize => _maxBodySize;

    public async Task InvokeAsync(HttpContext context)
    {
        var headers = context.Request.Headers;

        // The headers, in the order of every SDK: the three are present and well-formed, the
        // timestamp window, the network key, and the signature length. None of the body is read.
        var publicKeyHex = headers[Headers.PublicKey].FirstOrDefault();
        if (string.IsNullOrEmpty(publicKeyHex))
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, Messages.MissingHeader(Headers.PublicKey));
            return;
        }

        var signatureHex = headers[Headers.Signature].FirstOrDefault();
        if (string.IsNullOrEmpty(signatureHex))
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, Messages.MissingHeader(Headers.Signature));
            return;
        }
        var signature = ParseHex(signatureHex);
        if (signature is null)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, Messages.InvalidHeaderEncoding(Headers.Signature));
            return;
        }

        var timestampValue = headers[Headers.SignatureTimestamp].FirstOrDefault();
        if (string.IsNullOrEmpty(timestampValue))
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, Messages.MissingHeader(Headers.SignatureTimestamp));
            return;
        }
        if (ParseTimestamp(timestampValue, out var timestampMs) is { } timestampError)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, timestampError);
            return;
        }

        var now = _timeProvider.GetUtcNow().ToUnixTimeMilliseconds();
        if (Math.Abs(now - timestampMs) > (long)ProviderServerOptions.TimestampWindow.TotalMilliseconds)
        {
            await WriteGrpcError(context, StatusCode.InvalidArgument, Messages.TimestampOutsideWindow);
            return;
        }

        // A value that is not a key is not the network key either; keys are compared as points.
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
            await WriteGrpcError(context, StatusCode.Unauthenticated, Messages.UnknownPublicKey);
            return;
        }

        if (signature.Length != 64 && signature.Length != 65)
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated, Messages.SignatureVerificationFailed);
            return;
        }

        // The body, at most the limit, held once in memory; a declared length over the limit is
        // refused before anything is read.
        var body = context.Request.ContentLength > _maxBodySize
            ? null
            : await ReadBodyWithCap(context.Request, _maxBodySize);
        if (body is null)
        {
            await WriteGrpcError(context, StatusCode.ResourceExhausted, Messages.BodyTooLarge(_maxBodySize));
            return;
        }
        context.Request.Body = new MemoryStream(body, writable: false);

        if (!VerifyWithFramingFallback(signerPublicKey, body, Headers.EncodeTimestamp(timestampMs), signature,
                context.Request.ContentType))
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated, Messages.SignatureVerificationFailed);
            return;
        }

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
        || (IsGrpc(contentType)
            && body.Length >= 5
            && body[0] == 0
            && BinaryPrimitives.ReadUInt32BigEndian(body.AsSpan(1, 4)) == body.Length - 5
            && SignatureVerifier.Verify(publicKey, Keccak256.Hash(body[5..], timestampBytes), signature));

    private static bool IsGrpc(string? contentType)
    {
        var mediaType = contentType?.Split(';')[0].Trim() ?? "";
        return mediaType.Equals("application/grpc", StringComparison.OrdinalIgnoreCase)
            || mediaType.StartsWith("application/grpc+", StringComparison.OrdinalIgnoreCase);
    }

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
    /// Parses the timestamp header: ASCII digits only (no sign, no spaces), at most
    /// <see cref="long.MaxValue"/>. Returns the message it is refused with, or null.
    /// </summary>
    internal static string? ParseTimestamp(string value, out long timestampMs)
    {
        timestampMs = 0;
        if (value.Length == 0 || !value.All(char.IsAsciiDigit))
            return Messages.TimestampNotDecimal;
        return long.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out timestampMs)
            ? null
            : Messages.TimestampOutOfRange;
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
