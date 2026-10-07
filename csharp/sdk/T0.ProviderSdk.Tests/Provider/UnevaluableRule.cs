using Buf.Validate;
using Google.Protobuf;
using Google.Protobuf.Reflection;
using ProtoValidate;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// A message with a protovalidate rule that cannot be evaluated: its message-level CEL rule
/// <c>100 / this.divisor &gt; 0</c> divides by zero when <c>divisor</c> is 0, so ProtoValidate throws
/// instead of reporting a violation. Google.Protobuf has no dynamic message, so the class is
/// written the way protoc writes one, over a descriptor built here.
/// </summary>
public sealed class UnevaluableRule : IMessage<UnevaluableRule>
{
    public const string TypeName = "t0.sdk.test.UnevaluableRule";

    public static MessageParser<UnevaluableRule> Parser { get; } = new(() => new UnevaluableRule());

    public static MessageDescriptor Descriptor { get; } = BuildDescriptor();

    MessageDescriptor IMessage.Descriptor => Descriptor;

    public long Divisor { get; set; }

    /// <summary>
    /// The message of the exception ProtoValidate throws for a <see cref="UnevaluableRule"/> with
    /// divisor 0.
    /// </summary>
    public static string Cause()
    {
        try
        {
            new Validator().Validate(new UnevaluableRule(), failFast: false);
        }
        catch (Exception e)
        {
            return e.Message;
        }
        throw new InvalidOperationException($"ProtoValidate evaluated the rule of {TypeName}");
    }

    public void MergeFrom(UnevaluableRule message)
    {
        if (message.Divisor != 0)
            Divisor = message.Divisor;
    }

    public void MergeFrom(CodedInputStream input)
    {
        uint tag;
        while ((tag = input.ReadTag()) != 0)
        {
            if (tag == 8)
                Divisor = input.ReadInt64();
            else
                input.SkipLastField();
        }
    }

    public void WriteTo(CodedOutputStream output)
    {
        if (Divisor != 0)
        {
            output.WriteRawTag(8);
            output.WriteInt64(Divisor);
        }
    }

    public int CalculateSize() => Divisor != 0 ? 1 + CodedOutputStream.ComputeInt64Size(Divisor) : 0;

    public UnevaluableRule Clone() => new() { Divisor = Divisor };

    public bool Equals(UnevaluableRule? other) => other is not null && other.Divisor == Divisor;

    public override bool Equals(object? obj) => Equals(obj as UnevaluableRule);

    public override int GetHashCode() => Divisor.GetHashCode();

    private static MessageDescriptor BuildDescriptor()
    {
        var options = new MessageOptions();
        options.SetExtension(ValidateExtensions.Message, new MessageRules
        {
            Cel = { new Rule { Id = "divisor_divides", Expression = "100 / this.divisor > 0" } },
        });
        var file = new FileDescriptorProto
        {
            Name = "t0/sdk/test/unevaluable_rule.proto",
            Package = "t0.sdk.test",
            Syntax = "proto3",
            Dependency = { ValidateReflection.Descriptor.Name },
            MessageType =
            {
                new DescriptorProto
                {
                    Name = "UnevaluableRule",
                    Field =
                    {
                        new FieldDescriptorProto
                        {
                            Name = "divisor",
                            JsonName = "divisor",
                            Number = 1,
                            Type = FieldDescriptorProto.Types.Type.Int64,
                            Label = FieldDescriptorProto.Types.Label.Optional,
                        },
                    },
                    Options = options,
                },
            },
        };
        return FileDescriptor.FromGeneratedCode(
                file.ToByteArray(),
                [ValidateReflection.Descriptor],
                new GeneratedClrTypeInfo(null, null,
                [
                    new GeneratedClrTypeInfo(typeof(UnevaluableRule), Parser, ["Divisor"], null, null, null, null),
                ]))
            .MessageTypes[0];
    }
}
