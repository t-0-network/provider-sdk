using Grpc.Core;
using Grpc.Health.V1;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Api.Tzero.V1.PaymentIntent;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests.Provider;

/// <summary>
/// MapService serves a handler of any generated service behind the signature middleware, reports
/// the service in health, and creates the handler from Services. Here: the two roles of the
/// payment intent flow, whose handlers take the PaymentIntentService client.
/// </summary>
public class MapServiceTests
{
    // Same dev keypair used in CrossServerTests.
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    [Fact]
    public async Task Handlers_AreServed_CreatedFromServices_AndReportedInHealth()
    {
        var port = TestPorts.FindFreePort();
        var signer = Signer.FromHex(PrivateKey);
        var config = new T0Config
        {
            ProviderPrivateKey = PrivateKey,
            NetworkPublicKey = PublicKey,
            Port = port,
            TZeroEndpoint = "http://localhost:1",
        };
        var server = new T0ProviderServer(config, signer);
        server.Services.AddSingleton(NetworkClient.Create(
            new NetworkClientOptions { BaseUrl = "http://localhost:1" }, signer,
            invoker => new PaymentIntentService.PaymentIntentServiceClient(invoker)));
        server.MapService<PayInHandler>().MapService<BeneficiaryHandler>();

        await TestServer.RunAsync(server, port, async () =>
        {
            var options = new NetworkClientOptions { BaseUrl = $"http://127.0.0.1:{port}" };

            var payIn = NetworkClient.Create(options, signer, invoker => new PayInProviderService.PayInProviderServiceClient(invoker));
            var details = await payIn.GetPaymentDetailsAsync(new GetPaymentDetailsRequest { PaymentIntentId = 7 });
            Assert.Equal(GetPaymentDetailsResponse.ResultOneofCase.Details, details.ResultCase);

            var beneficiary = NetworkClient.Create(options, signer, invoker => new BeneficiaryService.BeneficiaryServiceClient(invoker));
            Assert.NotNull(await beneficiary.PaymentIntentUpdateAsync(new PaymentIntentUpdateRequest { PaymentIntentId = 7 }));

            var health = NetworkClient.Create(options, signer, invoker => new Health.HealthClient(invoker));
            foreach (var service in new[] { PayInProviderService.Descriptor.FullName, BeneficiaryService.Descriptor.FullName })
            {
                var response = await health.CheckAsync(new HealthCheckRequest { Service = service });
                Assert.Equal(HealthCheckResponse.Types.ServingStatus.Serving, response.Status);
            }
        });
    }

    public sealed class PayInHandler(PaymentIntentService.PaymentIntentServiceClient paymentIntentClient)
        : PayInProviderService.PayInProviderServiceBase
    {
        public override Task<GetPaymentDetailsResponse> GetPaymentDetails(
            GetPaymentDetailsRequest request, ServerCallContext context)
        {
            ArgumentNullException.ThrowIfNull(paymentIntentClient);
            return Task.FromResult(new GetPaymentDetailsResponse { Details = new GetPaymentDetailsResponse.Types.Details() });
        }
    }

    public sealed class BeneficiaryHandler(PaymentIntentService.PaymentIntentServiceClient paymentIntentClient)
        : BeneficiaryService.BeneficiaryServiceBase
    {
        public override Task<PaymentIntentUpdateResponse> PaymentIntentUpdate(
            PaymentIntentUpdateRequest request, ServerCallContext context)
        {
            ArgumentNullException.ThrowIfNull(paymentIntentClient);
            return Task.FromResult(new PaymentIntentUpdateResponse());
        }
    }
}
