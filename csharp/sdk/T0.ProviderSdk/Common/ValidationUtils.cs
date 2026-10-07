using Google.Protobuf;
using Grpc.Core;
using ProtoValidate;

namespace T0.ProviderSdk.Common;

/// <summary>
/// Response validation shared by <see cref="Provider.Validate"/> and
/// <see cref="Provider.ValidationInterceptor"/>, so both give the same answer for the same message.
/// </summary>
internal static class ValidationUtils
{
    // One validator for both: ProtoValidate compiles the rules of each message type once per validator.
    private static readonly Validator Validator = new();

    /// <summary>
    /// Validates <paramref name="message"/> against every one of its rules. Returns null when it
    /// passes.
    /// </summary>
    internal static ResponseValidationFailure? ValidateResponse(IMessage message)
    {
        ValidationResult result;
        try
        {
            result = Validator.Validate(message, failFast: false);
        }
        catch (ProtoValidate.Exceptions.ValidationException e)
        {
            // A rule that could not be compiled (CompilationException) or evaluated
            // (ExecutionException).
            return new ResponseValidationFailure(Unevaluable: true, e.Message);
        }
        return result.IsSuccess ? null : new ResponseValidationFailure(Unevaluable: false, FormatViolations(result));
    }

    // Each violation as "<field path>: <message>", joined by "; ", as every SDK formats them.
    internal static string FormatViolations(ValidationResult result) =>
        string.Join("; ", result.Violations.Select(v => $"{(v.Field is null ? "" : v.Field.GetPath())}: {v.Message}"));
}

/// <summary>
/// Why a response fails validation: its violations, formatted (<see cref="Unevaluable"/> false), or
/// the cause of a rule ProtoValidate could not compile or evaluate (<see cref="Unevaluable"/> true).
/// </summary>
internal sealed record ResponseValidationFailure(bool Unevaluable, string Detail)
{
    /// <summary>
    /// The error every SDK answers with: Internal "response validation failed: &lt;violations&gt;",
    /// or Internal "response validation error: &lt;cause&gt;" for a rule that cannot be evaluated.
    /// </summary>
    internal RpcException ToRpcException() => new(new Status(StatusCode.Internal,
        Unevaluable ? Messages.ResponseValidationError(Detail) : Messages.ResponseInvalid(Detail)));
}
