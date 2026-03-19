namespace T0.ProviderStarter;

/// <summary>
/// Template file contents for project scaffolding.
/// Files are stored inline to avoid embedded resource naming issues with
/// dot-prefixed files and nested directories.
/// </summary>
public static class TemplateFiles
{
    private static readonly Dictionary<string, string> AllFiles = new()
    {
        [".env.example"] = EnvExample,
        [".gitignore"] = GitIgnore,
        ["{{PROJECT_NAME}}.csproj"] = CsProj,
        ["Program.cs"] = ProgramCs,
        ["appsettings.json"] = AppSettings,
        ["Dockerfile"] = Dockerfile,
        ["Services/PaymentHandler.cs"] = PaymentHandler,
        ["Services/QuotePublisher.cs"] = QuotePublisher,
        ["Services/GetQuote.cs"] = GetQuote,
        ["Services/SubmitPayment.cs"] = SubmitPayment,
    };

    public static IReadOnlyDictionary<string, string> All => AllFiles;

    private const string EnvExample =
        """
        # T-0 Network Provider Configuration
        # Copy this file to .env and fill in your values

        # Your provider's private key (generated automatically by the starter)
        PROVIDER_PRIVATE_KEY=

        # T-0 Network's public key
        NETWORK_PUBLIC_KEY=0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b

        # T-0 Network API endpoint
        TZERO_ENDPOINT=https://api-sandbox.t-0.network

        # Port for your provider server
        PORT=8080
        """;

    private const string GitIgnore =
        """
        bin/
        obj/
        .env
        *.user
        .vs/
        .idea/
        """;

    private const string CsProj =
        """
        <Project Sdk="Microsoft.NET.Sdk.Web">

          <PropertyGroup>
            <TargetFramework>net10.0</TargetFramework>
            <ImplicitUsings>enable</ImplicitUsings>
            <Nullable>enable</Nullable>
            <RootNamespace>{{PROJECT_NAME_PASCAL}}</RootNamespace>
          </PropertyGroup>

          <ItemGroup>
            <PackageReference Include="T0.ProviderSdk" Version="1.1.8" />
            <PackageReference Include="DotNetEnv" Version="3.1.1" />
          </ItemGroup>

        </Project>
        """;

    private const string ProgramCs =
        """
        using T0.ProviderSdk;
        using T0.ProviderSdk.Crypto;
        using T0.ProviderSdk.Network;

        DotNetEnv.Env.Load();
        var config = T0Config.FromEnvironment();
        var signer = Signer.FromHex(config.ProviderPrivateKey);
        Console.WriteLine($"Provider public key: {signer.GetPublicKeyHexPrefixed()}");

        // TODO: Step 1.2 Share the generated public key from .env with the T-0 team

        var networkClient = NetworkClient.CreateNetworkServiceClient(config.TZeroEndpoint, signer);

        var server = new T0ProviderServer(config, signer);
        server.MapPaymentService<{{PROJECT_NAME_PASCAL}}.Services.PaymentHandler>(networkClient);
        server.AddHostedService<{{PROJECT_NAME_PASCAL}}.Services.QuotePublisher>();

        // TODO: Step 1.4 Verify that quotes for target currency are successfully received
        _ = {{PROJECT_NAME_PASCAL}}.Services.GetQuote.FetchAsync(networkClient);

        Console.WriteLine($"Step 1.1: Provider server initialized on port {config.Port}");
        await server.RunAsync();
        """;

    private const string AppSettings =
        """
        {
          "Logging": {
            "LogLevel": {
              "Default": "Information",
              "Microsoft.AspNetCore": "Warning"
            }
          },
          "Kestrel": {
            "EndpointDefaults": {
              "Protocols": "Http2"
            }
          }
        }
        """;

    private const string Dockerfile =
        """
        FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
        WORKDIR /src

        COPY *.csproj ./
        RUN dotnet restore

        COPY . .
        RUN dotnet publish -c Release -o /app

        FROM mcr.microsoft.com/dotnet/aspnet:10.0
        WORKDIR /app

        COPY --from=build /app .
        COPY .env* ./

        EXPOSE 8080

        ENTRYPOINT ["dotnet", "{{PROJECT_NAME}}.dll"]
        """;

