# T-0 Provider SDK -- TypeScript

TypeScript SDK for building provider integrations with the T-0 Network. Handles secp256k1 cryptographic signing, signature verification, and provides typed ConnectRPC clients for all T-0 Network APIs.

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

The middleware chain: `signatureValidation` captures raw request bytes for hashing, `nodeAdapter` bridges ConnectRPC to Node.js HTTP, and `createService` registers your handlers with signature verification.

### Standalone Signature Verification

For frameworks that don't use Node's `http.createServer` (Effect, Koa, Fastify, etc.), use `createRequestVerifier` to verify inbound requests with just the raw body bytes and headers:

```ts
import { createRequestVerifier, parsePublicKey } from "@t-0/provider-sdk";

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

  // Request is authenticated — parse the protobuf body and handle it
}
```

The lower-level primitives are also exported individually: `verifySignature`, `computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`. You can also import just the crypto module without pulling in ConnectRPC: `import { createRequestVerifier } from "@t-0/provider-sdk/crypto"`.

**Important constraints for standalone integrations:**

- **Raw body bytes only.** Pass the exact wire bytes to the verifier — no body parsers, no auto-decompression, never re-serialized protobuf. Protobuf encoding is not canonical; re-encoding produces different bytes and breaks verification.
- **Pass `Uint8Array`, not `ArrayBuffer`.** If your framework gives you an `ArrayBuffer` (e.g. `request.arrayBuffer()`), wrap it: `new Uint8Array(buf)`.
- **Header case.** `NetworkHeaders` enum values are title-case (`X-Signature`), but Node lowercases incoming headers. Look up headers by lowercase name: `headers["x-signature"]`.
- **Connect error format.** Return errors as JSON `{ "code": "unauthenticated", "message": "..." }` with the mapped HTTP status (401 for unauthenticated). A bare HTTP status code is not a valid Connect error response.
- **Health endpoint.** The T-0 Network probes `/grpc.health.v1.Health/Check` on every endpoint. The probe is signed. Standalone integrations must route this path and return a valid health response. See [`docs/HEALTH_SERVICE.md`](../../docs/HEALTH_SERVICE.md) for the wire contract.
- **`VerifyRequestFailure` is an open union.** New reason values may be added without a major version bump. Handle unknown reasons as generic failures.

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
