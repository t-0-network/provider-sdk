using Grpc.Core;
using Grpc.Core.Interceptors;
using Google.Protobuf;
using ProtoValidate;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// gRPC server interceptor that validates outgoing responses
/// against buf.validate proto annotations.
///
/// Invalid responses are rejected with StatusCode.Internal (provider implementation bug).
/// </summary>
public sealed class ValidationInterceptor : Interceptor
{
    private static readonly Validator _validator = new();

    public override async Task<TResponse> UnaryServerHandler<TRequest, TResponse>(
        TRequest request,
        ServerCallContext context,
        UnaryServerMethod<TRequest, TResponse> continuation)
    {
        var response = await continuation(request, context);

        if (response is IMessage responseMessage)
        {
            var result = _validator.Validate(responseMessage, failFast: false);
            if (!result.IsSuccess)
            {
                throw new RpcException(new Status(StatusCode.Internal,
                    $"response validation failed: {ValidationUtils.FormatViolations(result)}"));
            }
        }

        return response;
    }

}
