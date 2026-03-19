using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Network;

/// <summary>
/// HTTP message handler that signs outgoing requests with secp256k1.
/// Reads the raw request body, computes digest = Keccak256(body || LE_uint64(timestamp_ms)),
/// signs it, and adds X-Signature, X-Public-Key, X-Signature-Timestamp headers.
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
        // Read raw body bytes (CRITICAL: never re-serialize protobuf)
        var body = request.Content is not null
            ? await request.Content.ReadAsByteArrayAsync(cancellationToken)
            : [];

        // Get current timestamp in milliseconds
        var timestampMs = _timeProvider.GetUtcNow().ToUnixTimeMilliseconds();

        // Encode timestamp as 8-byte little-endian
        var timestampBytes = Headers.EncodeTimestamp(timestampMs);

        // Compute digest = Keccak256(body || timestampBytes)
        var digest = Keccak256.Hash(body, timestampBytes);

        // Sign the digest
        var result = _signer.Sign(digest);

        // Set signature headers
        request.Headers.TryAddWithoutValidation(Headers.PublicKey, result.PublicKeyHex);
        request.Headers.TryAddWithoutValidation(Headers.Signature, result.SignatureHex);
        request.Headers.TryAddWithoutValidation(Headers.SignatureTimestamp, timestampMs.ToString());

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
}
