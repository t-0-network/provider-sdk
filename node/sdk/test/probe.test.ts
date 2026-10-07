import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import http2 from 'node:http2';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import fs from 'node:fs';
import type { AddressInfo } from 'node:net';
import { create, toBinary } from '@bufbuild/protobuf';
import type { ServiceImpl } from '@connectrpc/connect';
import {
  ApprovePaymentQuoteRequestSchema,
  ApprovePaymentQuoteResponseSchema,
  PayoutRequestSchema,
  PayoutResponseSchema,
  PayoutResponse_FailedSchema,
  ProviderService,
  createHandler,
  createRequestDecoder,
  createRequestVerifier,
  rejectRequest,
  type Router,
} from '../src/index.js';
import {
  HealthCheckRequestSchema,
  HealthCheckResponseSchema,
  HealthCheckResponse_ServingStatus,
} from '../src/common/health_pb.js';
import { SDK_ECOSYSTEM_HEADER, SDK_VERSION_HEADER } from '../src/service/health.js';
import { SDK_VERSION } from '../src/version.js';

const execFileAsync = promisify(execFile);

const CROSS_TEST = path.resolve(import.meta.dirname, '..', '..', '..', 'cross_test');
const GO_HELPER = path.join(CROSS_TEST, 'go_helper', 'go_helper');
const VECTORS = path.join(CROSS_TEST, 'test_vectors.json');

const goAvailable = fs.existsSync(GO_HELPER);
if (!goAvailable && process.env.CI) {
  throw new Error(`Go helper binary required in CI but not found at ${GO_HELPER}`);
}

const vectors = JSON.parse(fs.readFileSync(VECTORS, 'utf8'));
const networkPublicKey = '0x' + vectors.keys.public_key;

// Runs every case of server_cases in cross_test/test_vectors.json against a server and fails with
// the probe's report if any case answers differently.
async function probe(url: string, protocol: 'connect' | 'grpc') {
  try {
    await execFileAsync(GO_HELPER, ['probe', url, '--sdk', 'node', '--protocol', protocol, '--vectors', VECTORS], {
      maxBuffer: 16 * 1024 * 1024,
    });
  } catch (e) {
    const err = e as { stdout?: string; stderr?: string };
    assert.fail(`probe failed:\n${err.stdout ?? ''}${err.stderr ?? ''}`);
  }
}

// A ProviderService whose responses fail response validation: ApprovePaymentQuotes returns an empty
// response (server case response-invalid), PayOut one whose failed.details is too long
// (response-invalid-nested-field).
const approvePaymentQuotesPath = `/${ProviderService.typeName}/ApprovePaymentQuotes`;
const payOutPath = `/${ProviderService.typeName}/PayOut`;
const invalidPayout = create(PayoutResponseSchema, {
  result: { case: 'failed', value: create(PayoutResponse_FailedSchema, { details: 'x'.repeat(1025) }) },
});
const registerRoutes = (router: Router) =>
  router.service(ProviderService, {
    approvePaymentQuotes: () => create(ApprovePaymentQuoteResponseSchema),
    payOut: () => invalidPayout,
  } as unknown as ServiceImpl<typeof ProviderService>);

