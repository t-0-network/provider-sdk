using Grpc.Core;
using Grpc.Core.Interceptors;
using Google.Protobuf;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using T0.ProviderSdk.Common;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// gRPC server interceptor that validates outgoing responses
/// against buf.validate proto annotations.
///
/// Invalid responses are rejected with StatusCode.Internal (provider implementation bug). So is a
/// response with a rule ProtoValidate cannot compile or evaluate ("response validation error:
/// &lt;cause&gt;"). On a <see cref="T0ProviderServer"/>, each one is also logged as a single error line
/// with rpc_method, response_type, violations (or error) and sdk_version.
///
/// <see cref="Validate.Check"/> runs the same validation inside a handler, with the same error.
/// </summary>
public sealed class ValidationInterceptor : Interceptor
{
    private readonly ILogger _logger;
    private readonly string _sdkVersion;

    /// <summary>
    /// Creates an interceptor that logs nothing.
    /// </summary>
    public ValidationInterceptor() : this(NullLogger.Instance, null) { }

    // T0ProviderServer passes its logger and the version set by WithSdkVersion, so the log names
    // the version the health headers report.
    internal ValidationInterceptor(ILogger logger, string? sdkVersion)
    {
        _logger = logger;
        _sdkVersion = sdkVersion ?? HealthServiceImpl.CachedSdkVersion;
    }

    public override async Task<TResponse> UnaryServerHandler<TRequest, TResponse>(
        TRequest request,
        ServerCallContext context,
        UnaryServerMethod<TRequest, TResponse> continuation)
    {
        var response = await continuation(request, context);

        if (response is IMessage responseMessage
            && ValidationUtils.ValidateResponse(responseMessage) is { } failure)
        {
            if (failure.Unevaluable)
                _logger.LogError(
                    "response validation error rpc_method={rpc_method} response_type={response_type} error={error} sdk_version={sdk_version}",
                    context.Method.TrimStart('/'), responseMessage.Descriptor.FullName, failure.Detail, _sdkVersion);
            else
                _logger.LogError(
                    "response validation failed rpc_method={rpc_method} response_type={response_type} violations={violations} sdk_version={sdk_version}",
                    context.Method.TrimStart('/'), responseMessage.Descriptor.FullName, failure.Detail, _sdkVersion);
            throw failure.ToRpcException();
        }

        return response;
    }

}
