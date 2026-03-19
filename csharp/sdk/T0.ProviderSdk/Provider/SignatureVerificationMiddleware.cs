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

        if (string.IsNullOrEmpty(options.NetworkPublicKeyHex))
            throw new ArgumentException("network public key is required");

        _networkPublicKey = SignatureVerifier.ParsePublicKeyHex(options.NetworkPublicKeyHex);
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

        // 4. Verify the public key matches the expected network key
        if (!publicKey.AsSpan().SequenceEqual(_networkPublicKey))
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
        if (!SignatureVerifier.Verify(publicKey, digest, signature))
        {
            await WriteGrpcError(context, StatusCode.Unauthenticated,
                "signature verification failed");
            return;
        }

        // 8. Proceed to next middleware/handler
        await _next(context);
    }

    /// <summary>
    /// Parses a hex-encoded header value (with 0x prefix). Returns null on failure.
    /// </summary>
    private static byte[]? ParseHexHeader(HttpContext context, string headerName)
    {
        var headerValue = context.Request.Headers[headerName].FirstOrDefault();
        if (string.IsNullOrEmpty(headerValue))
            return null;

        if (headerValue.Length < 2 || !headerValue.StartsWith("0x", StringComparison.OrdinalIgnoreCase))
            return null;

        try
        {
            return Convert.FromHexString(headerValue[2..]);
        }
        catch (FormatException)
        {
            return null;
        }
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