// The Node column of the shared server behavior: createHandler behind node:http (Connect) and
// node:http2 (gRPC; a plaintext node:http2 server speaks HTTP/2 only).
describe('shared server cases', { skip: goAvailable ? undefined : `Go helper not found at ${GO_HELPER}` }, () => {
  it('Connect over HTTP/1.1', async () => {
    const server = http.createServer(createHandler(networkPublicKey, registerRoutes));
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
      await probe(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, 'connect');
    } finally {
      server.closeAllConnections();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });

  it('gRPC over HTTP/2', async () => {
    const server = http2.createServer(createHandler(networkPublicKey, registerRoutes));
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
      await probe(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, 'grpc');
    } finally {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
});

// The server cases that createRequestDecoder does not run, each with why.
const notForDecoder: Record<string, string> = {
  'body-over-limit': 'the application reads the body; the decoder gets it whole and has no limit',
  'body-11-mib': 'the application reads the body; the decoder gets it whole and has no limit',
  'order-headers-before-body-size': 'the application reads the whole body before the decoder sees the headers',
  'valid-grpc-signed-without-prefix': 'gRPC only; rejectRequest and the decoder answer over Connect',
  'health-unknown-service': 'the application answers Health/Check; the helpers mount no health service',
};

// The server cases that createRequestVerifier does not run: those, and one more.
const notForVerifier: Record<string, string> = {
  ...notForDecoder,
  'response-invalid': 'the verifier does not see the response; encodeResponse validates it',
  'response-invalid-nested-field': 'the verifier does not see the response; encodeResponse validates it',
};

// Runs server_cases over Connect against a server and checks that every case not in skip passes.
// The probe has no option to leave cases out, so its per-case lines are read instead of its exit
// status.
async function probeHelpers(url: string, skip: Record<string, string>) {
  let stdout: string;
  try {
    ({ stdout } = await execFileAsync(GO_HELPER, ['probe', url, '--sdk', 'node', '--protocol', 'connect', '--vectors', VECTORS], {
      maxBuffer: 16 * 1024 * 1024,
    }));
  } catch (e) {
    stdout = (e as { stdout?: string }).stdout ?? '';
  }
  const lines = new Map<string, string>();
  for (const line of stdout.split('\n')) {
    const m = /^(PASS|FAIL) (\S+) \[connect\]/.exec(line);
    if (m) {
      lines.set(m[2], line);
    }
  }
  const names = new Set<string>(vectors.server_cases.map((c: { name: string }) => c.name));
  for (const name of Object.keys(skip)) {
    assert.ok(names.has(name), `${name} is not a server case`);
  }
  for (const name of names) {
    if (name in skip) {
      continue;
    }
    assert.match(lines.get(name) ?? `no answer for ${name}:\n${stdout}`, /^PASS /);
  }
}

const serving = toBinary(
  HealthCheckResponseSchema,
  create(HealthCheckResponseSchema, { status: HealthCheckResponse_ServingStatus.SERVING }),
);

// The application answers Health/Check itself here, so it sends the SDK identity headers the
// network reads on every health reply (docs/HEALTH_SERVICE.md), as createHandler does.
const identity = { [SDK_ECOSYSTEM_HEADER]: 'node', [SDK_VERSION_HEADER]: SDK_VERSION };

// A node:http server that reads each request's whole body and answers it with respond.
async function withServer(
  respond: (req: http.IncomingMessage, body: Buffer, res: http.ServerResponse) => void,
  run: (url: string) => Promise<void>,
) {
  const server = http.createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => respond(req, Buffer.concat(chunks), res));
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await run(`http://127.0.0.1:${(server.address() as AddressInfo).port}`);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

// The Node-only helpers give the server's answers (DECISIONS §8): an application that verifies with
// createRequestVerifier and rejects with rejectRequest, and one that decodes with createRequestDecoder.
describe('shared server cases through the request helpers', { skip: goAvailable ? undefined : `Go helper not found at ${GO_HELPER}` }, () => {
  it('createRequestVerifier and rejectRequest', async () => {
    const verify = createRequestVerifier({ networkPublicKey });
    await withServer(({ headers }, body, res) => {
      const result = verify({
        body,
        publicKeyHeader: headers['x-public-key'] as string,
        signatureHeader: headers['x-signature'] as string,
        timestampHeader: headers['x-signature-timestamp'] as string,
      });
      if (!result.valid) {
        const rejected = rejectRequest(result.reason, result.message);
        res.writeHead(rejected.status, rejected.headers).end(rejected.body);
        return;
      }
      res.writeHead(200, { 'Content-Type': 'application/proto', ...identity }).end(serving);
    }, (url) => probeHelpers(url, notForVerifier));
  });

  it('createRequestDecoder', async () => {
    const decode = createRequestDecoder({ networkPublicKey });
    await withServer(({ url, headers }, body, res) => {
      if (url === approvePaymentQuotesPath) {
        const result = decode(ApprovePaymentQuoteRequestSchema, { body, headers });
        if (!result.ok) {
          res.writeHead(result.error.status, result.error.headers).end(result.error.body);
          return;
        }
        const reply = result.encodeResponse(ApprovePaymentQuoteResponseSchema, create(ApprovePaymentQuoteResponseSchema));
        res.writeHead(reply.status, reply.headers).end(reply.body);
        return;
      }
      if (url === payOutPath) {
        const result = decode(PayoutRequestSchema, { body, headers });
        if (!result.ok) {
          res.writeHead(result.error.status, result.error.headers).end(result.error.body);
          return;
        }
        const reply = result.encodeResponse(PayoutResponseSchema, invalidPayout);
        res.writeHead(reply.status, reply.headers).end(reply.body);
        return;
      }
      const result = decode(HealthCheckRequestSchema, { body, headers });
      if (!result.ok) {
        res.writeHead(result.error.status, result.error.headers).end(result.error.body);
        return;
      }
      const reply = result.encodeResponse(
        HealthCheckResponseSchema,
        create(HealthCheckResponseSchema, { status: HealthCheckResponse_ServingStatus.SERVING }),
      );
      res.writeHead(reply.status, { ...reply.headers, ...identity }).end(reply.body);
    }, (url) => probeHelpers(url, notForDecoder));
  });
});
