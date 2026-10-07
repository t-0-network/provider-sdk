using Google.Protobuf;
using Grpc.Core;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// Lets a handler validate its response against its buf.validate rules before it returns it.
///
/// The <see cref="ValidationInterceptor"/> of a <see cref="T0ProviderServer"/> validates every
/// response anyway, but only after the handler has returned, so the handler never sees the
/// failure. <see cref="Check"/> runs the same validation in the handler's own call frame, where the
/// failure can be caught, logged, or turned into a domain-level answer (for example the
/// <c>Failed</c> arm of a <c>oneof result</c>). Letting the exception propagate gives the caller the
/// same error the interceptor would have given.
/// </summary>
/// <example>
/// <code>
/// public override Task&lt;PayoutResponse&gt; PayOut(PayoutRequest request, ServerCallContext context) =>
///     Task.FromResult(Validate.Check(new PayoutResponse { Accepted = new PayoutResponse.Types.Accepted() }));
/// </code>
/// </example>
public static class Validate
{
    /// <summary>
    /// Validates <paramref name="message"/> against its buf.validate rules.
    /// </summary>
    /// <param name="message">The response to validate.</param>
    /// <typeparam name="T">The message type.</typeparam>
    /// <returns><paramref name="message"/> itself, when it is valid.</returns>
    /// <exception cref="RpcException">
    /// <see cref="StatusCode.Internal"/> "response validation failed: &lt;field path&gt;: &lt;message&gt;",
    /// violations joined by "; ", when the message breaks its rules; <see cref="StatusCode.Internal"/>
    /// "response validation error: &lt;cause&gt;" when ProtoValidate cannot compile or evaluate one of
    /// them. The interceptor answers with the same error.
    /// </exception>
    public static T Check<T>(T message) where T : IMessage
    {
        // A null response is passed on unchanged, as the interceptor does.
        if (message is IMessage response && ValidationUtils.ValidateResponse(response) is { } failure)
            throw failure.ToRpcException();
        return message;
    }
}
