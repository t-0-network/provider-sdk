# T-0 Provider SDK -- C#

C# SDK for building T-0 Network payment provider integrations. The SDK provides a gRPC-based framework with automatic secp256k1 cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **.NET** 10.0 or later

## Quick Start

Scaffold a new provider project with one command:

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh -s -- --lang=csharp my-provider
```

All flags and the install-only form: [cli/README.md](../cli/README.md). What `init` creates: [starter template README](starter/template/README.md).

## Installation

To use the SDK directly, add the NuGet package:

```bash
dotnet add package T0.ProviderSdk
```

## Streaming and timeouts

```csharp
var signer = Signer.FromHex(privateKeyHex);
var options = new NetworkClientOptions
{
    BaseUrl = "https://api.t-0.network",      // the default
    Timeout = TimeSpan.FromSeconds(15),       // unary calls, the default
    StreamTimeout = TimeSpan.FromMinutes(5),  // client and server streams, whole call, the default
};

var client = NetworkClient.CreateNetworkServiceClient(options, signer);
// Any generated gRPC client: NetworkClient.Create(options, signer, invoker => new XClient(invoker))

// A deadline on the call replaces the default, shorter or longer.
await client.UpdateQuoteAsync(request, deadline: DateTime.UtcNow.AddMinutes(1));
```

## Custom signer

A client signs each request with a `SignFn`: it takes the 32-byte digest and returns the signature
and the 65-byte uncompressed public key it verifies against. `Signer.FromHex(privateKeyHex)`
converts to one, as above. To sign elsewhere, such as in an HSM or a KMS, pass your own:

```csharp
SignFn sign = digest =>
{
    byte[] signature = hsm.Sign(digest);   // 64 bytes r‖s, or 65 bytes r‖s‖v
    return (signature, hsm.PublicKey);     // 65 bytes, uncompressed (0x04 ‖ x ‖ y)
};
var client = NetworkClient.CreateNetworkServiceClient(options, sign);
```

The client checks the output before it sends anything. A signature that is not 64 or 65 bytes, a
public key that is not 65 bytes uncompressed, or an exception from the signer fails the call with
Internal `signing the request failed: <message>`.

`ISigner`, the signer interface of v1.2, is obsolete but still works: `Signer` implements it, the
server registers its signer as it, and every client factory and `SigningDelegatingHandler` take one
through obsolete overloads that sign with the same checks. New code passes a `SignFn` or a `Signer`.

A client or server stream is signed over its first message and sent as soon as that message is written; bidirectional streams are refused. A timeout must be greater than zero and at most 2147483647 ms (`NetworkClientOptions.MaxTimeout`). The streaming rules shared by all SDKs: [`docs/STREAMING.md`](../docs/STREAMING.md).

## KYC files

`KycFiles.UploadFileAsync` and `KycFiles.DownloadFileAsync` run a `KycFileService` stream on a client the caller already built. They do not sign or retry. `UploadId` is sent as given.

```csharp
var files = NetworkClient.Create(new NetworkClientOptions { BaseUrl = kybUrl }, signer,
    i => new KycFileService.KycFileServiceClient(i));
long fileId = await KycFiles.UploadFileAsync(files, new UploadFileRequest.Types.Metadata
{
    PayoutProviderId = pid, ClientId = cid, FileName = "passport.pdf",
}, await File.ReadAllBytesAsync(path));
var (metadata, data) = await KycFiles.DownloadFileAsync(files, new DownloadFileRequest
{
    FileId = fileId, PayoutRequesterId = rid, PayoutProviderId = pid, ClientId = cid,
});
```

A download holds up to twice the file in memory: the chunks and the joined bytes. A 50 MiB file can take 100 MiB. One transfer uses the stream timeout (5 minutes) unless the caller sets a longer `deadline` on the call, or a longer `StreamTimeout` on the client.

## Available Commands

```bash
dotnet run                 # Run the application
dotnet build               # Build the project
dotnet test                # Run tests
```

## Deployment

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `PROVIDER_PRIVATE_KEY is not set. Check your .env file.` | `.env` is generated with a fresh key next to the `.csproj`; run from that directory. To generate a new key, run `t0-init keygen` and set `PROVIDER_PRIVATE_KEY` to the private key it prints (see [`cli/README.md`](../cli/README.md)) |
| Signature verification failures | Ensure system clock is synchronized (NTP); timestamps outside the allowed window are rejected ([rules](../docs/CROSS_SDK_RULES.md)) |
| gRPC connection refused | Verify `TZERO_ENDPOINT` is correct and reachable |
| Port already in use | Change `PORT` in `.env` or stop the conflicting process |
