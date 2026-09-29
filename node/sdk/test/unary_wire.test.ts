import { describe, it, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { gzipSync } from 'node:zlib';
import { create, fromBinary, fromJsonString, toBinary } from '@bufbuild/protobuf';
import { StringValueSchema } from '@bufbuild/protobuf/wkt';
import { Code, ConnectError, createClient as createConnectClient, type Transport } from '@connectrpc/connect';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createConnectTransport } from '@connectrpc/connect-web';
import { createClient, type SignerFunction } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { signatureHeaders } from '../src/common/client/sign.js';
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
const BASE_URL = 'http://127.0.0.1:9'; // never dialed: every test here sends through a fake fetch

// The call every path makes: a health check with a non-empty body, a call header and a timeout.
const REQUEST = { service: 'grpc.health.v1.Health' };
const CALL_OPTIONS = { headers: { 'X-Call': 'golden' }, timeoutMs: 5_000 };

// What the connect-web transport (unary, Connect JSON) sent for this call, captured from it before
// it was removed. Headers are listed without the three signature headers, whose values depend on
// the key and the time.
const LEGACY = {
  body: '{"service":"grpc.health.v1.Health"}',
  headers: {
    'connect-protocol-version': '1',
    'connect-timeout-ms': '5000',
    'content-type': 'application/json',
    'x-call': 'golden',
  },
};

// What the SDK sends for it now: the same Connect request in binary.
const CURRENT = {
  body: '0a15' + Buffer.from('grpc.health.v1.Health').toString('hex'),
  headers: { ...LEGACY.headers, 'content-type': 'application/proto' },
};

interface Sent {
  url: string;
  init: RequestInit & { duplex?: unknown };
  body: Buffer;
}

// A fetch that records each request and answers a serving health check in the request's format.
function recordingFetch(sent: Sent[]): typeof globalThis.fetch {
  return async (url, init) => {
    const body = init?.body == null ? Buffer.alloc(0) : Buffer.from(await new Response(init.body).arrayBuffer());
    sent.push({ url: String(url), init: init ?? {}, body });
    const serving = create(HealthCheckResponseSchema, { status: HealthCheckResponse_ServingStatus.SERVING });
    return new Headers(init?.headers).get('Content-Type') === 'application/json'
      ? new Response('{"status":"SERVING"}', { headers: { 'Content-Type': 'application/json' } })
      : new Response(toBinary(HealthCheckResponseSchema, serving), { headers: { 'Content-Type': 'application/proto' } });
  };
}

// The unary transport createClient used before: connect-web over a fetch that signed init.body.
function legacyTransport(sign: SignerFunction, fetchFn: typeof globalThis.fetch): Transport {
  const customFetch: typeof globalThis.fetch = async (r, init) => {
    if (!init?.body || !(init.body instanceof Uint8Array)) {
      throw 'unsupported body type';
    }
    const headers = new Headers(init?.headers);
    for (const [name, value] of await signatureHeaders(sign, init.body)) {
      headers.append(name, value);
    }
    return fetchFn(r, { ...init, headers });
  };
  return createConnectTransport({ baseUrl: BASE_URL, fetch: customFetch });
}

function currentTransport(sign: SignerFunction, fetchFn: typeof globalThis.fetch): Transport {
  return createTransport({ ...transportOptions(sign, BASE_URL), httpClient: createSigningFetchClient(sign, fetchFn) });
}

async function check(t: TestContext, transport: (sign: SignerFunction, fetchFn: typeof globalThis.fetch) => Transport): Promise<Sent> {
  t.mock.method(Date, 'now', () => TIMESTAMP_MS);
  const sent: Sent[] = [];
  const client = createConnectClient(Health, transport(signer, recordingFetch(sent)));
  const resp = await client.check(REQUEST, CALL_OPTIONS);
  assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
  assert.equal(sent.length, 1);
  return sent[0];
}

