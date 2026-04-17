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
│   ├── index.ts                                  # Entry point
│   ├── service.ts                                # Phase 2: ProviderService handlers
│   ├── payment_intent_pay_in_service.ts          # Phase 3A: PayInProviderService handler
│   ├── payment_intent_beneficiary_service.ts     # Phase 3B: BeneficiaryService handler
│   ├── publish_quotes.ts                         # Phase 1: payout quote publishing
│   ├── get_quote.ts                              # Phase 1: quote retrieval
│   ├── publish_payment_intent_quotes.ts          # Phase 3A: pay-in quote publishing
│   ├── get_payment_intent_quote.ts               # Phase 3B: indicative quote retrieval
│   ├── create_payment_intent.ts                  # Phase 3B: create a payment intent
│   ├── confirm_funds_received.ts                 # Phase 3A: confirm funds received
│   ├── submit_payment.ts                         # Phase 2: payment submission
│   └── lib.ts                                    # Utility functions
├── Dockerfile                                    # Docker configuration
├── .env                                          # Environment variables (with generated keys)
├── .env.example                                  # Example environment file
├── .eslintrc.json                                # ESLint configuration
├── .gitignore                                    # Git ignore rules
├── package.json                                  # Project dependencies
└── tsconfig.json                                 # TypeScript configuration
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

### Phase 3: Payment Intent Flow

The payment intent flow is independent of Phase 2. It is an asynchronous pay-in flow where an end-user pays a pay-in provider in fiat (bank transfer, mobile money, etc.) and a beneficiary provider receives settlement on the crypto side. Quotes are indicative until funds are received, settlement happens periodically, and a confirmation code links the end-user's payment back to a specific payment intent.

Implement **one** of the two sub-phases below depending on your role. If you participate on both sides, implement both.

**Phase 3A -- Pay-In Provider role** (skip if you're a beneficiary):

1. **Step 3A.1** Replace the sample pay-in quote publishing in `src/publish_payment_intent_quotes.ts` with your own.
2. **Step 3A.2** Implement `getPaymentDetails` in `src/payment_intent_pay_in_service.ts` -- return bank account / mobile money details plus a payment reference the end-user will include in their transfer.
3. **Step 3A.3** When you detect the end-user's fiat payment, call `confirmFundsReceived` (see `src/confirm_funds_received.ts`).

**Phase 3B -- Beneficiary Provider role** (skip if you're pay-in):

1. **Step 3B.1** Verify indicative quotes are returned (`src/get_payment_intent_quote.ts`).
2. **Step 3B.2** Create payment intents for your end-users via `createPaymentIntent` (see `src/create_payment_intent.ts`).
3. **Step 3B.3** Implement `paymentIntentUpdate` in `src/payment_intent_beneficiary_service.ts` to receive notifications when funds are received.

If you only play one role, delete the files for the other role and remove the corresponding `r.service(...)` registration in `src/index.ts`.

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
