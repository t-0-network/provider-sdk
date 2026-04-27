using Grpc.Core;
using Grpc.Core.Interceptors;
using Google.Protobuf;
using ProtoValidate;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Network;

/// <summary>
/// gRPC client interceptor that validates outgoing requests against
/// buf.validate proto annotations before they are sent.
///
/// Invalid requests are rejected with StatusCode.InvalidArgument before leaving the client.
/// </summary>
public sealed class RequestValidationInterceptor : Interceptor
{
    private static readonly Validator _validator = new();

    public override AsyncUnaryCall<TResponse> AsyncUnaryCall<TRequest, TResponse>(
        TRequest request,
        ClientInterceptorContext<TRequest, TResponse> context,
        AsyncUnaryCallContinuation<TRequest, TResponse> continuation)
    {
        if (request is IMessage message)
        {
            var result = _validator.Validate(message, failFast: false);
            if (!result.IsSuccess)
            {
                throw new RpcException(new Status(StatusCode.InvalidArgument,
                    $"request validation failed: {ValidationUtils.FormatViolations(result)}"));
            }
        }

        return continuation(request, context);
    }

}
