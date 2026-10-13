# T-0 Network Provider SDK

SDKs and starter templates for building payment provider integrations with the T-0 Network. Available for Go, TypeScript, Python, Java, and C#.

## Quick Start

Scaffold a new provider project with one command -- this is Go; the other languages are `--lang=node|python|java|csharp`:

```bash
curl -fsSL https://github.com/t-0-network/provider-sdk/releases/latest/download/start.sh | sh -s -- --lang=go my-provider
```

Windows (PowerShell):

```powershell
iwr -useb https://github.com/t-0-network/provider-sdk/releases/latest/download/start.ps1 | iex
t0-init init --lang=go my-provider
```

The install-only form, every flag, and the run command `init` prints per language: [cli/README.md](cli/README.md).

## What `init` Creates

Each scaffold generates a ready-to-run provider project with:

- **secp256k1 keypair** -- auto-generated private key in `.env`, public key printed to console
- **Provider service stubs** -- handler implementations for all T-0 Network RPC methods
- **Network client** -- pre-configured client for calling T-0 Network APIs (quote publishing, payment submission)
- **Dockerfile** -- production-ready container build
- **Environment config** -- `.env` with sandbox defaults

## Getting Started

After scaffolding your project:

1. **Share your public key** (printed during setup) with the T-0 team to register your provider.

2. **Phase 1 -- Quoting:**
   - Implement your quote publishing logic (exchange rates for your supported currency pairs)
   - Verify quotes are received by the network

3. **Phase 2 -- Payments:**
   - Implement payment handlers (`update_payment`, `pay_out`)
   - Deploy and share your base URL with the T-0 team
   - Test end-to-end payment flows

Each generated project contains numbered `TODO` comments that guide you through these steps.

## Environment Variables

All starters read the same variables. `t0-init` creates `.env` from `.env.example` and writes a new private key into it. The other values in that file are for the sandbox.

| Variable | In `.env` after `t0-init` | If unset or empty | Description |
|----------|---------------------------|-------------------|-------------|
| `PROVIDER_PRIVATE_KEY` | A new key generated for this project | The provider does not start | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | The sandbox network key | The provider does not start | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | `https://api-sandbox.t-0.network` | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | `8080` | `8080` | Server port |
| `QUOTE_PUBLISHING_INTERVAL` | not set | `5000` | Quote publishing interval in milliseconds |

The process reads `.env` from the working directory. A variable set in the process environment wins over `.env`, even when its value is empty, and an empty value counts as unset. For example, if `.env` has `PORT=9000` and the environment has `PORT=`, the server listens on 8080. To use the value from `.env`, unset the variable. If `.env` is missing, the process prints one notice on stderr and continues with the process environment.

For production, set both `NETWORK_PUBLIC_KEY` and `TZERO_ENDPOINT` to the values the T-0 team gives you. If `TZERO_ENDPOINT` is unset, the provider connects to the sandbox.

If a key is missing, or `PORT` is not a port number, the process prints `ERROR: <message>` and one help line on stderr and exits with status 1. Any other startup failure, for example a malformed key or a port already in use, prints `Provider failed to start: <cause>` and exits with status 1. After startup, a failed call to the network is logged as one line, and the provider keeps running. On SIGINT or SIGTERM, the provider stops publishing quotes and waits for the calls in progress. Then it exits with status 0.

## SDK Packages

For direct SDK usage without the starter:

| Platform | Package | Install |
|----------|---------|---------|
| Go | `github.com/t-0-network/provider-sdk/go` | `go get github.com/t-0-network/provider-sdk/go` |
| TypeScript | `@t-0/provider-sdk` | `npm install @t-0/provider-sdk` |
| Python | `t0-provider-sdk` | `uv add t0-provider-sdk` |
| Java | `network.t-0:provider-sdk-java` | See [Java docs](java/README.md#installation) |
| C# | `T0.ProviderSdk` | `dotnet add package T0.ProviderSdk` |

## Security

- **Never commit `.env`** -- it is git-ignored by default. Keep `PROVIDER_PRIVATE_KEY` in a secrets manager in production.
- **Share only your public key** with the T-0 team. Never share your private key.
- **Use separate keys** for development, staging, and production environments.
- **Signature verification** -- all inbound requests from the T-0 Network are cryptographically verified using `NETWORK_PUBLIC_KEY`. Verification uses raw request body bytes.
- **Timestamp validation** -- request timestamps must be within the allowed window of server time ([rules](docs/CROSS_SDK_RULES.md)). Keep system clocks synchronized (NTP).

## Further Reading

- [T-0 Network Documentation](https://docs.t-0.network/)
- [Unified CLI](cli/README.md)
- Go: [SDK](go/README.md) | [what `init` creates](go/starter/template/README.md)
- TypeScript: [SDK](node/sdk/README.md) | [what `init` creates](node/starter/template/README.md)
- Python: [SDK](python/README.md) | [what `init` creates](python/starter/template/README.md)
- Java: [SDK](java/README.md) | [what `init` creates](java/starter/template/README.md)
- C#: [SDK](csharp/README.md) | [what `init` creates](csharp/starter/template/README.md)

## Support

- Review the `TODO` comments in the generated project code
- Check the [T-0 Network documentation](https://docs.t-0.network/)
- Contact the T-0 team for integration support and production onboarding

## Versioning

All SDKs share a unified version, managed via git tags (`vX.Y.Z`). See [`docs/VERSIONING.md`](docs/VERSIONING.md) for details on where the version lives in each ecosystem and [`docs/RELEASE_AND_PUBLISH.md`](docs/RELEASE_AND_PUBLISH.md) for the release/publish CI flow.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, testing, protobuf code generation, and release process.

After cloning, run `scripts/buf-generate.sh` once from the repository root. The Node, Python and C# protobuf code is generated, not committed, and the script also turns on the Git hooks that keep it current.
