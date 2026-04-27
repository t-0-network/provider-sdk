using ProtoValidate;

namespace T0.ProviderSdk.Common;

/// <summary>
/// Shared utilities for protovalidate violation formatting.
/// </summary>
internal static class ValidationUtils
{
    internal static string FormatViolations(ValidationResult result) =>
        string.Join("; ", result.Violations.Select(v => v.ToString()));
}
