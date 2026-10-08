namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// The shared Go helper the cross tests run against, cross_test/go_helper/go_helper. It is not in
/// the repository: build it with <c>cd cross_test/go_helper &amp;&amp; go build -o go_helper .</c>
/// </summary>
internal static class GoHelper
{
    // From the test output directory (bin/Debug/net10.0/) to the repository root.
    internal static readonly string CrossTestDirectory = Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "..", "cross_test"));

    internal static readonly string BinaryPath = Path.Combine(CrossTestDirectory, "go_helper", "go_helper");

    internal static readonly string VectorsPath = Path.Combine(CrossTestDirectory, "test_vectors.json");

    // CI sets CI. There a missing helper fails the tests that need it instead of skipping them.
    internal static bool InCI => Environment.GetEnvironmentVariable("CI") != null;

    /// <summary>
    /// The helper's path, for a test marked <see cref="GoHelperFactAttribute"/>. Fails the test when
    /// the helper is missing, which the attribute leaves to happen in CI only.
    /// </summary>
    internal static string Require()
    {
        if (!File.Exists(BinaryPath))
            Assert.Fail($"Go helper binary required in CI but not found at {BinaryPath}");
        return BinaryPath;
    }
}

/// <summary>
/// A fact that needs the Go helper. Without the helper it is skipped, with the reason, outside CI;
/// in CI it runs and fails (<see cref="GoHelper.Require"/>).
/// </summary>
public sealed class GoHelperFactAttribute : FactAttribute
{
    public GoHelperFactAttribute()
    {
        if (!GoHelper.InCI && !File.Exists(GoHelper.BinaryPath))
            Skip = $"the Go helper is not built (no {GoHelper.BinaryPath}); "
                + "build it with: cd cross_test/go_helper && go build -o go_helper .";
    }
}
