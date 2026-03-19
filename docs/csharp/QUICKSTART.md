# C# Provider SDK — Quick Start

## Create a New Provider

```bash
# Install and run the starter CLI
dotnet tool install -g T0.ProviderStarter
t0-provider-starter my-provider
cd my-provider
```

The starter generates a complete project with:
- `.env` with auto-generated secp256k1 keypair
- `PaymentHandler.cs` with all gRPC methods stubbed
- `QuotePublisher.cs` for periodic quote publishing
- `Dockerfile` for deployment

## Minimal Program.cs

```csharp
using T0.ProviderSdk;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

DotNetEnv.Env.Load();
var config = T0Config.FromEnvironment();
var signer = Signer.FromHex(config.ProviderPrivateKey);
var networkClient = NetworkClient.CreateNetworkServiceClient(config.TZeroEndpoint, signer);

var server = new T0ProviderServer(config, signer);
server.MapPaymentService<MyProvider.Services.PaymentHandler>(networkClient);
server.AddHostedService<MyProvider.Services.QuotePublisher>();
await server.RunAsync();
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | — | secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | Yes | — | T-0 Network's public key (hex) |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | API endpoint |
| `PORT` | No | `8080` | Server port |

## Integration Steps

1. **Step 1.1** — Run the provider server
2. **Step 1.2** — Share your public key with the T-0 team
3. **Step 1.3** — Replace sample quotes with your own pricing logic
4. **Step 1.4** — Verify quotes are received via `GetQuote`
5. **Step 2.1** — Implement `UpdatePayment` handler
6. **Step 2.2** — Deploy and share your base URL with T-0 team
7. **Step 2.3** — Test payment submission via `SubmitPayment`
8. **Step 2.4** — Implement `PayOut` handler
9. **Step 2.5** — Ask T-0 team to test payout to your provider

## PaymentIntent Support

For providers handling payment intents, map an additional service:

```csharp
var intentNetworkClient = NetworkClient.CreatePaymentIntentNetworkServiceClient(
    config.TZeroEndpoint, signer);

server.MapPaymentIntentService<MyProvider.Services.PaymentIntentHandler>(intentNetworkClient);
```

## Testing Your Handler

The SDK provides interfaces (`ISigner`, `ISignatureVerifier`) for easy mocking:

```csharp
// In your test
var mockSigner = new Mock<ISigner>();
mockSigner.Setup(s => s.GetPublicKey()).Returns(new byte[65]);
```
