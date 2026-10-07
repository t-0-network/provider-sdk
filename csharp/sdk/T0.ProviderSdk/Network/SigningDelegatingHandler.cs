using Grpc.Core;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Network;

/// <summary>
/// HTTP message handler that signs outgoing requests with secp256k1:
/// digest = Keccak256(signed_bytes || LE_uint64(timestamp_ms)), sent as X-Public-Key,
/// X-Signature and X-Signature-Timestamp.
/// </summary>
/// <remarks>
/// It sits below the gRPC framer, so for enveloped content (<c>application/grpc</c>,
/// <c>application/grpc+*</c> and <c>application/connect+*</c>) it signs the first envelope exactly
/// as sent, prefix included, and sends the request as soon as that envelope exists: a client stream
/// goes out only once its first message is written. Other content is signed over the whole body.
/// See docs/STREAMING.md.
/// </remarks>
public sealed class SigningDelegatingHandler : DelegatingHandler
{
    private readonly SignFn _signer;
    private readonly TimeProvider _timeProvider;

    public SigningDelegatingHandler(SignFn signer, TimeProvider? timeProvider = null)
    {
        _signer = signer ?? throw new ArgumentNullException(null, Messages.SignerNull);
        _timeProvider = timeProvider ?? TimeProvider.System;
    }

    /// <summary>
    /// The base URL's path, such as <c>/v1</c>, put before each request's path; empty for none.
    /// Grpc.Net ignores the path of a channel's address. The signature does not cover the URL.
    /// </summary>
    internal string PathPrefix { get; init; } = "";

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        if (PathPrefix.Length > 0 && request.RequestUri is { } uri)
            request.RequestUri = new Uri(uri.GetLeftPart(UriPartial.Authority) + PathPrefix + uri.PathAndQuery);

        if (request.Content is { } content && IsEnveloped(content))
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

    private void AddSignatureHeaders(HttpRequestMessage request, byte[] signed)
    {
        var timestampMs = _timeProvider.GetUtcNow().ToUnixTimeMilliseconds();
        var digest = Keccak256.Hash(signed, Headers.EncodeTimestamp(timestampMs));
        SignResult result;
        try
        {
            // SignResult checks the output before anything is sent: the signature, then the key.
            var (signature, publicKey) = _signer(digest);
            result = new SignResult(signature, publicKey);
        }
        catch (Exception e)
        {
            // Not Unavailable, which callers retry: a failing signer is not transient.
            throw new RpcException(new Status(StatusCode.Internal, Messages.SigningFailed(e.Message), e));
        }

        SetHeader(request, Headers.PublicKey, result.PublicKeyHex);
        SetHeader(request, Headers.Signature, result.SignatureHex);
        SetHeader(request, Headers.SignatureTimestamp, timestampMs.ToString());
    }

    // Replaces a value the caller set (e.g. as call metadata): a second value would make the
    // request unverifiable.
    private static void SetHeader(HttpRequestMessage request, string name, string value)
    {
        request.Headers.Remove(name);
        request.Headers.TryAddWithoutValidation(name, value);
    }

    private static bool IsEnveloped(HttpContent content)
    {
        var mediaType = content.Headers.ContentType?.MediaType;
        return mediaType is not null
            && (string.Equals(mediaType, "application/grpc", StringComparison.OrdinalIgnoreCase)
                || mediaType.StartsWith("application/grpc+", StringComparison.OrdinalIgnoreCase)
                || mediaType.StartsWith("application/connect+", StringComparison.OrdinalIgnoreCase));
    }
}
