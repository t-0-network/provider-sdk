# CLAUDE.md - Node SDK & Starter

## CRITICAL CRYPTOGRAPHIC REQUIREMENT

**Signature verification and signing MUST use raw payload bytes.**

Protobuf encoding is not canonical — re-encoding a deserialized message produces different bytes. Always verify/sign against the original wire bytes, never re-serialized output.

## Build Commands

```bash
cd sdk && npm ci && npm run build      # Build SDK
cd starter && npm ci && npm run build  # Build Starter
cd sdk && npm test                     # Run SDK tests
```

## Project Structure

```
node/
├── sdk/                  # @t-0/provider-sdk (published to npm)
│   ├── src/              # TypeScript source
│   ├── lib/              # Build output (ESM + CJS dual publish)
│   └── test/             # Tests
└── starter/              # @t-0/provider-starter-ts (published to npm)
    ├── src/              # CLI source
    └── template/         # Embedded project template
```

## Publishing

- Both packages are published to npm with `--provenance --access public`
- **npm provenance requires GitHub-hosted runners** (`ubuntu-latest`). Blacksmith/self-hosted runners are rejected by npm with "Unsupported GitHub Actions runner environment: self-hosted"
- Trusted publishing uses OIDC (`id-token: write` permission) — no npm tokens needed
- Trusted publisher config on npmjs.com must point to repo `t-0-network/provider-sdk` and the correct workflow/environment

## Versioning

Runtime version constant: `src/version.ts` (`SDK_VERSION`). Full details: [`docs/VERSIONING.md`](../docs/VERSIONING.md).

## Cross-Language Testing

Server-to-server cross-tests in `sdk/test/cross_server.test.ts` exercise bidirectional health check round-trips between Node and Go using the shared helper at `cross_test/go_helper/`. Build it first:

```bash
cd ../cross_test/go_helper && go build -o go_helper . && cd ../../node/sdk
npm test   # cross-tests included in the suite
```

CI builds the Go helper automatically. Tests fail (not skip) in CI if the helper is missing.

## Dual ESM/CJS Build

The SDK publishes both ESM and CJS:
- `lib/esm/` — ES modules (via `tsconfig.esm.json`)
- `lib/cjs/` — CommonJS (via `tsconfig.cjs.json`, with generated `package.json` containing `{"type":"commonjs"}`)
- Package exports map routes `import` → ESM, `require` → CJS
