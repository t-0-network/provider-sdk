using System.Buffers;
using System.Buffers.Binary;
using System.IO.Pipelines;
using System.Net;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Request content for a gRPC call whose signature covers only the first request frame.
///
/// <see cref="CreateAsync"/> starts the original content writing into a pipe and waits for the
/// first frame, <c>flags(1) || uint32be(length) || payload</c>, exactly as written. The returned
/// content sends that frame and then forwards the rest of the pipe as it arrives, flushing each
/// chunk, so a client stream is never buffered.
///
/// It can be sent again (as SocketsHttpHandler does when an HTTP/2 stream is refused) as long as
/// nothing after the first frame has been forwarded yet.
/// </summary>
internal sealed class FirstFrameThenPipeContent : HttpContent
{
    private const int FramePrefixLength = 5;

    private const int StateIdle = 0;
    private const int StateForwarding = 1;
    private const int StateClosed = 2;

    private readonly byte[] _firstFrame;
    private readonly long? _length;

    // The rest of the body. Null when it is known to be empty.
    private volatile PipeReader? _rest;
    private int _state;

    private FirstFrameThenPipeContent(HttpContent source, byte[] firstFrame, PipeReader? rest, long? length)
    {
        _firstFrame = firstFrame;
        _rest = rest;
        _length = length;

        foreach (var header in source.Headers)
        {
            // The length comes from TryComputeLength.
            if (!string.Equals(header.Key, "Content-Length", StringComparison.OrdinalIgnoreCase))
                Headers.TryAddWithoutValidation(header.Key, header.Value);
        }
    }

    /// <summary>
    /// The first frame, prefix included, exactly as the original content wrote it. Empty when the
    /// content ended before writing anything (a client stream completed without a message).
    /// </summary>
    public byte[] FirstFrame => _firstFrame;

    /// <summary>
    /// Starts <paramref name="source"/> writing and returns once its first frame is available.
    /// A fault in the source, before or after the first frame, surfaces from this call or from
    /// sending the returned content.
    /// </summary>
    public static async Task<FirstFrameThenPipeContent> CreateAsync(HttpContent source, CancellationToken cancellationToken)
    {
        var length = source.Headers.ContentLength;
        var pipe = new Pipe(new PipeOptions(useSynchronizationContext: false));
        _ = CopySourceAsync(source, pipe.Writer, cancellationToken);

        try
        {
            var (firstFrame, restIsEmpty) = await ReadFirstFrameAsync(pipe.Reader, cancellationToken).ConfigureAwait(false);
            if (restIsEmpty)
                await pipe.Reader.CompleteAsync().ConfigureAwait(false);

            return new FirstFrameThenPipeContent(source, firstFrame, restIsEmpty ? null : pipe.Reader, length);
        }
        catch (Exception ex)
        {
            // Fails the source's later writes instead of leaving them waiting on a full pipe.
            await pipe.Reader.CompleteAsync(ex).ConfigureAwait(false);
            throw;
        }
    }

    /// <summary>
    /// Releases the pipe when the request fails before the rest of the body started to send, so
    /// the original content's writes fail instead of waiting for a reader.
    /// </summary>
    public void Abort(Exception exception)
    {
        var rest = _rest;
        if (rest is not null && Interlocked.CompareExchange(ref _state, StateClosed, StateIdle) == StateIdle)
            rest.Complete(exception);
    }

    protected override Task SerializeToStreamAsync(Stream stream, TransportContext? context) =>
        SerializeToStreamAsync(stream, context, CancellationToken.None);

    protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context, CancellationToken cancellationToken)
    {
        var rest = _rest;
        if (rest is not null && Interlocked.CompareExchange(ref _state, StateForwarding, StateIdle) != StateIdle)
        {
            throw new InvalidOperationException(
                "The request body of a streaming call cannot be sent again once its later messages were sent.");
        }

        var forwardedRest = false;
        try
        {
            await stream.WriteAsync(_firstFrame, cancellationToken).ConfigureAwait(false);
            await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
            if (rest is null)
                return;

            while (true)
            {
                var result = await rest.ReadAsync(cancellationToken).ConfigureAwait(false);
                var buffer = result.Buffer;
                if (!buffer.IsEmpty)
                {
                    forwardedRest = true;
                    foreach (var segment in buffer)
                        await stream.WriteAsync(segment, cancellationToken).ConfigureAwait(false);
                    await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
                }

                rest.AdvanceTo(buffer.End);
                if (result.IsCompleted)
                    break;
            }
        }
        catch (Exception ex) when (rest is not null)
        {
            // The request is gone: fail the original content's pending and later writes.
            await rest.CompleteAsync(ex).ConfigureAwait(false);
            throw;
        }

        await rest.CompleteAsync().ConfigureAwait(false);
        if (!forwardedRest)
            _rest = null; // Nothing followed the first frame, so the body can be sent again.
    }

    protected override bool TryComputeLength(out long length)
    {
        length = _length ?? -1;
        return _length.HasValue;
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
            Abort(new ObjectDisposedException(nameof(FirstFrameThenPipeContent)));
        base.Dispose(disposing);
    }

    private static async Task CopySourceAsync(HttpContent source, PipeWriter writer, CancellationToken cancellationToken)
    {
        try
        {
            await source.CopyToAsync(writer.AsStream(leaveOpen: true), cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            await writer.CompleteAsync(ex).ConfigureAwait(false);
            return;
        }

        await writer.CompleteAsync().ConfigureAwait(false);
    }

    /// <summary>
    /// Reads exactly one frame and consumes nothing after it. Bytes are consumed as they are
    /// copied, so a frame larger than the pipe's pause threshold does not stall the writer.
    /// </summary>
    private static async Task<(byte[] Frame, bool RestIsEmpty)> ReadFirstFrameAsync(
        PipeReader reader, CancellationToken cancellationToken)
    {
        byte[]? frame = null;
        var filled = 0;

        while (true)
        {
            var result = await reader.ReadAsync(cancellationToken).ConfigureAwait(false);
            var buffer = result.Buffer;

            if (frame is null)
            {
                if (buffer.Length < FramePrefixLength)
                {
                    if (!result.IsCompleted)
                    {
                        reader.AdvanceTo(buffer.Start, buffer.End);
                        continue;
                    }

                    reader.AdvanceTo(buffer.End);
                    if (buffer.IsEmpty)
                        return ([], true); // Completed before the first message: sign empty bytes.
                    throw new InvalidOperationException("gRPC request body ends inside the prefix of its first message.");
                }

                frame = new byte[FramePrefixLength + ReadPayloadLength(buffer.Slice(0, FramePrefixLength))];
            }

            var take = (int)Math.Min(buffer.Length, frame.Length - filled);
            buffer.Slice(0, take).CopyTo(frame.AsSpan(filled));
            filled += take;
            var remaining = buffer.Slice(take);

            if (filled == frame.Length)
            {
                // Later messages stay in the pipe for SerializeToStreamAsync.
                reader.AdvanceTo(remaining.Start);
                return (frame, result.IsCompleted && remaining.IsEmpty);
            }

            reader.AdvanceTo(remaining.Start);
            if (result.IsCompleted)
                throw new InvalidOperationException("gRPC request body ends inside its first message.");
        }
    }

    private static int ReadPayloadLength(ReadOnlySequence<byte> prefix)
    {
        Span<byte> bytes = stackalloc byte[FramePrefixLength];
        prefix.CopyTo(bytes);
        var length = BinaryPrimitives.ReadUInt32BigEndian(bytes[1..]);
        if (length > Array.MaxLength - FramePrefixLength)
            throw new InvalidOperationException($"gRPC request message of {length} bytes is too large to sign.");
        return (int)length;
    }
}
