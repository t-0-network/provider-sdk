using T0.ProviderSdk;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

try
{
    MyProvider.Config.LoadEnv();
    var config = MyProvider.Config.FromEnvironment();
    var signer = Signer.FromHex(config.ProviderPrivateKey);
    Console.WriteLine($"Provider public key: {signer.GetPublicKeyHexPrefixed()}");

    // TODO: Step 1.2 Share the generated public key from .env with the T-0 team

    var networkClient = NetworkClient.CreateNetworkServiceClient(config.TZeroEndpoint, signer);
    var paymentIntentClient = NetworkClient.CreatePaymentIntentServiceClient(config.TZeroEndpoint, signer);

    var server = new T0ProviderServer(config, signer);
    server.MapPaymentService<MyProvider.Services.PaymentHandler>(networkClient);
    // Prints the listening line. QuotePublisher waits for it before its first publish.
    MyProvider.Listening.Port = config.Port;
    server.AddHostedService<MyProvider.Listening>();
    server.AddHostedService<MyProvider.Services.QuotePublisher>();

    // TODO: Step 1.4 Verify that quotes for target currency are successfully received
    server.AddHostedService<MyProvider.Services.GetQuote>();

    // TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
    // TODO: Step 2.3 Test payment submission (see Services/SubmitPayment.cs)
    // TODO: Step 2.5 Ask t-0 team to submit a payment to test your PayOut endpoint
    // TODO: Step 2.6 (optional) Complete manual AML checks if your PayOut returns ManualAmlCheck
    // (see Services/CompleteManualAmlCheck.cs)

    // ──────────────────────────────────────────────────────────────
    // Payment Intent Flow — Phase 3
    //
    // Implement the role that applies to you. See the README for details.
    // ──────────────────────────────────────────────────────────────

    // Phase 3A — Pay-In Provider role. Comment out if you are only a beneficiary.
    server.MapPayInProviderService<MyProvider.Services.PaymentIntentPayInHandler>(paymentIntentClient);
    // TODO: Step 3A.1 Replace with your own pay-in quote publishing logic
    server.AddHostedService<MyProvider.Services.PaymentIntentQuotePublisher>();

    // Phase 3B — Beneficiary Provider role. Comment out if you are only a pay-in provider.
    server.MapBeneficiaryService<MyProvider.Services.PaymentIntentBeneficiaryHandler>(paymentIntentClient);
    // TODO: Step 3B.1 Check that indicative quotes are being returned
    server.AddHostedService<MyProvider.Services.GetPaymentIntentQuote>();
    // TODO: Step 3B.2 Create a payment intent for a real end-user when they want to pay
    // (see Services/CreatePaymentIntent.cs)

    await server.RunAsync();
}
catch (MyProvider.ConfigException ex)
{
    Console.Error.WriteLine($"ERROR: {ex.Message}");
    Console.Error.WriteLine(ex.Help);
    Environment.Exit(1);
}
catch (OperationCanceledException)
{
    // SIGINT/SIGTERM: the host already drained.
}
catch (Exception ex) when (MyProvider.Listening.Started.IsCompleted)
{
    // The server ran, so this failure came while it stopped.
    Console.Error.WriteLine($"Provider failed to shut down: {ex.Message}");
}
catch (Exception ex)
{
    Console.Error.WriteLine($"Provider failed to start: {ex.Message}");
    Environment.Exit(1);
}
