using System.Collections.Concurrent;
using System.Globalization;
using System.Text;
using Buf.Validate;
using Google.Protobuf;
using Google.Protobuf.Reflection;
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

    // ProtoValidate applies a predefined rule (an extension of a buf.validate *Rules message, such
    // as one an application declares in its own protos) only when the file declaring it is in
    // ValidatorOptions.FileDescriptors. So a message whose file or imports declare one gets a
    // validator given those files, one per message file; every other message gets the one above.
    private static readonly ConcurrentDictionary<FileDescriptor, Validator> Validators = new();

    private static Validator ValidatorFor(FileDescriptor file) => Validators.GetOrAdd(file, f =>
    {
        var imports = new List<FileDescriptor>();
        AddImports(f, imports);
        // Only the *Rules messages of validate.proto can be extended.
        var ruleFiles = imports.Where(i => i.Extensions.UnorderedExtensions
            .Any(e => e.ExtendeeType.File == ValidateReflection.Descriptor)).ToList();
        // As in the validator above, the rules of a message type compile when it is first validated,
        // so a rule of another message in these files that cannot compile does not fail this one.
        return ruleFiles.Count == 0 ? Validator : new Validator(new ValidatorOptions
        {
            FileDescriptors = ruleFiles,
            DisableLazy = false,
            PreLoadDescriptors = false,
        });
    });

    // file and every file it imports, directly or not.
    private static void AddImports(FileDescriptor file, List<FileDescriptor> files)
    {
        if (files.Contains(file))
            return;
        files.Add(file);
        foreach (var dependency in file.Dependencies)
            AddImports(dependency, files);
    }

    /// <summary>
    /// Validates <paramref name="message"/> against every one of its rules. Returns null when it
    /// passes.
    /// </summary>
    internal static ResponseValidationFailure? ValidateResponse(IMessage message)
    {
        ValidationResult result;
        try
        {
            result = ValidatorFor(message.Descriptor.File).Validate(message, failFast: false);
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
        string.Join("; ", result.Violations.Select(v => $"{FieldPathString(v.Field)}: {v.Message}"));

    /// <summary>
    /// The field path of a violation as every SDK writes it (field_path_cases in
    /// cross_test/test_vectors.json): field names joined by ".", each list index or map key in
    /// brackets after its field name, and a string key in double quotes with JSON string escaping.
    /// An empty path is "".
    /// </summary>
    internal static string FieldPathString(FieldPath? path)
    {
        if (path is null)
            return "";
        var sb = new StringBuilder();
        for (var i = 0; i < path.Elements.Count; i++)
        {
            var element = path.Elements[i];
            if (i > 0)
                sb.Append('.');
            sb.Append(element.FieldName);
            switch (element.SubscriptCase)
            {
                case FieldPathElement.SubscriptOneofCase.Index:
                    sb.Append('[').Append(element.Index.ToString(CultureInfo.InvariantCulture)).Append(']');
                    break;
                case FieldPathElement.SubscriptOneofCase.BoolKey:
                    sb.Append(element.BoolKey ? "[true]" : "[false]");
                    break;
                case FieldPathElement.SubscriptOneofCase.IntKey:
                    sb.Append('[').Append(element.IntKey.ToString(CultureInfo.InvariantCulture)).Append(']');
                    break;
                case FieldPathElement.SubscriptOneofCase.UintKey:
                    sb.Append('[').Append(element.UintKey.ToString(CultureInfo.InvariantCulture)).Append(']');
                    break;
                case FieldPathElement.SubscriptOneofCase.StringKey:
                    AppendJsonString(sb.Append('['), element.StringKey).Append(']');
                    break;
            }
        }
        return sb.ToString();
    }

    // s in double quotes with JSON string escaping (RFC 8259): \" and \\, \b, \f, \n, \r and \t,
    // every other character below U+0020 as \u00xx, and every other character as it is (DEL and all
    // non-ASCII included).
    private static StringBuilder AppendJsonString(StringBuilder sb, string s)
    {
        sb.Append('"');
        foreach (var c in s)
        {
            switch (c)
            {
                case '"': sb.Append("\\\""); break;
                case '\\': sb.Append("\\\\"); break;
                case '\b': sb.Append("\\b"); break;
                case '\f': sb.Append("\\f"); break;
                case '\n': sb.Append("\\n"); break;
                case '\r': sb.Append("\\r"); break;
                case '\t': sb.Append("\\t"); break;
                default:
                    if (c < 0x20)
                        sb.Append("\\u").Append(((int)c).ToString("x4", CultureInfo.InvariantCulture));
                    else
                        sb.Append(c);
                    break;
            }
        }
        return sb.Append('"');
    }
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
