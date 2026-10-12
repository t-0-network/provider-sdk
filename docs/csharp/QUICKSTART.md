# C# Provider SDK — Quick Start

## Create a New Provider

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh -s -- --lang=csharp my-provider
cd my-provider
```

All flags and the install-only form: [`cli/README.md`](../../cli/README.md).

The initializer generates a complete project with:
- `.env` with auto-generated secp256k1 keypair
- `PaymentHandler.cs` with all gRPC methods stubbed
- `Config.cs`, which reads the environment into a `T0Config`
- `QuotePublisher.cs` for periodic quote publishing (on the starter's `QuotePublisherService`)
- `Dockerfile` for deployment

## Minimal Program.cs

```csharp
using T0.ProviderSdk;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

MyProvider.Config.LoadEnv();
var config = MyProvider.Config.FromEnvironment();
var signer = Signer.FromHex(config.ProviderPrivateKey);
var networkClient = NetworkClient.CreateNetworkServiceClient(config.TZeroEndpoint, signer);

var server = new T0ProviderServer(config, signer);
server.MapPaymentService<MyProvider.Services.PaymentHandler>(networkClient);
server.AddHostedService<MyProvider.Services.QuotePublisher>();
await server.RunAsync();
```

The overload that takes `NetworkClientOptions` in place of the base URL also sets `Timeout` (unary calls, default 15 s) and `StreamTimeout` (client and server streams, default 5 min); see [Streaming and timeouts](../../csharp/README.md#streaming-and-timeouts).

## Environment Variables

`t0-init` creates `.env` from `.env.example` and writes a new private key into it. The other values in that file are for the sandbox.

| Variable | In `.env` after `t0-init` | If unset or empty | Description |
|----------|---------------------------|-------------------|-------------|
| `PROVIDER_PRIVATE_KEY` | A new key generated for this project | The provider does not start | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | The sandbox network key | The provider does not start | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | `https://api-sandbox.t-0.network` | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | `8080` | `8080` | Server port |
| `QUOTE_PUBLISHING_INTERVAL` | not set | `5000` | Quote publishing interval in milliseconds |

The process reads `.env` from the working directory. A variable set in the process environment wins over `.env`, even when its value is empty, and an empty value counts as unset. For example, if `.env` has `PORT=9000` and the environment has `PORT=`, the server listens on 8080. To use the value from `.env`, unset the variable. If `.env` is missing, the process prints one notice on stderr and continues with the process environment.

For production, set both `NETWORK_PUBLIC_KEY` and `TZERO_ENDPOINT` to the values the T-0 team gives you. If `TZERO_ENDPOINT` is unset, the provider connects to the sandbox. `.env` holds your private key, so keep it out of Git. Use a separate private key in production.

If a key is missing, or `PORT` is not a port number, the process prints `ERROR: <message>` and one help line on stderr and exits with status 1. Any other startup failure, for example a malformed key or a port already in use, prints `Provider failed to start: <cause>` and exits with status 1. On SIGINT or SIGTERM, the provider stops publishing quotes and waits for the calls in progress. Then it exits with status 0.

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
10. **Step 2.6 (optional)** — Complete manual AML checks via `CompleteManualAmlCheck` if your `PayOut` handler returns `ManualAmlCheck`

## PaymentIntent Support

For providers handling payment intents, map an additional service:

```csharp
var intentNetworkClient = NetworkClient.CreatePaymentIntentNetworkServiceClient(
    config.TZeroEndpoint, signer);

server.MapPaymentIntentService<MyProvider.Services.PaymentIntentHandler>(intentNetworkClient);
```

## Testing Your Handler

A client takes a `SignFn` delegate, so a test can pass a plain lambda instead of a key; `ISignatureVerifier` can be mocked for server tests:

```csharp
// In your test
SignFn fakeSigner = digest => (new byte[65], fakeUncompressedPublicKey);
```
