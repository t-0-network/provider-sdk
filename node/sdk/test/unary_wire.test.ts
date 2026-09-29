import { describe, it, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { create, fromBinary, fromJsonString, toBinary } from '@bufbuild/protobuf';
import { StringValueSchema } from '@bufbuild/protobuf/wkt';
import { createClient as createConnectClient } from '@connectrpc/connect';
import type { UniversalClientFn } from '@connectrpc/connect/protocol';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createClient } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { createSigningHttpClient } from '../src/common/client/signing-http-client.js';
import { computeDigest, NetworkHeaders, parsePublicKey, verifySignature } from '../src/crypto/index.js';
import {
  Health,
  HealthCheckRequestSchema,
  HealthCheckResponseSchema,
  HealthCheckResponse_ServingStatus,
} from '../src/service/health_pb.js';
import { StreamTest, stringValues } from './stream_helpers.js';

const vectors = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../../cross_test/test_vectors.json'), 'utf-8'));
const signer = CreateSigner(vectors.keys.private_key);
const TIMESTAMP_MS = 1_706_000_000_000;
const BASE_URL = 'http://127.0.0.1:9'; // never dialed: only used with a recording client

const REQUEST = { service: 'grpc.health.v1.Health' };
const CALL_OPTIONS = { headers: { 'X-Call': 'golden' }, timeoutMs: 5_000 };

// What connect-web sent for this call, recorded before it was removed (without signature headers).
// fetch added Content-Length on the wire; the SDK now sets it itself (LEGACY_ON_THE_WIRE).
const LEGACY = {
  body: '{"service":"grpc.health.v1.Health"}',
  headers: {
    'connect-protocol-version': '1',
    'connect-timeout-ms': '5000',
    'content-type': 'application/json',
    'x-call': 'golden',
  },
};

const LEGACY_ON_THE_WIRE = { ...LEGACY.headers, 'content-length': String(LEGACY.body.length) };

const CURRENT = {
  body: '0a15' + Buffer.from('grpc.health.v1.Health').toString('hex'),
  headers: { ...LEGACY.headers, 'content-type': 'application/proto', 'content-length': '23' },
};

interface Sent {
  url: string;
  method: string;
  header: Headers;
  hasBody: boolean;
  chunks: Buffer[];
  body: Buffer;
}

// Records what the signing client hands the HTTP client and answers SERVING.
function recordingClient(sent: Sent[]): UniversalClientFn {
  return async (req) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req.body ?? []) {
      chunks.push(Buffer.from(chunk));
    }
    const header = new Headers(req.header);
    sent.push({ url: req.url, method: req.method, header, hasBody: req.body !== undefined, chunks, body: Buffer.concat(chunks) });
    const json = header.get('Content-Type') === 'application/json';
    const serving = create(HealthCheckResponseSchema, { status: HealthCheckResponse_ServingStatus.SERVING });
    const body = json ? Buffer.from('{"status":"SERVING"}') : toBinary(HealthCheckResponseSchema, serving);
    return {
      status: 200,
      header: new Headers({ 'Content-Type': json ? 'application/json' : 'application/proto' }),
      body: (async function* () { yield body; })(),
      trailer: new Headers(),
    };
  };
}

async function check(t: TestContext, useBinaryFormat = true): Promise<Sent> {
  t.mock.method(Date, 'now', () => TIMESTAMP_MS);
  const sent: Sent[] = [];
  const transport = createTransport({
    ...transportOptions(signer, BASE_URL, undefined, useBinaryFormat),
    httpClient: createSigningHttpClient(signer, recordingClient(sent)),
  });
  const client = createConnectClient(Health, transport);
  const resp = await client.check(REQUEST, CALL_OPTIONS);
  assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
  assert.equal(sent.length, 1);
  return sent[0];
}

// Without the signature headers and without User-Agent, which connect-web did not send.
function plainHeaders(header: Headers): Record<string, string> {
  const headers = new Headers(header);
  for (const name of [NetworkHeaders.Signature, NetworkHeaders.PublicKey, NetworkHeaders.SignatureTimestamp, 'User-Agent']) {
    headers.delete(name);
  }
  return Object.fromEntries(headers);
}

// One chunk, announced by its Content-Length.
function assertOneChunk(s: Sent): void {
  assert.equal(s.chunks.length, 1);
  assert.equal(s.header.get('Content-Length'), String(s.body.length));
}

