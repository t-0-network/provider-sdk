import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { toJsonString, toBinary, create } from '@bufbuild/protobuf';
import {
  createRequestDecoder,
  computeDigest,
  rejectRequest,
} from '../src/index.js';
import { createRequestDecoder as createGenericDecoder } from '../src/crypto/index.js';
import {
  HealthCheckRequestSchema,
  HealthCheckResponseSchema,
  HealthCheckResponse_ServingStatus,
} from '../src/common/health_pb.js';
import {
  DecimalSchema,
} from '../src/common/gen/tzero/v1/common/common_pb.js';
import { networkRegistry } from '../src/service/validate_response.js';

function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return {
    priv,
    publicKeyHex: '0x' + Buffer.from(pub).toString('hex'),
  };
}

function sign(body: Uint8Array, priv: Uint8Array) {
  const ts = Date.now();
  const digest = computeDigest(body, ts);
  const sig = secp256k1.sign(digest, priv, { prehash: false });
  const sigBytes = Buffer.from(sig).subarray(0, 64);
  return {
    'x-signature': '0x' + sigBytes.toString('hex'),
    'x-public-key': '0x' + Buffer.from(secp256k1.getPublicKey(priv, false)).toString('hex'),
    'x-signature-timestamp': String(ts),
  };
}

