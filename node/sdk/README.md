# T-0 Provider SDK -- TypeScript

TypeScript SDK for building provider integrations with the T-0 Network. Handles secp256k1 cryptographic signing, signature verification, and provides typed clients for all T-0 Network APIs. All request and response payloads are **Protobuf-encoded**.

## Quick Start

Bootstrap a new provider project:

```bash
npx @t-0/provider-starter-ts
```

See [starter README](../starter/README.md) for details on the generated project.

## Installation

```bash
npm install @t-0/provider-sdk
```

## Usage

### Provider Service

Implement the `ProviderService` interface to receive callbacks from the T-0 Network (payment updates, payout requests, etc.):

```ts
import http from "node:http";
import {
  createService,
  nodeAdapter,
  signatureValidation,
  ProviderService,
  PayoutRequest,
  PayoutResponse,
  UpdatePaymentRequest,
  UpdatePaymentResponse,
  HandlerContext,
} from "@t-0/provider-sdk";

const networkPublicKey = process.env.NETWORK_PUBLIC_KEY!;

const server = http.createServer(
  signatureValidation(
    nodeAdapter(
      createService(networkPublicKey, (r) => {
        r.service(ProviderService, {
          async payOut(req: PayoutRequest, ctx: HandlerContext): Promise<PayoutResponse> {
            // Handle payout requests from counterparts
            return { result: { case: "accepted", value: {} } } as PayoutResponse;
          },
          async updatePayment(req: UpdatePaymentRequest, ctx: HandlerContext): Promise<UpdatePaymentResponse> {
            // Handle payment status updates
            return {} as UpdatePaymentResponse;
          },
        });
      })
    )
  )
);

server.listen(3000);
```

The middleware chain: `signatureValidation` captures raw request bytes for hashing, `nodeAdapter` bridges the RPC transport to Node.js HTTP, and `createService` registers your handlers with signature verification.

### Standalone Signature Verification

For frameworks that don't use Node's `http.createServer` (Effect, Koa, Fastify, etc.), use `createRequestVerifier` to verify inbound requests with just the raw body bytes and headers:

```ts
import { fromBinary, toBinary } from "@bufbuild/protobuf";
import { createRequestVerifier, PayoutRequestSchema, PayoutResponseSchema } from "@t-0/provider-sdk";

const verify = createRequestVerifier({
  networkPublicKey: process.env.NETWORK_PUBLIC_KEY!,
});

// In your framework's request handler:
function handleRequest(rawBody: Uint8Array, headers: Record<string, string>) {
  const result = verify({
    body: rawBody,
    signatureHeader: headers["x-signature"],
    publicKeyHeader: headers["x-public-key"],
    timestampHeader: headers["x-signature-timestamp"],
  });

  if (!result.valid) {
    // result.reason is one of: 'invalid_timestamp', 'timestamp_out_of_range',
    // 'invalid_public_key', 'unknown_public_key', 'invalid_signature_format',
    // 'signature_failed'
    return errorResponse(result.reason);
  }

  // Deserialize the Protobuf request (same raw bytes you verified)
  const request = fromBinary(PayoutRequestSchema, rawBody);
  const response = handlePayout(request);

  // Serialize the Protobuf response
  return successResponse(toBinary(PayoutResponseSchema, response), {
    "content-type": "application/proto",
  });
}
```

All request and response payloads are Protobuf-encoded — deserialize with `fromBinary()`, serialize with `toBinary()` (both from `@bufbuild/protobuf`).

The lower-level primitives are also exported individually: `verifySignature`, `computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`.

### Network Client

Use `createClient` to call T-0 Network APIs. The client handles request signing automatically:

```ts
import { createClient, NetworkService } from "@t-0/provider-sdk";

const privateKey = process.env.PROVIDER_PRIVATE_KEY!;
const endpoint = process.env.TZERO_ENDPOINT || "https://api-sandbox.t-0.network";

const networkClient = createClient(privateKey, endpoint, NetworkService);

// Publish quotes
await networkClient.updateQuote({
  payOut: [
    {
      currency: "EUR",
      quoteType: 1, // REALTIME
      paymentMethod: 1,
      bands: [{ clientQuoteId: "q1", maxAmount: { value: "10000" }, rate: { value: "0.92" } }],
      expiration: { seconds: BigInt(Math.floor(Date.now() / 1000) + 30) },
      timestamp: { seconds: BigInt(Math.floor(Date.now() / 1000)) },
    },
  ],
});

// Get a quote
const quote = await networkClient.getQuote({
  amount: { payOutAmount: { value: "100" } },
  payOutCurrency: "EUR",
  payOutMethod: 1,
  quoteType: 1,
});
```

## Development

```bash
npm ci               # Install dependencies
npm run build        # Build (ESM + CJS dual output)
npm test             # Run tests
```
