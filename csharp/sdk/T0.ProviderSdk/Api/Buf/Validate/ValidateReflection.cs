// Minimal stub for buf.validate descriptor dependency.
// The generated protobuf code references this for file descriptor registration.
// A full protovalidate C# package does not exist on NuGet, so we provide
// just enough to satisfy the descriptor dependency chain.

using Pbr = Google.Protobuf.Reflection;

namespace Buf.Validate;

public static class ValidateReflection
{
    private static Pbr::FileDescriptor? _descriptor;

    public static Pbr::FileDescriptor Descriptor =>
        _descriptor ??= Pbr::FileDescriptor.FromGeneratedCode(
            System.Convert.FromBase64String(
                // Minimal self-describing proto: syntax="proto3", package="buf.validate", file="buf/validate/validate.proto"
                "ChtidWYvdmFsaWRhdGUvdmFsaWRhdGUucHJvdG8SDGJ1Zi52YWxpZGF0ZWIGcHJvdG8z"),
            [],
            new Pbr::GeneratedClrTypeInfo(null, null, null));
}
