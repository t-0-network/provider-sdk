using System.Buffers.Binary;
using System.Net;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;

namespace T0.ProviderSdk.Tests.Network;

/// <summary>
/// Push-style request content, like grpc-dotnet's: it writes to the transport's stream when it is
/// sent and decides itself when (and whether) to write the next frame.
/// </summary>
internal sealed class PushContent : HttpContent
{
    private readonly Func<Stream, Task> _write;

    public PushContent(Func<Stream, Task> write, string contentType = "application/grpc")
    {
        _write = write;
        Headers.TryAddWithoutValidation("Content-Type", contentType);
    }

    public static PushContent Frames(params byte[][] frames) => new(async stream =>
    {
        foreach (var frame in frames)
        {
            await stream.WriteAsync(frame);
            await stream.FlushAsync();
        }
    });

    protected override Task SerializeToStreamAsync(Stream stream, TransportContext? context) => _write(stream);

    protected override bool TryComputeLength(out long length)
    {
        length = -1;
        return false;
    }
}

/// <summary>
/// Inner handler that records the request as it arrives and then reads its body the way a
/// transport does, into a <see cref="RecordingStream"/>.
/// </summary>
internal sealed class RecordingHandler : HttpMessageHandler
{
    public TaskCompletionSource<HttpRequestMessage> Received { get; } =
        new(TaskCreationOptions.RunContinuationsAsynchronously);

    public RecordingStream Body { get; } = new();

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Received.TrySetResult(request);
        if (request.Content is not null)
            await request.Content.CopyToAsync(Body, cancellationToken);
        return new HttpResponseMessage(HttpStatusCode.OK);
    }
}

/// <summary>
/// Write-only stream that keeps what it is given and lets a test wait for a length.
/// </summary>
internal sealed class RecordingStream : Stream
{
    private readonly MemoryStream _buffer = new();
    private readonly List<(long Length, TaskCompletionSource Reached)> _waiters = [];

    public byte[] ToArray()
    {
        lock (_buffer)
            return _buffer.ToArray();
    }

    public Task WaitForLengthAsync(long length)
    {
        lock (_buffer)
        {
            if (_buffer.Length >= length)
                return Task.CompletedTask;
            var reached = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            _waiters.Add((length, reached));
            return reached.Task;
        }
    }

    public override void Write(ReadOnlySpan<byte> buffer)
    {
        lock (_buffer)
        {
            _buffer.Write(buffer);
            foreach (var waiter in _waiters.Where(w => _buffer.Length >= w.Length).ToList())
            {
                waiter.Reached.TrySetResult();
                _waiters.Remove(waiter);
            }
        }
    }

    public override void Write(byte[] buffer, int offset, int count) => Write(buffer.AsSpan(offset, count));

    public override Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
    {
        Write(buffer.AsSpan(offset, count));
        return Task.CompletedTask;
    }

    public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default)
    {
        Write(buffer.Span);
        return ValueTask.CompletedTask;
    }

    public override void Flush() { }

    public override bool CanRead => false;
    public override bool CanSeek => false;
    public override bool CanWrite => true;
    public override long Length => throw new NotSupportedException();
    public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
    public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
    public override void SetLength(long value) => throw new NotSupportedException();
}

internal sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
{
    public override DateTimeOffset GetUtcNow() => now;
}

internal static class StreamingTestHelpers
{
    /// <summary>
    /// A gRPC frame: flags(1) || uint32be(length) || payload.
    /// </summary>
    public static byte[] Frame(byte[] payload, byte flags = 0)
    {
        var frame = new byte[5 + payload.Length];
        frame[0] = flags;
        BinaryPrimitives.WriteUInt32BigEndian(frame.AsSpan(1, 4), (uint)payload.Length);
        payload.CopyTo(frame, 5);
        return frame;
    }

    public static byte[] Frame(string payload) => Frame(System.Text.Encoding.UTF8.GetBytes(payload));

    /// <summary>
    /// Whether the request's signature headers verify over <paramref name="signed"/>.
    /// </summary>
    public static bool SignatureCovers(HttpRequestMessage request, byte[] signed)
    {
        var publicKey = HeaderBytes(request, Headers.PublicKey);
        var signature = HeaderBytes(request, Headers.Signature);
        var timestampMs = long.Parse(request.Headers.GetValues(Headers.SignatureTimestamp).Single());
        var digest = Keccak256.Hash(signed, Headers.EncodeTimestamp(timestampMs));
        return SignatureVerifier.Verify(publicKey, digest, signature);
    }

    public static byte[] HeaderBytes(HttpRequestMessage request, string name) =>
        HexUtils.HexToBytes(HexUtils.StripHexPrefix(request.Headers.GetValues(name).Single()));

    public static async Task<T> WithTimeout<T>(this Task<T> task) => await task.WaitAsync(TimeSpan.FromSeconds(10));

    public static async Task WithTimeout(this Task task) => await task.WaitAsync(TimeSpan.FromSeconds(10));
}
