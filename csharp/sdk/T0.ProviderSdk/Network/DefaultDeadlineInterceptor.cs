using Grpc.Core;
using Grpc.Core.Interceptors;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Network;

/// <summary>
/// gRPC client interceptor that gives a call without a deadline a default one: the timeout option
/// of its method, else <see cref="NetworkClientOptions.Timeout"/> for unary calls and
/// <see cref="NetworkClientOptions.StreamTimeout"/> for client and server streams. Bidirectional
/// streams fail with <see cref="StatusCode.Unimplemented"/> before anything is sent.
/// </summary>
/// <remarks>See docs/STREAMING.md.</remarks>
internal sealed class DefaultDeadlineInterceptor : Interceptor
{
    private readonly TimeSpan _unaryTimeout;
    private readonly TimeSpan _streamTimeout;
    private readonly IReadOnlyDictionary<string, TimeSpan> _methodTimeouts;

    /// <summary>
    /// Takes the timeouts from <paramref name="options"/> as they are now, and the timeout option of
    /// each method that has one from <paramref name="methodTimeouts"/>, by the method's full name
    /// ("/pkg.Service/Method").
    /// </summary>
    public DefaultDeadlineInterceptor(NetworkClientOptions options, IReadOnlyDictionary<string, TimeSpan> methodTimeouts)
    {
        ArgumentNullException.ThrowIfNull(options);
        ArgumentNullException.ThrowIfNull(methodTimeouts);
        _unaryTimeout = options.Timeout;
        _streamTimeout = options.StreamTimeout;
        _methodTimeouts = methodTimeouts;
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

    // The caller's own deadline replaces the default, longer or shorter. The method's timeout option
    // replaces the client's timeout, longer or shorter: the network answers within it.
    private ClientInterceptorContext<TRequest, TResponse> WithDefaultDeadline<TRequest, TResponse>(
        ClientInterceptorContext<TRequest, TResponse> context)
        where TRequest : class
        where TResponse : class
    {
        if (context.Options.Deadline is not null)
            return context;

        if (!_methodTimeouts.TryGetValue(context.Method.FullName, out var timeout))
            timeout = context.Method.Type == MethodType.Unary ? _unaryTimeout : _streamTimeout;
        return new ClientInterceptorContext<TRequest, TResponse>(
            context.Method, context.Host, context.Options.WithDeadline(DateTime.UtcNow + timeout));
    }
}
