using ProtoValidate;

namespace T0.ProviderSdk.Common;

/// <summary>
/// Shared utilities for protovalidate violation formatting.
/// </summary>
internal static class ValidationUtils
{
    // Each violation as "<field path>: <message>", joined by "; ", as every SDK formats them.
    internal static string FormatViolations(ValidationResult result) =>
        string.Join("; ", result.Violations.Select(v => $"{(v.Field is null ? "" : v.Field.GetPath())}: {v.Message}"));
}
