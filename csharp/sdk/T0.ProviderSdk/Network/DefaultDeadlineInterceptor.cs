using Grpc.Core;
using Grpc.Core.Interceptors;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Network;

/// <summary>
/// gRPC client interceptor that gives a call without a deadline a default one:
/// <see cref="NetworkClientOptions.Timeout"/> for unary calls,
/// <see cref="NetworkClientOptions.StreamTimeout"/> for client and server streams. Bidirectional
/// streams fail with <see cref="StatusCode.Unimplemented"/> before anything is sent.
/// </summary>
/// <remarks>See docs/STREAMING.md.</remarks>
internal sealed class DefaultDeadlineInterceptor : Interceptor
{
    private readonly TimeSpan _unaryTimeout;
    private readonly TimeSpan _streamTimeout;

    /// <summary>
    /// Takes the timeouts from <paramref name="options"/> as they are now.
    /// </summary>
    public DefaultDeadlineInterceptor(NetworkClientOptions options)
    {
        ArgumentNullException.ThrowIfNull(options);
        _unaryTimeout = options.Timeout;
        _streamTimeout = options.StreamTimeout;
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

    // A policy, not a signing limit: the network does not accept bidirectional streams (#370).
    public override AsyncDuplexStreamingCall<TRequest, TResponse> AsyncDuplexStreamingCall<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncDuplexStreamingCallContinuation<TRequest, TResponse> continuation) =>
        throw new RpcException(new Status(StatusCode.Unimplemented, Messages.BidiNotSupported));

    // The caller's own deadline replaces the default, longer or shorter.
    private ClientInterceptorContext<TRequest, TResponse> WithDefaultDeadline<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context)
        where TRequest : class
        where TResponse : class
    {
        if (context.Options.Deadline is not null)
            return context;

        var timeout = context.Method.Type == MethodType.Unary ? _unaryTimeout : _streamTimeout;
        return new ClientInterceptorContext<TRequest, TResponse>(
            context.Method, context.Host, context.Options.WithDeadline(DateTime.UtcNow + timeout));
    }
}
