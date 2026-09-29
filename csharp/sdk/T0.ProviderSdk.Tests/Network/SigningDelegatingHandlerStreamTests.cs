using System.Net;
using System.Text;
using Google.Protobuf;
using Grpc.Core;
using Grpc.Net.Client;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using static T0.ProviderSdk.Tests.Network.StreamingTestHelpers;
using StringValue = Google.Protobuf.WellKnownTypes.StringValue;

namespace T0.ProviderSdk.Tests.Network;

public class SigningDelegatingHandlerStreamTests
{
    private const string TestPrivateKey = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private static readonly DateTimeOffset FixedTime = DateTimeOffset.FromUnixTimeMilliseconds(1706000000000);

    private static (HttpClient Client, RecordingHandler Inner) NewClient()
    {
        var inner = new RecordingHandler();
        var handler = new SigningDelegatingHandler(Signer.FromHex(TestPrivateKey), new FixedTimeProvider(FixedTime))
        {
            InnerHandler = inner
        };
        return (new HttpClient(handler), inner);
    }

    private static HttpRequestMessage Post(HttpContent content) =>
        new(HttpMethod.Post, "http://example.com/test.v1.StreamTest/ClientStream") { Content = content };

    [Fact]
    public async Task ClientStream_IsSentBeforeLaterFramesAreWritten_AndSignedOverTheFirstFrame()
    {
        var frame1 = Frame("m1");
        var frame2 = Frame("m2");
        var frame3 = Frame("m3");
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(frame1);
            await stream.FlushAsync();
            await gate.Task;
            await stream.WriteAsync(frame2);
            await stream.FlushAsync();
            await stream.WriteAsync(frame3);
            await stream.FlushAsync();
        });
        var (client, inner) = NewClient();

        var send = client.SendAsync(Post(source));

        var request = await inner.Received.Task.WithTimeout();
        await inner.Body.WaitForLengthAsync(frame1.Length).WithTimeout();
        Assert.False(send.IsCompleted);
        Assert.Equal(frame1, inner.Body.ToArray());

        gate.SetResult();
        using var response = await send.WithTimeout();

        Assert.Equal([.. frame1, .. frame2, .. frame3], inner.Body.ToArray());
        Assert.True(SignatureCovers(request, frame1));
        Assert.False(SignatureCovers(request, inner.Body.ToArray()));
        Assert.Equal(FixedTime.ToUnixTimeMilliseconds().ToString(),
            request.Headers.GetValues(Headers.SignatureTimestamp).Single());
        Assert.Equal("application/grpc", request.Content!.Headers.ContentType!.MediaType);
        Assert.Null(request.Content.Headers.ContentLength);

        // Later frames were forwarded, not kept, so the body cannot be sent again.
        await Assert.ThrowsAsync<InvalidOperationException>(() => request.Content.CopyToAsync(new MemoryStream()));
    }

    [Fact]
    public async Task FirstFrameLargerThanAPipeSegment_IsSignedWhole()
    {
        // Over a pipe segment (4 KiB) and the pause threshold (64 KiB); the length needs three bytes.
        var payload = new byte[100_000];
        Random.Shared.NextBytes(payload);
        var frame1 = Frame(payload);
        var frame2 = Frame("m2");
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(PushContent.Frames(frame1, frame2))).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.True(SignatureCovers(request, frame1));
        Assert.Equal([.. frame1, .. frame2], inner.Body.ToArray());
    }

    [Fact]
    public async Task FirstFrameSplitAcrossWrites_IsReassembled()
    {
        var frame1 = Frame(Encoding.UTF8.GetBytes("a first message written in pieces"), flags: 1);
        var frame2 = Frame("m2");
        var source = new PushContent(async stream =>
        {
            // Split inside the prefix and inside the payload.
            foreach (var (start, end) in new[] { (0, 2), (2, 5), (5, 12), (12, frame1.Length) })
            {
                await stream.WriteAsync(frame1.AsMemory(start..end));
                await stream.FlushAsync();
                await Task.Delay(20);
            }
            await stream.WriteAsync(frame2);
        });
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.True(SignatureCovers(request, frame1));
        Assert.Equal([.. frame1, .. frame2], inner.Body.ToArray());
    }

    [Fact]
    public async Task EmptyClientStream_SignsEmptyBytesAndIsSent()
    {
        var source = new PushContent(_ => Task.CompletedTask);
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.True(SignatureCovers(request, []));
        Assert.Empty(inner.Body.ToArray());
    }

    [Fact]
    public async Task UnaryRequest_CanBeSentAgain()
    {
        // What SocketsHttpHandler does when an HTTP/2 stream is refused: serialize the content again.
        var frame = Frame("hello");
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(PushContent.Frames(frame))).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        var resent = new MemoryStream();
        await request.Content!.CopyToAsync(resent);
        Assert.Equal(frame, inner.Body.ToArray());
        Assert.Equal(frame, resent.ToArray());
    }

    [Fact]
    public async Task FirstFrameWriteFails_ContentCanBeSentAgain()
    {
        var frame1 = Frame("m1");
        var frame2 = Frame("m2");
        var inner = new RetryingHandler();
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(frame1);
            await stream.FlushAsync();
            await inner.FirstAttempt.Task;
            await stream.WriteAsync(frame2);
            await stream.FlushAsync();
        });
        var handler = new SigningDelegatingHandler(Signer.FromHex(TestPrivateKey), new FixedTimeProvider(FixedTime))
        {
            InnerHandler = inner
        };
        using var client = new HttpClient(handler);

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        Assert.True(HasInChain<IOException>(await inner.FirstAttempt.Task));
        Assert.Equal([.. frame1, .. frame2], inner.Body.ToArray());
    }

    [Fact]
    public async Task KnownLength_IsKept()
    {
        var body = Frame("hello");
        var source = new ByteArrayContent(body);
        source.Headers.ContentType = new("application/grpc+proto");
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.Equal(body.Length, request.Content!.Headers.ContentLength);
        Assert.Equal("application/grpc+proto", request.Content.Headers.ContentType!.MediaType);
        Assert.True(SignatureCovers(request, body));
    }

    [Fact]
    public async Task SingleFrameBody_GetsItsLength()
    {
        // A single-frame body of unknown length, as a unary gRPC call sends it, still goes out with a length.
        var frame = Frame("hello");
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(PushContent.Frames(frame))).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.Equal(frame.Length, request.Content!.Headers.ContentLength);
        Assert.Equal(frame, inner.Body.ToArray());
    }

    [Theory]
    [InlineData("application/grpc")]
    [InlineData("application/grpc+proto")]
    [InlineData("application/connect+proto")]
    [InlineData("application/connect+json")]
    public async Task EnvelopedContent_IsSignedOverTheFirstEnvelope(string contentType)
    {
        var frame1 = Frame("m1");
        var frame2 = Frame("m2");
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(frame1);
            await stream.WriteAsync(frame2);
        }, contentType);
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.True(SignatureCovers(request, frame1));
        Assert.Equal([.. frame1, .. frame2], inner.Body.ToArray());
    }

    [Theory]
    [InlineData("application/proto")]
    [InlineData("application/json")]
    public async Task ContentThatIsNotEnveloped_IsSignedOverTheWholeBody(string contentType)
    {
        byte[] body = [.. Frame("m1"), .. Frame("m2")];
        var source = new ByteArrayContent(body);
        source.Headers.TryAddWithoutValidation("Content-Type", contentType);
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(Post(source)).WithTimeout();

        var request = await inner.Received.Task.WithTimeout();
        Assert.True(SignatureCovers(request, body));
        Assert.Equal(body, inner.Body.ToArray());
    }

    [Fact]
    public async Task SourceFaultBeforeTheFirstFrame_Surfaces()
    {
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(Frame("m1").AsMemory(0, 3));
            throw new SourceFailedException();
        });
        var (client, inner) = NewClient();

        var ex = await Assert.ThrowsAnyAsync<Exception>(() => client.SendAsync(Post(source)).WithTimeout());

        Assert.True(HasInChain<SourceFailedException>(ex), ex.ToString());
        Assert.False(inner.Received.Task.IsCompleted);
    }

    [Fact]
    public async Task SourceFaultAfterTheFirstFrame_Surfaces()
    {
        var frame1 = Frame("m1");
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(frame1);
            await stream.FlushAsync();
            await Task.Delay(20);
            throw new SourceFailedException();
        });
        var (client, inner) = NewClient();

        var ex = await Assert.ThrowsAnyAsync<Exception>(() => client.SendAsync(Post(source)).WithTimeout());

        Assert.True(HasInChain<SourceFailedException>(ex), ex.ToString());
        Assert.True(SignatureCovers(await inner.Received.Task.WithTimeout(), frame1));
    }

    [Theory]
    [InlineData(3)] // inside the prefix
    [InlineData(6)] // inside the payload
    public async Task TruncatedFirstFrame_Fails(int length)
    {
        var frame1 = Frame("m1");
        var (client, inner) = NewClient();

        var ex = await Assert.ThrowsAsync<RpcException>(
            () => client.SendAsync(Post(PushContent.Frames(frame1[..length]))).WithTimeout());
        Assert.Equal(StatusCode.InvalidArgument, ex.StatusCode);
        Assert.Equal("streaming request ends inside its first message", ex.Status.Detail);
        Assert.False(inner.Received.Task.IsCompleted);
    }

    [Fact]
    public async Task TruncatedFirstFrame_ReachesTheCallerAsInvalidArgument()
    {
        // The gRPC framer writes whole messages, so a handler above the signer cuts the body.
        var inner = new RecordingHandler();
        var signing = new SigningDelegatingHandler(Signer.FromHex(TestPrivateKey)) { InnerHandler = inner };
        using var channel = GrpcChannel.ForAddress("http://example.com", new GrpcChannelOptions
        {
            HttpClient = new HttpClient(new TruncatingHandler(length: 6) { InnerHandler = signing }),
            DisposeHttpClient = true,
        });
        var marshaller = Marshallers.Create(value => value.ToByteArray(), StringValue.Parser.ParseFrom);
        var method = new Method<StringValue, StringValue>(
            MethodType.Unary, "test.v1.StreamTest", "Unary", marshaller, marshaller);

        using var call = channel.CreateCallInvoker().AsyncUnaryCall(
            method, null, new CallOptions(deadline: DateTime.UtcNow.AddSeconds(10)), new StringValue { Value = "m1" });
        var ex = await Assert.ThrowsAsync<RpcException>(() => call.ResponseAsync).WithTimeout();

        Assert.Equal(StatusCode.InvalidArgument, ex.StatusCode);
        Assert.Equal("streaming request ends inside its first message", ex.Status.Detail);
        Assert.False(inner.Received.Task.IsCompleted);
    }

    [Fact]
    public async Task HugeLengthPrefix_DoesNotAllocateAheadOfItsBytes()
    {
        // Claims almost 2 GiB, then ends after two bytes.
        byte[] body = [0, 0x7F, 0xFF, 0x00, 0x00, .. Encoding.UTF8.GetBytes("m1")];
        var (client, inner) = NewClient();
        var before = GC.GetTotalAllocatedBytes(precise: true);

        var ex = await Assert.ThrowsAsync<RpcException>(
            () => client.SendAsync(Post(PushContent.Frames(body))).WithTimeout());

        Assert.Equal(StatusCode.InvalidArgument, ex.StatusCode);
        // Process-wide, so other tests count too; the claimed length would be about 2 GiB.
        Assert.InRange(GC.GetTotalAllocatedBytes(precise: true) - before, 0, 256L * 1024 * 1024);
        Assert.False(inner.Received.Task.IsCompleted);
    }

    [Fact]
    public async Task FirstFrameTooLargeToSign_Fails()
    {
        // Length 0xFFFFFFFF: more than a byte array can hold.
        byte[] body = [0, 0xFF, 0xFF, 0xFF, 0xFF, .. Encoding.UTF8.GetBytes("m1")];
        var (client, inner) = NewClient();

        await Assert.ThrowsAsync<InvalidOperationException>(
            () => client.SendAsync(Post(PushContent.Frames(body))).WithTimeout());
        Assert.False(inner.Received.Task.IsCompleted);
    }

    [Fact]
    public async Task TransportFailureBeforeTheBodyIsRead_FailsLaterSourceWrites()
    {
        var sourceWrite = new TaskCompletionSource<Exception?>(TaskCreationOptions.RunContinuationsAsynchronously);
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var source = new PushContent(async stream =>
        {
            await stream.WriteAsync(Frame("m1"));
            await stream.FlushAsync();
            await gate.Task;
            try
            {
                // Larger than the pipe's pause threshold, so it would wait for a reader forever.
                await stream.WriteAsync(Frame(new byte[100_000]));
                await stream.FlushAsync();
                sourceWrite.SetResult(null);
            }
            catch (Exception ex)
            {
                sourceWrite.SetResult(ex);
                throw;
            }
        });
        var handler = new SigningDelegatingHandler(Signer.FromHex(TestPrivateKey), new FixedTimeProvider(FixedTime))
        {
            InnerHandler = new FailingHandler()
        };
        using var client = new HttpClient(handler);

        await Assert.ThrowsAsync<HttpRequestException>(() => client.SendAsync(Post(source)).WithTimeout());

        gate.SetResult();
        Assert.IsType<HttpRequestException>(await sourceWrite.Task.WithTimeout());
    }

    [Theory]
    [InlineData("application/proto")] // signed over the whole body
    [InlineData("application/grpc")] // signed over the first frame
    public async Task SignatureHeadersAlreadyOnTheRequest_AreReplaced(string contentType)
    {
        var body = Frame("hello");
        var source = new ByteArrayContent(body);
        source.Headers.TryAddWithoutValidation("Content-Type", contentType);
        var request = Post(source);
        // As call metadata adds them: lower-case names.
        request.Headers.TryAddWithoutValidation(Headers.PublicKey.ToLowerInvariant(), "0x04");
        request.Headers.TryAddWithoutValidation(Headers.Signature.ToLowerInvariant(), "0x00");
        request.Headers.TryAddWithoutValidation(Headers.SignatureTimestamp.ToLowerInvariant(), "1");
        var (client, inner) = NewClient();

        using var response = await client.SendAsync(request).WithTimeout();

        var sent = await inner.Received.Task.WithTimeout();
        Assert.Equal(Signer.FromHex(TestPrivateKey).GetPublicKeyHexPrefixed(),
            Assert.Single(sent.Headers.GetValues(Headers.PublicKey)));
        Assert.Single(sent.Headers.GetValues(Headers.Signature));
        Assert.Equal(FixedTime.ToUnixTimeMilliseconds().ToString(),
            Assert.Single(sent.Headers.GetValues(Headers.SignatureTimestamp)));
        Assert.True(SignatureCovers(sent, body));
    }

    [Fact]
    public async Task CancelledWhileWaitingForTheFirstFrame_FailsTheSourceWrites()
    {
        var sourceWrite = new TaskCompletionSource<Exception?>(TaskCreationOptions.RunContinuationsAsynchronously);
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var source = new PushContent(async stream =>
        {
            await gate.Task;
            try
            {
                // Larger than the pipe's pause threshold, so it would wait for a reader forever.
                await stream.WriteAsync(Frame(new byte[100_000]));
                sourceWrite.SetResult(null);
            }
            catch (Exception ex)
            {
                sourceWrite.SetResult(ex);
                throw;
            }
        });
        var (client, inner) = NewClient();
        using var cts = new CancellationTokenSource();

        var send = client.SendAsync(Post(source), cts.Token);
        cts.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => send.WithTimeout());

        gate.SetResult();
        Assert.IsAssignableFrom<OperationCanceledException>(await sourceWrite.Task.WithTimeout());
        Assert.False(inner.Received.Task.IsCompleted);
    }

    private static bool HasInChain<T>(Exception? ex) where T : Exception
    {
        for (; ex is not null; ex = ex.InnerException)
        {
            if (ex is T)
                return true;
        }
        return false;
    }

    private sealed class SourceFailedException() : Exception("the request content failed");

    /// <summary>
    /// Replaces the request body with its first <c>length</c> bytes, keeping the content type.
    /// </summary>
    private sealed class TruncatingHandler(int length) : DelegatingHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var source = request.Content!;
            var body = await source.ReadAsByteArrayAsync(cancellationToken);
            request.Content = new ByteArrayContent(body[..length]);
            request.Content.Headers.ContentType = source.Headers.ContentType;
            return await base.SendAsync(request, cancellationToken);
        }
    }

    /// <summary>
    /// A transport that fails before reading the request body, as when the connection is refused.
    /// </summary>
    private sealed class FailingHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromException<HttpResponseMessage>(new HttpRequestException("connection refused"));
    }

    /// <summary>
    /// A transport that serializes the body into a stream that fails, then again, as SocketsHttpHandler
    /// does when an HTTP/2 stream is refused.
    /// </summary>
    private sealed class RetryingHandler : HttpMessageHandler
    {
        public TaskCompletionSource<Exception?> FirstAttempt { get; } =
            new(TaskCreationOptions.RunContinuationsAsynchronously);

        public MemoryStream Body { get; } = new();

        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var content = request.Content!;
            Exception? failure = null;
            try
            {
                await content.CopyToAsync(new RefusedStream(), cancellationToken);
            }
            catch (Exception ex)
            {
                failure = ex;
            }
            FirstAttempt.SetResult(failure);

            await content.CopyToAsync(Body, cancellationToken);
            return new HttpResponseMessage(HttpStatusCode.OK);
        }
    }

    private sealed class RefusedStream : MemoryStream
    {
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default) =>
            ValueTask.FromException(new IOException("the HTTP/2 stream was refused"));
    }
}
