using System.Reflection;
using Google.Protobuf.Reflection;

namespace T0.ProviderSdk.Common;

/// <summary>
/// Finds the generated gRPC service of a client or handler type. Generated clients and handler
/// bases are nested in the service's static class, which has a static <c>Descriptor</c>.
/// </summary>
internal static class GeneratedService
{
    // The service whose generated client or handler base is type or a base type of it, else null.
    internal static ServiceDescriptor? DescriptorOf(Type type)
    {
        for (var t = type; t is not null; t = t.BaseType)
        {
            if (t.DeclaringType?.GetProperty("Descriptor", BindingFlags.Public | BindingFlags.Static)
                    ?.GetValue(null) is ServiceDescriptor service)
                return service;
        }
        return null;
    }
}
