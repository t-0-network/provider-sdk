using System.Reflection;
using Google.Protobuf.WellKnownTypes;
using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.System;

namespace T0.ProviderSdk.Provider;

/// <summary>
/// Auto-registered SystemService implementation. Returns the list of registered
/// gRPC service FQNs, the server's current wall-clock time, the SDK version
/// (read from the assembly's <see cref="AssemblyInformationalVersionAttribute"/>,
/// which MSBuild populates from the <c>&lt;Version&gt;</c> element in the .csproj),
/// and the SDK ecosystem identifier.
/// </summary>
internal sealed class SystemServiceImpl : SystemService.SystemServiceBase
{
    private static readonly string CachedSdkVersion = LoadSdkVersion();

    private readonly IReadOnlyList<string> _services;

    public SystemServiceImpl(IReadOnlyList<string> services)
    {
        _services = services;
    }

    public override Task<HealthResponse> Health(HealthRequest request, ServerCallContext context)
    {
        var response = new HealthResponse
        {
            CurrentTime = Timestamp.FromDateTime(DateTime.UtcNow),
            SdkVersion = CachedSdkVersion,
            SdkEcosystem = SdkEcosystem.Csharp,
        };
        response.Services.AddRange(_services);
        return Task.FromResult(response);
    }

    private static string LoadSdkVersion()
    {
        var raw = typeof(SystemServiceImpl).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion;
        if (string.IsNullOrEmpty(raw))
            return "unknown";
        var plus = raw.IndexOf('+');
        return plus >= 0 ? raw[..plus] : raw;
    }
}
