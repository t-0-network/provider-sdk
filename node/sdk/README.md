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

### Standalone Request Decoding

For frameworks that don't use Node's `http.createServer` (Hono, Effect, Koa, Fastify, etc.), use `createNetworkRequestDecoder` for one-call signature verification + Content-Type-aware decoding + protovalidation. It returns an either-type result: success with the decoded message and a response encoder, or failure with a ready-to-send HTTP error.

```ts
import { createNetworkRequestDecoder, PayoutRequestSchema, PayoutResponseSchema } from "@t-0/provider-sdk";

const decode = createNetworkRequestDecoder({
  networkPublicKey: process.env.NETWORK_PUBLIC_KEY!,
});

// Hono / fetch-shaped framework:
app.post("/payout", async (c) => {
  const body = new Uint8Array(await c.req.arrayBuffer());
  const result = decode(PayoutRequestSchema, { body, headers: c.req.raw.headers });

  if (!result.ok) {
    return new Response(result.error.body, {
      status: result.error.status,
      headers: result.error.headers,
    });
  }

  const response = handlePayout(result.request);

  // encodeResponse validates + encodes in the matching wire format (JSON or proto)
  const wire = result.encodeResponse(PayoutResponseSchema, response);
  return new Response(wire.body, { status: wire.status, headers: wire.headers });
});
```

```ts
// Raw Node http example:
import http from "node:http";
import { createNetworkRequestDecoder, PayoutRequestSchema, PayoutResponseSchema } from "@t-0/provider-sdk";

const decode = createNetworkRequestDecoder({
  networkPublicKey: process.env.NETWORK_PUBLIC_KEY!,
});

http.createServer((req, res) => {
  const chunks: Buffer[] = [];
  req.on("data", (c) => chunks.push(c));
  req.on("end", () => {
    const body = Buffer.concat(chunks);
    const result = decode(PayoutRequestSchema, { body, headers: req.headers });

    if (!result.ok) {
      res.writeHead(result.error.status, result.error.headers);
      res.end(result.error.body);
      return;
    }

    const response = handlePayout(result.request);
    const wire = result.encodeResponse(PayoutResponseSchema, response);
    res.writeHead(wire.status, wire.headers);
    res.end(wire.body);
  });
}).listen(3000);
```

The decoder accepts both fetch `Headers` and Node's `Record<string, string | string[] | undefined>`. It normalizes header case internally, detects Content-Type (`application/json` or `application/proto` / `application/protobuf` / `application/x-protobuf`), and the returned `encodeResponse` closure responds in the matching format.

For custom proto registries (e.g. non-network schemas with custom predefined rules), use the generic `createRequestDecoder` from `@t-0/provider-sdk/crypto` and pass your own `registry`.

**Important constraints for standalone integrations:**

- **Raw body bytes only.** Pass the exact wire bytes — no body parsers, no auto-decompression, never re-serialized protobuf. Protobuf encoding is not canonical; re-encoding produces different bytes and breaks verification.
- **Health endpoint.** The T-0 Network probes `/grpc.health.v1.Health/Check` on every endpoint. The probe is signed. Standalone integrations must route this path and return a valid health response. See [`docs/HEALTH_SERVICE.md`](../../docs/HEALTH_SERVICE.md) for the wire contract.
- **`DecodeRequestFailure` is an open union.** New error shapes may be added without a major version bump. Handle unknown failures as generic errors.

<details>
<summary>Lower-level primitives</summary>

The individual building blocks are also exported: `createRequestVerifier`, `rejectRequest`, `verifySignature`, `computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`. You can import just the crypto module via the `./crypto` subpath: `import { createRequestVerifier } from "@t-0/provider-sdk/crypto"`.
</details>

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
