# T-0 Provider Starter -- TypeScript

CLI tool to scaffold a Node.js TypeScript provider project for the T-0 Network.

## Quick Start

```bash
npx @t-0/provider-starter-ts
```

The CLI will prompt for a project name, then create a ready-to-run project with a secp256k1 keypair (via OpenSSL), environment config, provider service stubs, and a Dockerfile.

## Generated Project Structure

```
your-project-name/
├── src/
│   ├── index.ts              # Entry point
│   ├── service.ts            # Provider service implementation
│   ├── publish_quotes.ts     # Quote publishing logic
│   ├── get_quote.ts          # Quote retrieval logic
│   ├── submit_payment.ts     # Payment submission logic
│   └── lib.ts                # Utility functions
├── Dockerfile                # Docker configuration
├── .env                      # Environment variables (with generated keys)
├── .env.example              # Example environment file
├── .eslintrc.json            # ESLint configuration
├── .gitignore                # Git ignore rules
├── package.json              # Project dependencies
└── tsconfig.json             # TypeScript configuration
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `src/service.ts` | Implement your payment processing logic. Look for `TODO` comments. |
| `src/publish_quotes.ts` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | No | `3000` | Server port |
| `QUOTE_PUBLISHING_INTERVAL` | No | -- | Quote publishing frequency in milliseconds |

## Getting Started

### Phase 1: Quoting

1. Open `.env` and find your generated public key (marked as "Step 1.2"). Share it with the T-0 team to register your provider.
2. Implement your quote publishing logic in `src/publish_quotes.ts`.
3. Start the dev server (`npm run dev`) and verify quotes are published.
4. Confirm quote retrieval works by checking the output of `getQuote` in `src/index.ts`.

### Phase 2: Payments

1. Implement `updatePayment` handler in `src/service.ts`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `payOut` handler in `src/service.ts`.
4. Test payment submission by uncommenting the `submitPayment` call in `src/index.ts`.
5. Coordinate with the T-0 team to test end-to-end payment flows.

## Available Commands

```bash
npm run dev        # Run in development mode with ts-node
npm run build      # Compile TypeScript to dist/
npm start          # Run compiled production build
npm run lint       # Lint TypeScript source with ESLint
```

## Deployment

```bash
docker build -t my-provider .
docker run -p 3000:3000 --env-file .env my-provider
```

## SDK Reference

For direct SDK usage (without the starter), see the [TypeScript SDK documentation](../sdk/README.md).

## Troubleshooting

**"Directory already exists"** -- Choose a different project name.

**"OpenSSL not found"** -- Install OpenSSL (`brew install openssl` on macOS, `sudo apt-get install openssl` on Debian/Ubuntu).

**Key generation fails** -- Ensure OpenSSL is in your PATH: `openssl version`.

**npm install fails** -- Check Node.js >= 18 and npm >= 8: `node --version && npm --version`.