describe('createRequestDecoder (generic)', () => {
  it('decodes a JSON request and encodeResponse returns JSON', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: 'test' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.format, 'json');
    assert.equal(result.request.service, 'test');

    const wire = result.encodeResponse(HealthCheckRequestSchema, msg);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers['Content-Type'], 'application/json');
    assert.equal(typeof wire.body, 'string');
  });

  it('decodes a binary proto request and encodeResponse returns proto', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: 'binary-test' });
    const protoBody = toBinary(HealthCheckRequestSchema, msg);
    const headers = { ...sign(protoBody, priv), 'content-type': 'application/proto' };

    const result = decode(HealthCheckRequestSchema, { body: protoBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.format, 'proto');
    assert.equal(result.request.service, 'binary-test');

    const wire = result.encodeResponse(HealthCheckRequestSchema, msg);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers['Content-Type'], 'application/proto');
    assert.ok(wire.body instanceof Uint8Array);
  });

  it('encodeResponse accepts a different schema than the request', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const reqMsg = create(HealthCheckRequestSchema, { service: 'test' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, reqMsg));
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;

    const respMsg = create(HealthCheckResponseSchema, {
      status: HealthCheckResponse_ServingStatus.SERVING,
    });
    const wire = result.encodeResponse(HealthCheckResponseSchema, respMsg);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers['Content-Type'], 'application/json');
    const parsed = JSON.parse(wire.body as string);
    assert.equal(parsed.status, 'SERVING');
  });

  it('accepts fetch Headers object', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: '' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const rawHeaders = sign(jsonBody, priv);
    const fetchHeaders = new Headers({
      ...rawHeaders,
      'content-type': 'application/json',
    });

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers: fetchHeaders });
    assert.equal(result.ok, true);
  });

  it('accepts Node record with string[] values', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: '' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const rawHeaders = sign(jsonBody, priv);
    const nodeHeaders: Record<string, string | string[]> = {
      ...rawHeaders,
      'content-type': ['application/json'],
    };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers: nodeHeaders });
    assert.equal(result.ok, true);
  });

  it('strips Content-Type parameters (charset)', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: '' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json; charset=utf-8' };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.format, 'json');
  });

  it('verifies and decodes raw JSON bytes with unknown fields (raw-bytes regression)', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const rawJson = '{"service":"hello","unknownField":42}';
    const jsonBody = new TextEncoder().encode(rawJson);
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.request.service, 'hello');
  });

  it('handles mixed-case record keys', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: '' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const rawHeaders = sign(jsonBody, priv);
    const mixedHeaders: Record<string, string> = {
      'X-Signature': rawHeaders['x-signature'],
      'X-Public-Key': rawHeaders['x-public-key'],
      'X-Signature-Timestamp': rawHeaders['x-signature-timestamp'],
      'Content-Type': 'application/json',
    };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers: mixedHeaders });
    assert.equal(result.ok, true);
  });

  it('rejects missing Content-Type with 415', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new TextEncoder().encode('{}');
    const headers = sign(body, priv);

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 415);
  });

  it('rejects malformed binary body with 400', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new Uint8Array([0xff, 0xff, 0xff, 0xff, 0xff]);
    const headers = { ...sign(body, priv), 'content-type': 'application/proto' };

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.equal(parsed.code, 'invalid_argument');
  });

  it('rejects bad signature with same result as rejectRequest', () => {
    const { publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new Uint8Array([1, 2, 3]);
    const headers = {
      'x-signature': '0x' + '00'.repeat(64),
      'x-public-key': publicKeyHex,
      'x-signature-timestamp': String(Date.now()),
      'content-type': 'application/json',
    };

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    const expected = rejectRequest('signature_failed');
    assert.equal(result.error.status, expected.status);
  });

  it('rejects missing signature headers cleanly', () => {
    const { publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new Uint8Array([1, 2, 3]);
    const headers = { 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.ok(result.error.status === 400 || result.error.status === 401);
  });

  it('rejects unsupported Content-Type with 415', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new TextEncoder().encode('hello');
    const headers = { ...sign(body, priv), 'content-type': 'text/plain' };

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 415);
  });

  it('rejects malformed body with 400', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const body = new TextEncoder().encode('not json {{{');
    const headers = { ...sign(body, priv), 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.equal(parsed.code, 'invalid_argument');
  });

  it('rejects contract-invalid request with 400 and violations', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({
      networkPublicKey: publicKeyHex,
      registry: networkRegistry,
    });

    const msg = create(DecimalSchema, { unscaled: BigInt(100), exponent: 99 });
    const jsonBody = new TextEncoder().encode(
      toJsonString(DecimalSchema, msg, { registry: networkRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(DecimalSchema, { body: jsonBody, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.equal(parsed.code, 'invalid_argument');
    assert.ok(Array.isArray(parsed.violations));
    assert.ok(parsed.violations.length > 0);
  });

  it('encodeResponse returns 500 on invalid response', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({
      networkPublicKey: publicKeyHex,
      registry: networkRegistry,
    });

    const validMsg = create(DecimalSchema, { unscaled: BigInt(100), exponent: -2 });
    const jsonBody = new TextEncoder().encode(
      toJsonString(DecimalSchema, validMsg, { registry: networkRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(DecimalSchema, { body: jsonBody, headers });
    if (!result.ok) {
      assert.fail('Expected decode to succeed for encodeResponse test');
    }

    const badResponse = create(DecimalSchema, { unscaled: BigInt(100), exponent: 99 });
    const wire = result.encodeResponse(DecimalSchema, badResponse);
    assert.equal(wire.status, 500);
    const parsed = JSON.parse(wire.body as string);
    assert.equal(parsed.code, 'internal');
  });
});

describe('createRequestDecoder (network-preconfigured)', () => {
  it('decodes a network message with registry-aware validation', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(DecimalSchema, { unscaled: BigInt(12345), exponent: -2 });
    const jsonBody = new TextEncoder().encode(
      toJsonString(DecimalSchema, msg, { registry: networkRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(DecimalSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.request.unscaled, BigInt(12345));
    assert.equal(result.request.exponent, -2);
  });

  it('rejects contract-invalid network message with violations', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(DecimalSchema, { unscaled: BigInt(100), exponent: 99 });
    const jsonBody = new TextEncoder().encode(
      toJsonString(DecimalSchema, msg, { registry: networkRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(DecimalSchema, { body: jsonBody, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.ok(parsed.violations.length > 0);
  });
});

describe('createRequestDecoder (generic, from crypto subpath)', () => {
  it('decodes without network registry when imported from crypto', () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createGenericDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(HealthCheckRequestSchema, { service: 'generic' });
    const jsonBody = new TextEncoder().encode(toJsonString(HealthCheckRequestSchema, msg));
    const headers = { ...sign(jsonBody, priv), 'content-type': 'application/json' };

    const result = decode(HealthCheckRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.request.service, 'generic');
  });
});
