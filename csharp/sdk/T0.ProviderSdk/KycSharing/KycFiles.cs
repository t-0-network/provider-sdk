using Google.Protobuf;
using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.Manage.KycSharing;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.KycSharing;

/// <summary>
/// Uploads and downloads a KYC file through <c>KycFileService</c>. The caller builds the client.
/// These methods only run the stream.
/// </summary>
public static class KycFiles
{
    /// <summary>The largest <c>Chunk.data</c> a helper sends: the <c>max_len</c> of that field.</summary>
    public const int KycFileChunkMaxBytes = 1048576;

    /// <summary>
    /// Sends metadata, then <paramref name="data"/> in chunks, and returns the stored file id.
    /// <c>CompleteAsync</c> runs only after every write. A write that fails is not completed;
    /// the error returned is the status from <c>ResponseAsync</c> when that is a server status.
    /// An empty file sends no chunk. Chunks are views of <paramref name="data"/>.
    /// </summary>
    public static async Task<long> UploadFileAsync(
        KycFileService.KycFileServiceClient client,
        UploadFileRequest.Types.Metadata metadata,
        ReadOnlyMemory<byte> data,
        CallOptions options = default)
    {
        using var call = client.UploadFile(options);
        try
        {
            await call.RequestStream.WriteAsync(new UploadFileRequest { Metadata = metadata }, options.CancellationToken);
            for (int offset = 0; offset < data.Length;)
            {
                int length = Math.Min(KycFileChunkMaxBytes, data.Length - offset);
                var chunk = new UploadFileRequest
                {
                    Chunk = new UploadFileRequest.Types.Chunk
                    {
                        Data = UnsafeByteOperations.UnsafeWrap(data.Slice(offset, length)),
                    },
                };
                await call.RequestStream.WriteAsync(chunk, options.CancellationToken);
                offset += length;
            }
            await call.RequestStream.CompleteAsync();
        }
        catch (Exception writeError)
        {
            // The server may already have answered. Its status, not the write failure, is the result.
            try
            {
                return (await call.ResponseAsync).FileId;
            }
            catch (RpcException)
            {
                throw;
            }
            catch (Exception)
            {
                throw writeError;
            }
        }
        return (await call.ResponseAsync).FileId;
    }

    /// <summary>
    /// Returns the file metadata and every byte. A stream that is not one metadata message
    /// followed by chunks is disposed, which cancels it, and fails with <see cref="Messages.KycDownloadShape"/>.
    /// No bytes are returned. A server status is the call's error.
    /// </summary>
    public static async Task<(DownloadFileResponse.Types.Metadata Metadata, byte[] Data)> DownloadFileAsync(
        KycFileService.KycFileServiceClient client,
        DownloadFileRequest request,
        CallOptions options = default)
    {
        using var call = client.DownloadFile(request, options);
        if (!await call.ResponseStream.MoveNext(options.CancellationToken))
            throw ShapeError();
        if (call.ResponseStream.Current.PayloadCase != DownloadFileResponse.PayloadOneofCase.Metadata)
            throw ShapeError();
        var metadata = call.ResponseStream.Current.Metadata;
        using var bytes = new MemoryStream();
        while (await call.ResponseStream.MoveNext(options.CancellationToken))
        {
            if (call.ResponseStream.Current.PayloadCase != DownloadFileResponse.PayloadOneofCase.Chunk)
                throw ShapeError();
            call.ResponseStream.Current.Chunk.Data.WriteTo(bytes);
        }
        return (metadata, bytes.ToArray());
    }

    private static RpcException ShapeError() =>
        new(new Status(StatusCode.InvalidArgument, Messages.KycDownloadShape));
}