    private const string PaymentHandler =
        """
        using Grpc.Core;
        using T0.ProviderSdk.Api.Tzero.V1.Common;
        using T0.ProviderSdk.Api.Tzero.V1.Payment;

        namespace {{PROJECT_NAME_PASCAL}}.Services;

        // Please refer to docs and proto definitions to understand the purpose of each method.
        public class PaymentHandler(
            NetworkService.NetworkServiceClient networkClient,
            ILogger<PaymentHandler> logger) : ProviderService.ProviderServiceBase
        {
            // TODO: Step 2.1 Implement how you handle updates of payments initiated by you
            public override Task<UpdatePaymentResponse> UpdatePayment(
                UpdatePaymentRequest request, ServerCallContext context)
            {
                logger.LogInformation("UpdatePayment: payment_id={PaymentId}", request.PaymentId);
                return Task.FromResult(new UpdatePaymentResponse());
            }

            // TODO: Step 2.4 Implement how you do payouts (payments initiated by your counterparts)
            public override async Task<PayoutResponse> PayOut(
                PayoutRequest request, ServerCallContext context)
            {
                logger.LogInformation("PayOut: payment_id={PaymentId}, currency={Currency}",
                    request.PaymentId, request.Currency);

                // TODO: FinalizePayout should be called when your system completes the payout
                await networkClient.FinalizePayoutAsync(new FinalizePayoutRequest
                {
                    PaymentId = request.PaymentId,
                    Success = new FinalizePayoutRequest.Types.Success
                    {
                        Receipt = new PaymentReceipt
                        {
                            Sepa = new PaymentReceipt.Types.Sepa
                            {
                                BankingTransactionReferenceId = "123456"
                            }
                        }
                    }
                });

                return new PayoutResponse { Accepted = new PayoutResponse.Types.Accepted() };
            }

            // TODO: Optionally implement handling of limit update notifications
            public override Task<UpdateLimitResponse> UpdateLimit(
                UpdateLimitRequest request, ServerCallContext context)
            {
                return Task.FromResult(new UpdateLimitResponse());
            }

            // TODO: Optionally implement handling of new ledger transactions
            public override Task<AppendLedgerEntriesResponse> AppendLedgerEntries(
                AppendLedgerEntriesRequest request, ServerCallContext context)
            {
                return Task.FromResult(new AppendLedgerEntriesResponse());
            }

            // TODO: Implement "Last Look" — verify final rates and approve after AML check
            public override Task<ApprovePaymentQuoteResponse> ApprovePaymentQuotes(
                ApprovePaymentQuoteRequest request, ServerCallContext context)
            {
                return Task.FromResult(new ApprovePaymentQuoteResponse
                {
                    Accepted = new ApprovePaymentQuoteResponse.Types.Accepted()
                });
            }
        }
        """;

    private const string QuotePublisher =
        """
        using Google.Protobuf.WellKnownTypes;
        using T0.ProviderSdk.Api.Tzero.V1.Common;
        using T0.ProviderSdk.Api.Tzero.V1.Payment;
        using T0.ProviderSdk.Hosting;

        namespace {{PROJECT_NAME_PASCAL}}.Services;

        // TODO: Step 1.3 Replace this with fetching quotes from your systems and publishing them into the T-0 Network.
        // Recommended: publish at least once per 5 seconds, but not more than once per second.
        public class QuotePublisher(NetworkService.NetworkServiceClient client)
            : QuotePublisherService(TimeSpan.FromSeconds(5))
        {
            protected override Task PublishQuotesAsync(CancellationToken ct)
            {
                var currency = "EUR";
                var paymentMethod = PaymentMethodType.Sepa;
                var now = DateTimeOffset.UtcNow;
                var expiration = Timestamp.FromDateTimeOffset(now.AddSeconds(30));
                var timestamp = Timestamp.FromDateTimeOffset(now);

                // NOTE: Every UpdateQuote request discards all previous quotes.
                // Combine multiple quotes into a single request.
                client.UpdateQuote(new UpdateQuoteRequest
                {
                    PayIn = // Quote at which you take local currency and settle with USDT (on-ramp)
                    {
                        new UpdateQuoteRequest.Types.Quote
                        {
                            Currency = currency,
                            QuoteType = QuoteType.Realtime, // REALTIME is the only supported type
                            PaymentMethod = paymentMethod,
                            Expiration = expiration,
                            Timestamp = timestamp,
                            Bands = // One or more bands allowed
                            {
                                new UpdateQuoteRequest.Types.Quote.Types.Band
                                {
                                    ClientQuoteId = Guid.NewGuid().ToString(),
                                    MaxAmount = new Decimal { Unscaled = 1000, Exponent = 0 }, // max amount in USD
                                    // Rate is always USD/XXX (e.g. for BRL: USD/BRL)
                                    Rate = new Decimal { Unscaled = 86, Exponent = -2 } // 0.86
                                }
                            }
                        }
                    },
                    PayOut = // Quote at which you take USDT and pay out local currency (off-ramp)
                    {
                        new UpdateQuoteRequest.Types.Quote
                        {
                            Currency = currency,
                            QuoteType = QuoteType.Realtime,
                            PaymentMethod = paymentMethod,
                            Expiration = expiration,
                            Timestamp = timestamp,
                            Bands =
                            {
                                new UpdateQuoteRequest.Types.Quote.Types.Band
                                {
                                    ClientQuoteId = Guid.NewGuid().ToString(),
                                    MaxAmount = new Decimal { Unscaled = 1000, Exponent = 0 },
                                    Rate = new Decimal { Unscaled = 88, Exponent = -2 } // 0.88
                                }
                            }
                        }
                    }
                });

                return Task.CompletedTask;
            }
        }
        """;

