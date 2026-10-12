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

    var server = new T0ProviderServer(config, signer);
    server.MapPaymentService<MyProvider.Services.PaymentHandler>(networkClient);
    server.AddHostedService<MyProvider.Services.QuotePublisher>();

    // TODO: Step 1.4 Verify that quotes for target currency are successfully received
    MyProvider.Services.GetQuote.Port = config.Port;
    server.AddHostedService<MyProvider.Services.GetQuote>();

    await server.RunAsync();
}
catch (MyProvider.ConfigException ex)
{
    Console.Error.WriteLine($"ERROR: {ex.Message}");
    Console.Error.WriteLine(ex.Help);
    Environment.Exit(1);
}
catch (Exception ex) when (MyProvider.Config.IsInvalidNetworkKey(ex))
{
    Console.Error.WriteLine($"ERROR: {ex.Message}");
    Console.Error.WriteLine(MyProvider.Config.NetworkKeyHelp);
    Environment.Exit(1);
}
catch (OperationCanceledException)
{
    // SIGINT/SIGTERM: the host already drained.
}
catch (Exception ex)
{
    Console.Error.WriteLine($"Provider failed to start: {ex.Message}");
    Environment.Exit(1);
}
