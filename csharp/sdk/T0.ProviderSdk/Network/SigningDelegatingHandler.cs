using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Network;

/// <summary>
/// HTTP message handler that signs outgoing requests with secp256k1.
/// Computes digest = Keccak256(signed_bytes || LE_uint64(timestamp_ms)), signs it, and adds
/// X-Signature, X-Public-Key, X-Signature-Timestamp headers.
///
/// What it signs depends on the request's content type:
/// <list type="bullet">
/// <item>gRPC (<c>application/grpc</c>, <c>application/grpc+*</c>): the first request frame
/// exactly as sent, <c>flags(1) || uint32be(length) || payload</c>, compressed payload included.
/// The request is sent as soon as that frame is available and later frames are forwarded
/// unbuffered. Unary and server-streaming requests are one frame, so this is the whole body. A
/// client stream completed before its first message signs empty bytes.</item>
/// <item>Anything else: the whole body.</item>
/// </list>
///
/// A client-streaming request is sent only once its first message is written (or the stream is
/// completed), so write before awaiting the response headers.
///
/// Port of Go's SigningTransport (go/network/signing_transport.go).
/// </summary>
public sealed class SigningDelegatingHandler : DelegatingHandler
{
    private readonly Signer _signer;
    private readonly TimeProvider _timeProvider;

    public SigningDelegatingHandler(Signer signer, TimeProvider? timeProvider = null)
    {
        _signer = signer ?? throw new ArgumentNullException(nameof(signer));
        _timeProvider = timeProvider ?? TimeProvider.System;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        if (request.Content is { } content && IsGrpc(content))
            return await SendSignedOverFirstFrameAsync(request, content, cancellationToken).ConfigureAwait(false);

        // Read raw body bytes (CRITICAL: never re-serialize protobuf)
        var body = request.Content is not null
            ? await request.Content.ReadAsByteArrayAsync(cancellationToken)
            : [];

        AddSignatureHeaders(request, body);

        // Restore body content (since ReadAsByteArrayAsync consumed it)
        if (body.Length > 0)
        {
            var contentType = request.Content?.Headers.ContentType;
            request.Content = new ByteArrayContent(body);
            if (contentType is not null)
                request.Content.Headers.ContentType = contentType;
        }

        return await base.SendAsync(request, cancellationToken);
    }

    private async Task<HttpResponseMessage> SendSignedOverFirstFrameAsync(
        HttpRequestMessage request, HttpContent source, CancellationToken cancellationToken)
    {
        // Waits for the first frame only; for a client stream that is the first written message.
        var content = await FirstFrameThenPipeContent.CreateAsync(source, cancellationToken).ConfigureAwait(false);
        try
        {
            AddSignatureHeaders(request, content.FirstFrame);
            request.Content = content;
            return await base.SendAsync(request, cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            content.Abort(ex);
            throw;
        }
    }

    /// <summary>
    /// Signs <paramref name="signed"/> with the current timestamp and sets the signature headers:
    /// digest = Keccak256(signed || LE_uint64(timestamp_ms)).
    /// </summary>
    private void AddSignatureHeaders(HttpRequestMessage request, byte[] signed)
    {
        // Get current timestamp in milliseconds
        var timestampMs = _timeProvider.GetUtcNow().ToUnixTimeMilliseconds();

        // Compute digest = Keccak256(signed || LE_uint64(timestamp_ms))
        var digest = Keccak256.Hash(signed, Headers.EncodeTimestamp(timestampMs));

        // Sign the digest
        var result = _signer.Sign(digest);

        // Set signature headers, replacing any the caller set (e.g. as call metadata):
        // TryAddWithoutValidation appends, and a second value makes the request unverifiable.
        SetHeader(request, Headers.PublicKey, result.PublicKeyHex);
        SetHeader(request, Headers.Signature, result.SignatureHex);
        SetHeader(request, Headers.SignatureTimestamp, timestampMs.ToString());
    }

    private static void SetHeader(HttpRequestMessage request, string name, string value)
    {
        request.Headers.Remove(name);
        request.Headers.TryAddWithoutValidation(name, value);
    }

    /// <summary>
    /// <c>application/grpc</c> and <c>application/grpc+*</c>; not gRPC-Web.
    /// </summary>
    private static bool IsGrpc(HttpContent content)
    {
        var mediaType = content.Headers.ContentType?.MediaType;
        return mediaType is not null
            && (string.Equals(mediaType, "application/grpc", StringComparison.OrdinalIgnoreCase)
                || mediaType.StartsWith("application/grpc+", StringComparison.OrdinalIgnoreCase));
    }
}