    private const string GetQuote =
        """
        using T0.ProviderSdk.Api.Tzero.V1.Common;
        using T0.ProviderSdk.Api.Tzero.V1.Payment;

        namespace {{PROJECT_NAME_PASCAL}}.Services;

        public static class GetQuote
        {
            public static async Task FetchAsync(NetworkService.NetworkServiceClient client)
            {
                try
                {
                    var response = await client.GetQuoteAsync(new GetQuoteRequest
                    {
                        PayOutCurrency = "GBP",
                        PayOutMethod = PaymentMethodType.Swift,
                        QuoteType = QuoteType.Realtime,
                        Amount = new PaymentAmount
                        {
                            SettlementAmount = new Decimal { Unscaled = 500, Exponent = 0 } // amount in USD
                        }
                    });

                    switch (response.ResultCase)
                    {
                        case GetQuoteResponse.ResultOneofCase.Success:
                            Console.WriteLine($"Step 1.4: Got quote id={response.Success.QuoteId.QuoteId}");
                            break;
                        case GetQuoteResponse.ResultOneofCase.Failure:
                            Console.WriteLine($"Quote failed: {response.Failure.Reason}");
                            break;
                    }
                }
                catch (Grpc.Core.RpcException ex)
                {
                    Console.WriteLine($"Error getting quote: {ex.Status.StatusCode} - {ex.Message}");
                }
            }
        }
        """;

    private const string SubmitPayment =
        """
        using T0.ProviderSdk.Api.Tzero.V1.Common;
        using T0.ProviderSdk.Api.Tzero.V1.Payment;

        namespace {{PROJECT_NAME_PASCAL}}.Services;

        // TODO: Step 2.3 Test payment submission
        public static class SubmitPayment
        {
            public static async Task SubmitAsync(NetworkService.NetworkServiceClient client)
            {
                try
                {
                    var clientId = Guid.NewGuid().ToString();
                    var response = await client.CreatePaymentAsync(new CreatePaymentRequest
                    {
                        PaymentClientId = clientId,
                        Amount = new PaymentAmount
                        {
                            PayOutAmount = new Decimal { Unscaled = 10, Exponent = 0 }
                        },
                        Currency = "GBP",
                        PaymentDetails = new PaymentDetails
                        {
                            Sepa = new PaymentDetails.Types.Sepa
                            {
                                Iban = "GB12345567890",
                                BeneficiaryName = "Max Mustermann"
                            }
                        }
                    });

                    switch (response.ResultCase)
                    {
                        case CreatePaymentResponse.ResultOneofCase.Accepted:
                            Console.WriteLine($"Step 2.3: Payment accepted, id={response.Accepted.PaymentId}");
                            break;
                        case CreatePaymentResponse.ResultOneofCase.Failure:
                            Console.WriteLine($"Payment failed: {response.Failure.Reason}");
                            break;
                    }
                }
                catch (Grpc.Core.RpcException ex)
                {
                    Console.WriteLine($"Error submitting payment: {ex.Status.StatusCode} - {ex.Message}");
                }
            }
        }
        """;
}
