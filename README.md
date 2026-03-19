# T-0 Network Provider SDK

SDKs and starter templates for building payment provider integrations with the T-0 Network. Available for Go, TypeScript, Python, and Java.

## Quick Start

Choose your platform and run the one-liner to scaffold a new provider project:

### Go

```bash
go run github.com/t-0-network/provider-sdk/go/starter@latest my-provider
```

Requires Go 1.25+. See [Go starter documentation](go/starter/README.md) for details.

### TypeScript

```bash
npx @t-0/provider-starter-ts
```

Requires Node.js LTS. See [TypeScript starter documentation](node/starter/README.md) for details.

### Python

```bash
uvx t0-provider-starter my_provider
```

Requires Python 3.13+ and [uv](https://docs.astral.sh/uv/). See [Python documentation](python/README.md) for details.

### Java

```bash
curl -fsSL -L https://github.com/t-0-network/provider-sdk/releases/latest/download/provider-init.jar -o provider-init.jar && java -jar provider-init.jar && rm provider-init.jar
```

Requires Java 17+. See [Java documentation](java/README.md) for details.

## What the Starter Creates

Each starter generates a ready-to-run provider project with:

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

All platforms use the same environment variables:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API URL |
| `PORT` | No | `8080` (`3000` for TypeScript) | Server listen port |
| `QUOTE_PUBLISHING_INTERVAL` | No | `5000` | Quote publishing interval (ms) |

## SDK Packages

For direct SDK usage without the starter:

| Platform | Package | Install |
|----------|---------|---------|
| Go | `github.com/t-0-network/provider-sdk/go` | `go get github.com/t-0-network/provider-sdk/go` |
| TypeScript | `@t-0/provider-sdk` | `npm install @t-0/provider-sdk` |
| Python | `t0-provider-sdk` | `uv add t0-provider-sdk` |
| Java | `network.t-0:provider-sdk-java` | See [Java docs](java/README.md#installation) |

## Security

- **Never commit `.env`** -- it is git-ignored by default. Keep `PROVIDER_PRIVATE_KEY` in a secrets manager in production.
- **Share only your public key** with the T-0 team. Never share your private key.
- **Use separate keys** for development, staging, and production environments.
- **Signature verification** -- all inbound requests from the T-0 Network are cryptographically verified using `NETWORK_PUBLIC_KEY`. Verification uses raw request body bytes.
- **Timestamp validation** -- request timestamps must be within +/- 60 seconds of server time. Keep system clocks synchronized (NTP).

## Further Reading

- [T-0 Network Documentation](https://t-0.network/docs)
- [Go Starter](go/starter/README.md) | [Go SDK](go/README.md)
- [TypeScript Starter](node/starter/README.md) | [TypeScript SDK](node/sdk/README.md)
- [Python Starter & SDK](python/README.md)
- [Java Starter & SDK](java/README.md)

## Support

- Review the `TODO` comments in the generated project code
- Check the [T-0 Network documentation](https://docs.t-0.network/)
- Contact the T-0 team for integration support and production onboarding

## Versioning

All SDKs share a unified version, managed via git tags (`vX.Y.Z`).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, testing, protobuf code generation, and release process.
