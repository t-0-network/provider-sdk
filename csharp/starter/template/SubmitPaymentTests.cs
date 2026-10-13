using System.Text.RegularExpressions;
using Grpc.Core;
using T0.ProviderSdk.Api.Ivms101.V1.Ivms;
using T0.ProviderSdk.Api.Tzero.V1.Payment;
using MyProvider.Services;
using Xunit;
using Decimal = T0.ProviderSdk.Api.Tzero.V1.Common.Decimal;

namespace MyProvider;

/// <summary>
/// The payment submission demo against a recording fake invoker. No server, no sandbox.
/// </summary>
public class SubmitPaymentTests
{
    private static readonly Regex IbanPattern = new("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$", RegexOptions.Compiled);
    private const string SandboxIban = "GB29NWBK60161331926819"; // the sandbox IBAN the demo pins

    /// <summary>Records the CreatePayment request and replies with a fixed response or error.</summary>
    private sealed class RecordingInvoker : CallInvoker
    {
        public int Calls { get; private set; }
        public CreatePaymentRequest? Captured { get; private set; }
        public CreatePaymentResponse Response { get; init; } = new();
        public Func<CreatePaymentRequest, Exception>? ThrowOnCall { get; init; }

        public override TResponse BlockingUnaryCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request) =>
            throw new NotSupportedException();

        public override AsyncUnaryCall<TResponse> AsyncUnaryCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request)
        {
            Calls++;
            var paymentRequest = (CreatePaymentRequest)(object)request;
            Captured = paymentRequest;
            if (ThrowOnCall is { } f)
                throw f(paymentRequest);
            return new AsyncUnaryCall<TResponse>(
                Task.FromResult((TResponse)(object)Response),
                Task.FromResult(new Metadata()),
                () => Status.DefaultSuccess,
                () => new Metadata(),
                () => { });
        }

        public override AsyncServerStreamingCall<TResponse> AsyncServerStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options, TRequest request) =>
            throw new NotSupportedException();

        public override AsyncClientStreamingCall<TRequest, TResponse> AsyncClientStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options) =>
            throw new NotSupportedException();

        public override AsyncDuplexStreamingCall<TRequest, TResponse> AsyncDuplexStreamingCall<TRequest, TResponse>(
            Method<TRequest, TResponse> method, string? host, CallOptions options) =>
            throw new NotSupportedException();
    }

    private static async Task<(CreatePaymentRequest? Request, string Out, string Error)> RunAsync(
        CreatePaymentResponse? response = null, Exception? error = null)
    {
        var invoker = new RecordingInvoker
        {
            Response = response ?? new CreatePaymentResponse(),
            ThrowOnCall = error is null ? null : _ => error,
        };
        var client = new NetworkService.NetworkServiceClient(invoker);

        var previousOut = Console.Out;
        var previousError = Console.Error;
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        try
        {
            Console.SetOut(stdout);
            Console.SetError(stderr);
            await SubmitPayment.SubmitAsync(client);
        }
        finally
        {
            Console.SetOut(previousOut);
            Console.SetError(previousError);
        }

        return (invoker.Captured, stdout.ToString(), stderr.ToString());
    }

    [Fact]
    public async Task Builds_one_schema_valid_request_and_logs_accepted()
    {
        // The response a real accept carries: Accepted marks its amounts required.
        var accepted = new CreatePaymentResponse
        {
            Accepted = new CreatePaymentResponse.Types.Accepted
            {
                PaymentId = 42,
                SettlementAmount = new Decimal { Unscaled = 1250, Exponent = -2 }, // 12.50 USD
                PayoutAmount = new Decimal { Unscaled = 10, Exponent = 0 },
                PayoutProviderId = 1
            }
        };

        var (request, stdout, _) = await RunAsync(accepted);

        Assert.NotNull(request);
        Assert.NotEqual(Guid.Empty, Guid.Parse(request.PaymentClientId)); // parses, so it is a UUID

        Assert.Equal("GBP", request.Currency);
        Assert.Equal(10, (int)request.Amount.PayOutAmount.Unscaled);
        Assert.Equal(0, request.Amount.PayOutAmount.Exponent);

        // The same checks the network's buf.validate rules apply to the SEPA demo data.
        Assert.Matches(IbanPattern, request.PaymentDetails.Sepa.Iban);
        Assert.InRange(request.PaymentDetails.Sepa.Iban.Length, 15, 34);
        Assert.Equal(SandboxIban, request.PaymentDetails.Sepa.Iban);
        Assert.Equal("Max Mustermann", request.PaymentDetails.Sepa.BeneficiaryName);

        var travel = request.TravelRuleData;
        Assert.NotNull(travel); // required by the contract
        var originator = Assert.Single(travel.Originator);
        var beneficiary = Assert.Single(travel.Beneficiary);
        AssertLegalName(originator, "Doe", "John");
        AssertLegalName(beneficiary, "Mustermann", "Max");
        // The SEPA beneficiary name and the IVMS101 beneficiary describe the same person.
        Assert.Equal(
            request.PaymentDetails.Sepa.BeneficiaryName,
            $"{beneficiary.NaturalPerson.Name.NameIdentifiers[0].SecondaryIdentifier} {beneficiary.NaturalPerson.Name.NameIdentifiers[0].PrimaryIdentifier}");

        Assert.Contains("Payment accepted", stdout);
        Assert.Contains("id=42", stdout);
    }

    private static void AssertLegalName(Person person, string primary, string secondary)
    {
        var identifier = Assert.Single(person.NaturalPerson.Name.NameIdentifiers);
        Assert.Equal(NaturalPersonNameTypeCode.Legl, identifier.NameIdentifierType);
        Assert.Equal(primary, identifier.PrimaryIdentifier);
        Assert.Equal(secondary, identifier.SecondaryIdentifier);
    }

    [Fact]
    public async Task Failure_result_is_logged()
    {
        var response = new CreatePaymentResponse
        {
            Failure = new CreatePaymentResponse.Types.Failure
            {
                Reason = CreatePaymentResponse.Types.Failure.Types.Reason.QuoteNotFound
            }
        };

        var (_, stdout, _) = await RunAsync(response);

        Assert.Contains("Payment failed", stdout);
        Assert.Contains("QuoteNotFound", stdout); // the generated C# enum name
    }

    [Fact]
    public async Task Unset_result_is_reported_as_unknown()
    {
        // A response without a result (a deprecated or unset oneof arm) is unknown,
        // not a crash and not a silent success.
        var (_, stdout, _) = await RunAsync(new CreatePaymentResponse());

        Assert.Contains("Unknown result type", stdout);
    }

    [Fact]
    public async Task Rpc_error_is_one_stderr_line_and_does_not_propagate()
    {
        var (_, stdout, stderr) = await RunAsync(
            error: new RpcException(new Status(StatusCode.Unavailable, "connection refused")));

        var lines = stderr.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        Assert.Single(lines); // one line, no stack trace
        Assert.StartsWith("Error submitting payment:", lines[0]);
        Assert.Equal("", stdout.Trim());
    }

    [Fact]
    public async Task Rpc_error_does_not_retry()
    {
        var invoker = new RecordingInvoker { ThrowOnCall = _ => new RpcException(new Status(StatusCode.Unavailable, "down")) };
        var client = new NetworkService.NetworkServiceClient(invoker);

        var previousError = Console.Error;
        try
        {
            Console.SetError(new StringWriter());
            await SubmitPayment.SubmitAsync(client);
        }
        finally
        {
            Console.SetError(previousError);
        }

        Assert.Equal(1, invoker.Calls);
    }
}
