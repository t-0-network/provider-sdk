import { describe, it, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { gzipSync } from 'node:zlib';
import { create, fromBinary, fromJsonString, toBinary } from '@bufbuild/protobuf';
import { StringValueSchema } from '@bufbuild/protobuf/wkt';
import { Code, ConnectError, createClient as createConnectClient } from '@connectrpc/connect';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createClient } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { createSigningFetchClient } from '../src/common/client/signing-http-client.js';
import { computeDigest, NetworkHeaders, parsePublicKey, verifySignature } from '../src/crypto/index.js';
import {
  Health,
  HealthCheckRequestSchema,
  HealthCheckResponseSchema,
  HealthCheckResponse_ServingStatus,
} from '../src/service/health_pb.js';
import { StreamTest } from './stream_helpers.js';

const vectors = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../../cross_test/test_vectors.json'), 'utf-8'));
const signer = CreateSigner(vectors.keys.private_key);
const TIMESTAMP_MS = 1_706_000_000_000;
const BASE_URL = 'http://127.0.0.1:9'; // never dialed: only used with a fake fetch

const REQUEST = { service: 'grpc.health.v1.Health' };
const CALL_OPTIONS = { headers: { 'X-Call': 'golden' }, timeoutMs: 5_000 };

// What connect-web sent for this call, recorded before it was removed (without signature headers).
const LEGACY = {
  body: '{"service":"grpc.health.v1.Health"}',
  headers: {
    'connect-protocol-version': '1',
    'connect-timeout-ms': '5000',
    'content-type': 'application/json',
    'x-call': 'golden',
  },
};

const CURRENT = {
  body: '0a15' + Buffer.from('grpc.health.v1.Health').toString('hex'),
  headers: { ...LEGACY.headers, 'content-type': 'application/proto' },
};

interface Sent {
  url: string;
  init: RequestInit & { duplex?: unknown };
  body: Buffer;
}

function recordingFetch(sent: Sent[]): typeof globalThis.fetch {
  return async (url, init) => {
    const body = init?.body == null ? Buffer.alloc(0) : Buffer.from(await new Response(init.body).arrayBuffer());
    sent.push({ url: String(url), init: init ?? {}, body });
    if (new Headers(init?.headers).get('Content-Type') === 'application/json') {
      return new Response('{"status":"SERVING"}', { headers: { 'Content-Type': 'application/json' } });
    }
    const serving = create(HealthCheckResponseSchema, { status: HealthCheckResponse_ServingStatus.SERVING });
    return new Response(toBinary(HealthCheckResponseSchema, serving), { headers: { 'Content-Type': 'application/proto' } });
  };
}

async function check(t: TestContext, useBinaryFormat = true): Promise<Sent> {
  t.mock.method(Date, 'now', () => TIMESTAMP_MS);
  const sent: Sent[] = [];
  const transport = createTransport({
    ...transportOptions(signer, BASE_URL, undefined, useBinaryFormat),
    httpClient: createSigningFetchClient(signer, recordingFetch(sent)),
  });
  const client = createConnectClient(Health, transport);
  const resp = await client.check(REQUEST, CALL_OPTIONS);
  assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
  assert.equal(sent.length, 1);
  return sent[0];
}

// Without the signature headers and without User-Agent, which connect-web did not send.
function plainHeaders(init: RequestInit): Record<string, string> {
  const headers = new Headers(init.headers);
  for (const name of [NetworkHeaders.Signature, NetworkHeaders.PublicKey, NetworkHeaders.SignatureTimestamp, 'User-Agent']) {
    headers.delete(name);
  }
  return Object.fromEntries(headers);
}

function assertSignedOverBody(s: Sent): void {
  const headers = new Headers(s.init.headers);
  assert.equal(headers.get(NetworkHeaders.PublicKey), '0x' + vectors.keys.public_key);
  assert.equal(headers.get(NetworkHeaders.SignatureTimestamp), String(TIMESTAMP_MS));
  const signature = Buffer.from(headers.get(NetworkHeaders.Signature)!.replace(/^0x/, ''), 'hex');
  const publicKey = parsePublicKey(vectors.keys.public_key);
  assert.ok(verifySignature(publicKey, computeDigest(s.body, TIMESTAMP_MS), signature), 'the signature covers the body as sent');
}

