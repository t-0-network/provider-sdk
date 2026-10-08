using System.Buffers;
using System.Buffers.Binary;
using System.IO.Pipelines;
using System.Net;
using Grpc.Core;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Network;

/// <summary>
/// Request content that sends the first envelope, read ahead for signing, then forwards the rest
/// of the original content through a pipe as it is written, unbuffered.
/// </summary>
/// <remarks>
/// A request the server refused before reading it may be sent again, so the content can be sent
/// again until it has written the first envelope and starts forwarding the rest. See docs/STREAMING.md.
/// </remarks>
internal sealed class FirstFrameThenPipeContent : HttpContent
{
    private const int FramePrefixLength = 5;

    private const int StateIdle = 0;
    private const int StateForwarding = 1;
    private const int StateClosed = 2;

    private readonly byte[] _firstFrame;
    private readonly long? _length;

    // Null when nothing follows the first frame, which keeps the content re-sendable.
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
    /// The first frame, prefix included, as written; empty for a client stream completed without a message.
    /// </summary>
    public byte[] FirstFrame => _firstFrame;

    /// <summary>
    /// Starts <paramref name="source"/> writing into a pipe and returns once its first frame is available.
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

            // A body that had ended by the time its only frame was read (a small unary call) has a
            // known length even when the source had none.
            return new FirstFrameThenPipeContent(
                source, firstFrame, restIsEmpty ? null : pipe.Reader, restIsEmpty ? firstFrame.Length : length);
        }
        catch (Exception ex)
        {
            // Fails the source's later writes instead of leaving them waiting on a full pipe.
            await pipe.Reader.CompleteAsync(ex).ConfigureAwait(false);
            throw;
        }
    }

    /// <summary>
    /// Called when the request fails before the rest is forwarded, so the original content's writes
    /// fail instead of waiting for a reader.
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
        var state = Volatile.Read(ref _state);
        if (rest is not null && state != StateIdle)
            throw CannotSend(state);

        // A failure here leaves the rest untouched, so the content can be sent again.
        await stream.WriteAsync(_firstFrame, cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
        if (rest is null)
            return;

        state = Interlocked.CompareExchange(ref _state, StateForwarding, StateIdle);
        if (state != StateIdle)
            throw CannotSend(state);

        var forwardedRest = false;
        try
        {
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
        catch (Exception ex)
        {
            // Fail the original content's pending and later writes.
            await rest.CompleteAsync(ex).ConfigureAwait(false);
            throw;
        }

        await rest.CompleteAsync().ConfigureAwait(false);
        if (!forwardedRest)
            _rest = null;
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

    // Consumes the frame's bytes as it copies them, so a frame over the pipe's pause threshold
    // does not stall the writer; bytes after the frame stay in the pipe.
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
                    throw BrokenFirstMessage();
                }

                frame = new byte[ReadFrameLength(buffer.Slice(0, FramePrefixLength))];
            }

            var take = (int)Math.Min(buffer.Length, frame.Length - filled);
            buffer.Slice(0, take).CopyTo(frame.AsSpan(filled));
            filled += take;
            var remaining = buffer.Slice(take);

            if (filled == frame.Length)
            {
                reader.AdvanceTo(remaining.Start);
                return (frame, result.IsCompleted && remaining.IsEmpty);
            }

            reader.AdvanceTo(remaining.Start);
            if (result.IsCompleted)
                throw BrokenFirstMessage();
        }
    }

    private static RpcException BrokenFirstMessage() =>
        new(new Status(StatusCode.InvalidArgument, Messages.FirstMessageIncomplete));

    // The prefix and payload length. A frame longer than a byte array can hold could never arrive
    // whole, so it is a first message that ends early, refused before anything is signed.
    private static int ReadFrameLength(ReadOnlySequence<byte> prefix)
    {
        Span<byte> bytes = stackalloc byte[FramePrefixLength];
        prefix.CopyTo(bytes);
        var payloadLength = BinaryPrimitives.ReadUInt32BigEndian(bytes[1..]);
        if (payloadLength > Array.MaxLength - FramePrefixLength)
            throw BrokenFirstMessage();
        return FramePrefixLength + (int)payloadLength;
    }

    private static InvalidOperationException CannotSend(int state) => new(state == StateClosed
        ? Messages.ClientStreamAborted
        : Messages.ClientStreamResend);
}
