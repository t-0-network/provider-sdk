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
