using System.Reflection;
using Grpc.Core;
using Grpc.Health.V1;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// The health service the transport mounts on every server it builds — see
/// <c>docs/HEALTH_SERVICE.md</c>.
///
/// <para>Reports SERVING for the services registered on this server and NotFound
/// for anything else. The set is frozen at construction; nothing is computed per
/// request. <c>Watch</c> is left at <see cref="Health.HealthBase"/>'s
/// UNIMPLEMENTED: it is server-streaming, and the body-hash signature scheme
/// these servers run behind has no story for streams.</para>
/// </summary>
internal sealed class HealthServiceImpl : Health.HealthBase
{
    /// <summary>
    /// Headers carrying the identity of the SDK answering the probe. They ride on
    /// the health response and nowhere else: <c>HealthCheckResponse</c> has a
    /// single status field and <c>Check</c> names its service in the request, so
    /// the contract itself has no room for this.
    /// </summary>
    internal const string SdkEcosystemHeader = "t0-sdk-ecosystem";
    internal const string SdkVersionHeader = "t0-sdk-version";

    private const string SdkEcosystem = "csharp";

    private static readonly HealthCheckResponse Serving = new()
    {
        Status = HealthCheckResponse.Types.ServingStatus.Serving,
    };

    private static readonly string CachedSdkVersion = LoadSdkVersion();

    private readonly HashSet<string> _registered;

    public HealthServiceImpl(IEnumerable<string> services)
    {
        _registered = new HashSet<string>(services);
    }

    public override async Task<HealthCheckResponse> Check(HealthCheckRequest request, ServerCallContext context)
    {
        await context.WriteResponseHeadersAsync(new Metadata
        {
            { SdkEcosystemHeader, SdkEcosystem },
            { SdkVersionHeader, CachedSdkVersion },
        });

        // An empty service name asks about the process as a whole, which is up if
        // this handler is running at all.
        if (request.Service.Length > 0 && !_registered.Contains(request.Service))
            throw new RpcException(new Status(StatusCode.NotFound, $"unknown service '{request.Service}'"));

        return Serving;
    }

    /// <summary>
    /// Reads <see cref="AssemblyInformationalVersionAttribute"/>, which MSBuild
    /// populates from the <c>&lt;Version&gt;</c> element in the .csproj. Any
    /// <c>+gitsha</c> suffix (set when <c>IncludeSourceRevisionInInformationalVersion</c>
    /// is on) is stripped so the result is plain semver. No second source of
    /// truth — the csproj <c>&lt;Version&gt;</c> is the only knob.
    /// </summary>
    internal static string LoadSdkVersion()
    {
        var raw = typeof(HealthServiceImpl).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion;
        if (string.IsNullOrEmpty(raw))
            return "unknown";
        var plus = raw.IndexOf('+');
        return plus >= 0 ? raw[..plus] : raw;
    }
}