// The headers of a request without the signature headers, and without the User-Agent, which only
// the current transport sets.
function plainHeaders(init: RequestInit): Record<string, string> {
  const headers = new Headers(init.headers);
  for (const name of [NetworkHeaders.Signature, NetworkHeaders.PublicKey, NetworkHeaders.SignatureTimestamp, 'User-Agent']) {
    headers.delete(name);
  }
  return Object.fromEntries(headers);
}

// Checks that the signature headers are the vector key's signature over exactly the bytes sent.
function assertSignedOverBody(s: Sent): void {
  const headers = new Headers(s.init.headers);
  assert.equal(headers.get(NetworkHeaders.PublicKey), '0x' + vectors.keys.public_key);
  assert.equal(headers.get(NetworkHeaders.SignatureTimestamp), String(TIMESTAMP_MS));
  const signature = Buffer.from(headers.get(NetworkHeaders.Signature)!.replace(/^0x/, ''), 'hex');
  const publicKey = parsePublicKey(vectors.keys.public_key);
  assert.ok(verifySignature(publicKey, computeDigest(s.body, TIMESTAMP_MS), signature), 'the signature covers the body as sent');
}

describe('Unary request on the wire (golden)', () => {
  it('the connect-web transport sent the recorded request', async (t) => {
    const s = await check(t, legacyTransport);
    assert.equal(s.url, `${BASE_URL}/grpc.health.v1.Health/Check`);
    assert.equal(s.init.method, 'POST');
    assert.equal(s.init.redirect, 'error');
    assert.equal(s.body.toString(), LEGACY.body);
    assert.deepEqual(plainHeaders(s.init), LEGACY.headers);
    assert.equal(new Headers(s.init.headers).get('User-Agent'), null);
    assertSignedOverBody(s);
  });

  it('the SDK sends the same Connect request in binary, signed over the whole body', async (t) => {
    const s = await check(t, currentTransport);
    assert.equal(s.url, `${BASE_URL}/grpc.health.v1.Health/Check`);
    assert.equal(s.init.method, 'POST');
    assert.equal(s.init.redirect, 'error');
    assert.equal(s.body.toString('hex'), CURRENT.body);
    assert.deepEqual(plainHeaders(s.init), CURRENT.headers);
    assert.match(new Headers(s.init.headers).get('User-Agent') ?? '', /^connect-es\//);
    assertSignedOverBody(s);

    // One buffer, not a stream: fetch sends it with a Content-Length, as connect-web did.
    assert.ok(s.init.body instanceof Uint8Array);
    assert.equal(s.init.duplex, undefined);
  });

  it('both carry the same message', () => {
    assert.deepEqual(
      fromBinary(HealthCheckRequestSchema, Buffer.from(CURRENT.body, 'hex')),
      fromJsonString(HealthCheckRequestSchema, LEGACY.body),
    );
  });
});

// Sends one request through the signing HTTP client with a fake fetch.
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

describe('The signing HTTP client signs a body that is not enveloped whole', () => {
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

  // Two envelopes in one chunk: an enveloped content type would fail the call (one envelope per
  // chunk), any other is signed whole.
  const twoEnvelopes = Buffer.from('00000000010a' + '00000000010b', 'hex');
  for (const contentType of ['application/proto', 'application/json', 'application/grpc-web+proto']) {
    it(`signs ${contentType} whole`, async (t) => {
      const s = await sendThroughSigningClient(t, contentType, 'POST', [twoEnvelopes]);
      assert.equal(s.body.toString('hex'), twoEnvelopes.toString('hex'));
      assertSignedOverBody(s);
    });
  }
  for (const contentType of ['application/connect+proto', 'Application/Connect+Proto; charset=utf-8', 'application/grpc', 'application/grpc+proto']) {
    it(`signs ${contentType} over its first envelope`, async (t) => {
      await assert.rejects(sendThroughSigningClient(t, contentType, 'POST', [twoEnvelopes]), /not one complete envelope/);
    });
  }
});

// Answers every request with one gzip-encoded response, the way connect-go answers a unary call
// from fetch: fetch asks for gzip itself (Accept-Encoding), and connect-go compresses every unary
// response it may.
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