describe('Unary request on the wire (golden)', () => {
  it('the SDK sends the same Connect request in binary, signed over the whole body', async (t) => {
    const s = await check(t);
    assert.equal(s.url, `${BASE_URL}/grpc.health.v1.Health/Check`);
    assert.equal(s.init.method, 'POST');
    assert.equal(s.init.redirect, 'error');
    assert.equal(s.body.toString('hex'), CURRENT.body);
    assert.deepEqual(plainHeaders(s.init), CURRENT.headers);
    assert.match(new Headers(s.init.headers).get('User-Agent') ?? '', /^connect-es\//);
    assertSignedOverBody(s);

    // One buffer, not a stream, so fetch sets Content-Length as it did for connect-web.
    assert.ok(s.init.body instanceof Uint8Array);
    assert.equal(s.init.duplex, undefined);
  });

  it('with useBinaryFormat: false the SDK sends the request connect-web sent', async (t) => {
    const s = await check(t, false);
    assert.equal(s.url, `${BASE_URL}/grpc.health.v1.Health/Check`);
    assert.equal(s.body.toString(), LEGACY.body);
    assert.deepEqual(plainHeaders(s.init), LEGACY.headers);
    assertSignedOverBody(s);
    assert.ok(s.init.body instanceof Uint8Array);
  });

  it('carries the message connect-web sent', () => {
    assert.deepEqual(
      fromBinary(HealthCheckRequestSchema, Buffer.from(CURRENT.body, 'hex')),
      fromJsonString(HealthCheckRequestSchema, LEGACY.body),
    );
  });
});

async function sendThroughSigningClient(t: TestContext, contentType: string, method: string, chunks?: Uint8Array[]): Promise<Sent> {
  t.mock.method(Date, 'now', () => TIMESTAMP_MS);
  const sent: Sent[] = [];
  await createSigningFetchClient(signer, recordingFetch(sent))({
    url: `${BASE_URL}/test.v1.StreamTest/Unary`,
    method,
    header: new Headers({ 'Content-Type': contentType }),
    body: chunks === undefined ? undefined : (async function* () { yield* chunks; })(),
  });
  assert.equal(sent.length, 1);
  return sent[0];
}

describe('The signing HTTP client signs a body whole unless its content type is enveloped', () => {
  it('joins a body of several chunks, signs it and sends it as one buffer', async (t) => {
    const s = await sendThroughSigningClient(t, 'application/proto', 'POST', [Buffer.from('0a05', 'hex'), Buffer.from('hello')]);
    assert.equal(s.body.toString('hex'), '0a0568656c6c6f');
    assert.ok(s.init.body instanceof Uint8Array);
    assert.equal(s.init.duplex, undefined);
    assertSignedOverBody(s);
  });

  it('signs a request without a body over empty bytes and sends it without one', async (t) => {
    const s = await sendThroughSigningClient(t, 'application/proto', 'GET');
    assert.equal(s.init.body, undefined);
    assert.equal(s.body.length, 0);
    assertSignedOverBody(s);
  });

  // Two envelopes in one chunk: signed whole, unless the content type is enveloped and the call fails.
  const twoEnvelopes = Buffer.from('00000000010a' + '00000000010b', 'hex');
  for (const contentType of ['application/proto', 'application/json', 'application/grpc-web+proto']) {
    it(`signs ${contentType} whole`, async (t) => {
      const s = await sendThroughSigningClient(t, contentType, 'POST', [twoEnvelopes]);
      assert.equal(s.body.toString('hex'), twoEnvelopes.toString('hex'));
      assertSignedOverBody(s);
    });
  }
  for (const contentType of ['application/connect+proto', 'Application/Connect+Proto; charset=utf-8', 'application/grpc', 'application/grpc+proto']) {
    it(`treats ${contentType} as enveloped: refuses a first chunk of two envelopes`, async (t) => {
      await assert.rejects(sendThroughSigningClient(t, contentType, 'POST', [twoEnvelopes]), /not one complete envelope/);
    });
  }
});

// As connect-go answers fetch: fetch sends Accept-Encoding: gzip, connect-go gzips unary responses.
async function withGzipServer(status: number, contentType: string, body: Uint8Array, fn: (url: string) => Promise<void>) {
  const encoded = gzipSync(body);
  const server = http.createServer((req, res) => {
    req.resume();
    req.on('end', () => {
      res.writeHead(status, { 'Content-Type': contentType, 'Content-Encoding': 'gzip', 'Content-Length': encoded.length }).end(encoded);
    });
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await fn(`http://127.0.0.1:${(server.address() as AddressInfo).port}`);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

describe('A unary response that fetch decoded is read as decoded', () => {
  it('reads a gzip-encoded message', async () => {
    const message = toBinary(StringValueSchema, create(StringValueSchema, { value: 'decoded' }));
    await withGzipServer(200, 'application/proto', message, async (url) => {
      const client = createClient(vectors.keys.private_key, url, StreamTest);
      assert.equal((await client.unary({ value: 'x' })).value, 'decoded');
    });
  });

  it('reads a gzip-encoded error', async () => {
    const error = Buffer.from('{"code":"invalid_argument","message":"bad request"}');
    await withGzipServer(400, 'application/json', error, async (url) => {
      const client = createClient(vectors.keys.private_key, url, StreamTest);
      await assert.rejects(client.unary({ value: 'x' }), (err: unknown) => {
        assert.ok(err instanceof ConnectError, `want a ConnectError, got ${err}`);
        assert.equal(err.code, Code.InvalidArgument);
        assert.equal(err.rawMessage, 'bad request');
        return true;
      });
    });
  });
});
