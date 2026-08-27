# T-0 Provider SDK -- TypeScript

TypeScript SDK for building provider integrations with the T-0 Network. All communication is Protobuf-encoded and secp256k1-signed. Provides signature verification for inbound requests and typed clients for all T-0 Network APIs.

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
  createHandler,
  ProviderService,
  PayoutRequest,
  PayoutResponse,
  UpdatePaymentRequest,
  UpdatePaymentResponse,
  HandlerContext,
} from "@t-0/provider-sdk";

const networkPublicKey = process.env.NETWORK_PUBLIC_KEY!;

const server = http.createServer(
  createHandler(networkPublicKey, (r) => {
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
);

server.listen(3000);
```

`createHandler` composes the full middleware chain in one call: signature validation (raw byte capture), Connect-Node adapter, and service registration with signature verification.

<details>
<summary>Manual composition (advanced)</summary>

For cases where you need to customize the middleware chain, the individual components are also exported:

```ts
import { createService, nodeAdapter, signatureValidation } from "@t-0/provider-sdk";

const server = http.createServer(
  signatureValidation(nodeAdapter(createService(networkPublicKey, registerRoutes)))
);
```

`signatureValidation` captures raw request bytes for hashing, `nodeAdapter` bridges the RPC transport to Node.js HTTP, and `createService` registers your handlers with signature verification.
</details>

### Standalone Signature Verification

For frameworks that don't use Node's `http.createServer` (Effect, Koa, Fastify, etc.), use `createRequestVerifier` to verify inbound requests with just the raw body bytes and headers:

```ts
import { fromBinary, toBinary } from "@bufbuild/protobuf";
import { createRequestVerifier, rejectRequest, PayoutRequestSchema, PayoutResponseSchema } from "@t-0/provider-sdk";

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
    // rejectRequest maps the failure reason to a well-formed HTTP error
    // with status (400 or 401), Content-Type header, and JSON body.
    const rejected = rejectRequest(result.reason);
    return errorResponse(rejected.status, rejected.headers, rejected.body);
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

The lower-level primitives are also exported individually: `verifySignature`, `computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`. You can also import just the crypto module via the `./crypto` subpath: `import { createRequestVerifier } from "@t-0/provider-sdk/crypto"`.

**Important constraints for standalone integrations:**

- **Raw body bytes only.** Pass the exact wire bytes to the verifier — no body parsers, no auto-decompression, never re-serialized protobuf. Protobuf encoding is not canonical; re-encoding produces different bytes and breaks verification.
- **Pass `Uint8Array`, not `ArrayBuffer`.** If your framework gives you an `ArrayBuffer` (e.g. `request.arrayBuffer()`), wrap it: `new Uint8Array(buf)`.
- **Header case.** `NetworkHeaders` enum values are title-case (`X-Signature`), but Node lowercases incoming headers. Look up headers by lowercase name: `headers["x-signature"]`.
- **Wire format is binary protobuf.** Requests and successful responses use `Content-Type: application/proto`. Deserialize with `fromBinary()`, serialize with `toBinary()` from `@bufbuild/protobuf`. For verification errors, use `rejectRequest(result.reason)` to get the correct HTTP status, headers, and JSON body. Success responses **must** include `Content-Type: application/proto`.
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
