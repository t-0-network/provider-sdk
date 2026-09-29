using Grpc.Core;
using Grpc.Core.Interceptors;

namespace T0.ProviderSdk.Network;

/// <summary>
/// gRPC client interceptor that gives each call without a deadline a default one, picked by the
/// call's type: unary calls get <see cref="NetworkClientOptions.Timeout"/>; client-streaming and
/// server-streaming calls get <see cref="NetworkClientOptions.StreamTimeout"/>, or no deadline
/// when it is null. A deadline set in the call's <see cref="CallOptions"/> is kept.
///
/// The deadline bounds the whole call, from the start of the request to the end of the response,
/// and is sent to the server as <c>grpc-timeout</c>.
///
/// Bidirectional (duplex) streams are not supported: a duplex call throws an
/// <see cref="RpcException"/> with <see cref="StatusCode.Unimplemented"/> before anything is sent.
/// </summary>
public sealed class DefaultDeadlineInterceptor : Interceptor
{
    private readonly TimeSpan? _unaryTimeout;
    private readonly TimeSpan? _streamTimeout;

    /// <summary>
    /// Takes the timeouts from <paramref name="options"/> as they are now.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">
    /// A timeout is zero or negative, other than <see cref="System.Threading.Timeout.InfiniteTimeSpan"/>.
    /// </exception>
    public DefaultDeadlineInterceptor(NetworkClientOptions options)
    {
        ArgumentNullException.ThrowIfNull(options);
        _unaryTimeout = Validate(options.Timeout, nameof(NetworkClientOptions.Timeout));
        _streamTimeout = options.StreamTimeout is { } stream
            ? Validate(stream, nameof(NetworkClientOptions.StreamTimeout))
            : null;
    }

    public override TResponse BlockingUnaryCall<TRequest, TResponse>(
        TRequest request,
        ClientInterceptorContext<TRequest, TResponse> context,
        BlockingUnaryCallContinuation<TRequest, TResponse> continuation) =>
        continuation(request, WithDefaultDeadline(context));

    public override AsyncUnaryCall<TResponse> AsyncUnaryCall<TRequest, TResponse>(
        TRequest request,
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncUnaryCallContinuation<TRequest, TResponse> continuation) =>
        continuation(request, WithDefaultDeadline(context));

    public override AsyncServerStreamingCall<TResponse> AsyncServerStreamingCall<TRequest, TResponse>(
        TRequest request,
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncServerStreamingCallContinuation<TRequest, TResponse> continuation) =>
        continuation(request, WithDefaultDeadline(context));

    public override AsyncClientStreamingCall<TRequest, TResponse> AsyncClientStreamingCall<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncClientStreamingCallContinuation<TRequest, TResponse> continuation) =>
        continuation(WithDefaultDeadline(context));

    // Bidirectional streams are not supported in any SDK: fail before anything is sent rather
    // than partway through the call, or after it waited unsent for a first message.
    public override AsyncDuplexStreamingCall<TRequest, TResponse> AsyncDuplexStreamingCall<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncDuplexStreamingCallContinuation<TRequest, TResponse> continuation) =>
        throw new RpcException(new Status(StatusCode.Unimplemented, "bidirectional streams are not supported"));

    private ClientInterceptorContext<TRequest, TResponse> WithDefaultDeadline<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context)
        where TRequest : class
        where TResponse : class
    {
        if (context.Options.Deadline is not null)
            return context;

        var timeout = context.Method.Type == MethodType.Unary ? _unaryTimeout : _streamTimeout;
        if (timeout is null)
            return context;

        return new ClientInterceptorContext<TRequest, TResponse>(
            context.Method, context.Host, context.Options.WithDeadline(DateTime.UtcNow + timeout.Value));
    }

    // InfiniteTimeSpan means no deadline.
    private static TimeSpan? Validate(TimeSpan timeout, string name)
    {
        if (timeout == Timeout.InfiniteTimeSpan)
            return null;
        if (timeout <= TimeSpan.Zero)
            throw new ArgumentOutOfRangeException(name, timeout, $"{name} must be greater than zero.");
        return timeout;
    }
}