function assertSignedOverBody(s: Sent): void {
  const headers = s.header;
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
    assert.equal(s.method, 'POST');
    assert.equal(s.body.toString('hex'), CURRENT.body);
    assert.deepEqual(plainHeaders(s.header), CURRENT.headers);
    assert.match(s.header.get('User-Agent') ?? '', /^connect-es\//);
    assertSignedOverBody(s);
    assertOneChunk(s);
  });

  it('with useBinaryFormat: false the SDK sends the request connect-web sent', async (t) => {
    const s = await check(t, false);
    assert.equal(s.url, `${BASE_URL}/grpc.health.v1.Health/Check`);
    assert.equal(s.body.toString(), LEGACY.body);
    assert.deepEqual(plainHeaders(s.header), LEGACY_ON_THE_WIRE);
    assertSignedOverBody(s);
    assertOneChunk(s);
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
  await createSigningHttpClient(signer, recordingClient(sent))({
    url: `${BASE_URL}/test.v1.StreamTest/Unary`,
    method,
    header: new Headers({ 'Content-Type': contentType }),
    body: chunks === undefined ? undefined : (async function* () { yield* chunks; })(),
  });
  assert.equal(sent.length, 1);
  return sent[0];
}

describe('The signing HTTP client signs a body whole unless its content type is enveloped', () => {
  it('joins a body of several chunks, signs it and sends it as one chunk', async (t) => {
    const s = await sendThroughSigningClient(t, 'application/proto', 'POST', [Buffer.from('0a05', 'hex'), Buffer.from('hello')]);
    assert.equal(s.body.toString('hex'), '0a0568656c6c6f');
    assertOneChunk(s);
    assertSignedOverBody(s);
  });

  it('signs a request without a body over empty bytes and sends it without one', async (t) => {
    const s = await sendThroughSigningClient(t, 'application/proto', 'GET');
    assert.equal(s.hasBody, false);
    assert.equal(s.header.get('Content-Length'), null);
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

  // connect-node throws into the body when a write fails.
  for (const close of ['return', 'throw'] as const) {
    it(`${close}() on a stream body reaches the iterator it reads from`, async (t) => {
      t.mock.method(Date, 'now', () => TIMESTAMP_MS);
      let closed = false;
      async function* source() {
        try {
          yield Buffer.from('00000000010a', 'hex');
          yield Buffer.from('00000000010b', 'hex');
        } finally {
          closed = true;
        }
      }
      const closing: UniversalClientFn = async (req) => {
        const body = req.body![Symbol.asyncIterator]();
        await body.next();
        await body[close]?.(new Error('closed')).catch(() => {});
        return { status: 200, header: new Headers(), body: (async function* () {})(), trailer: new Headers() };
      };
      await createSigningHttpClient(signer, closing)({
        url: `${BASE_URL}/test.v1.StreamTest/ClientStream`,
        method: 'POST',
        header: new Headers({ 'Content-Type': 'application/connect+proto' }),
        body: source(),
      });
      assert.equal(closed, true);
    });
  }
});

interface Arrived {
  headers: http.IncomingHttpHeaders;
  body: Buffer;
}

function envelope(flags: number, payload: Uint8Array): Buffer {
  const prefix = Buffer.alloc(5);
  prefix.writeUInt8(flags, 0);
  prefix.writeUInt32BE(payload.length, 1);
  return Buffer.concat([prefix, payload]);
}

// Records each request as it arrived and answers StreamTest calls with "ok"; /redirect answers 307.
async function withWireServer(fn: (url: string, arrived: Arrived[]) => Promise<void>) {
  const arrived: Arrived[] = [];
  const ok = toBinary(StringValueSchema, create(StringValueSchema, { value: 'ok' }));
  const server = http.createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req) {
      chunks.push(chunk);
    }
    arrived.push({ headers: req.headers, body: Buffer.concat(chunks) });
    if (req.url?.startsWith('/redirect/')) {
      res.writeHead(307, { Location: req.url.slice('/redirect'.length) }).end();
    } else if (req.headers['content-type'] === 'application/proto') {
      res.writeHead(200, { 'Content-Type': 'application/proto' }).end(ok);
    } else {
      res.writeHead(200, { 'Content-Type': 'application/connect+proto' }).end(Buffer.concat([envelope(0, ok), envelope(2, Buffer.from('{}'))]));
    }
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await fn(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, arrived);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

describe('On the wire (connect-node over HTTP/1.1)', () => {
  it('a unary body is sent with its Content-Length, and no Accept-Encoding', async () => {
    await withWireServer(async (url, arrived) => {
      const client = createClient(vectors.keys.private_key, url, StreamTest);
      assert.equal((await client.unary({ value: 'hello' })).value, 'ok');

      assert.equal(arrived.length, 1);
      const [a] = arrived;
      assert.equal(a.body.toString('hex'), '0a0568656c6c6f');
      assert.equal(a.headers['content-length'], String(a.body.length));
      assert.equal(a.headers['transfer-encoding'], undefined);
      assert.equal(a.headers['accept-encoding'], undefined);
    });
  });

  it('a client stream is sent chunked', async () => {
    await withWireServer(async (url, arrived) => {
      const client = createClient(vectors.keys.private_key, url, StreamTest);
      assert.equal((await client.clientStream(stringValues('m1', 'm2'))).value, 'ok');

      assert.equal(arrived.length, 1);
      const [a] = arrived;
      assert.equal(a.headers['transfer-encoding'], 'chunked');
      assert.equal(a.headers['content-length'], undefined);
      assert.equal(a.headers['accept-encoding'], undefined);
    });
  });

  it('a redirect is not followed', async () => {
    await withWireServer(async (url, arrived) => {
      const client = createClient(vectors.keys.private_key, `${url}/redirect`, StreamTest);
      await assert.rejects(client.unary({ value: 'hello' }));
      assert.equal(arrived.length, 1, 'the signed request is not sent again');
    });
  });
});
