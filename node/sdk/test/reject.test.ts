import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import {
  rejectRequest,
  createRequestVerifier,
  computeDigest,
  type VerifyRequestFailure,
} from '../src/index.js';

describe('rejectRequest', () => {
  const badRequestReasons: VerifyRequestFailure[] = [
    'invalid_timestamp',
    'timestamp_out_of_range',
    'invalid_public_key',
    'invalid_signature_format',
  ];

  const unauthenticatedReasons: VerifyRequestFailure[] = [
    'unknown_public_key',
    'signature_failed',
  ];

  for (const reason of badRequestReasons) {
    it(`maps "${reason}" to 400`, () => {
      const result = rejectRequest(reason);
      assert.equal(result.status, 400);
      assert.equal(result.headers['Content-Type'], 'application/json');
      const body = JSON.parse(result.body);
      assert.equal(body.code, 'invalid_argument');
      assert.ok(body.message.length > 0);
    });
  }

  for (const reason of unauthenticatedReasons) {
    it(`maps "${reason}" to 401`, () => {
      const result = rejectRequest(reason);
      assert.equal(result.status, 401);
      assert.equal(result.headers['Content-Type'], 'application/json');
      const body = JSON.parse(result.body);
      assert.equal(body.code, 'unauthenticated');
      assert.ok(body.message.length > 0);
    });
  }

  it('round-trips with createRequestVerifier against a raw http server', async () => {
    const priv = Uint8Array.from(randomBytes(32));
    const pub = secp256k1.getPublicKey(priv, false);
    const pubHex = '0x' + Buffer.from(pub).toString('hex');

    const verify = createRequestVerifier({ networkPublicKey: pubHex });

    const server = http.createServer((req, res) => {
      const chunks: Buffer[] = [];
      req.on('data', (c) => chunks.push(c));
      req.on('end', () => {
        const body = Buffer.concat(chunks);
        const result = verify({
          body,
          signatureHeader: req.headers['x-signature'] as string,
          publicKeyHeader: req.headers['x-public-key'] as string,
          timestampHeader: req.headers['x-signature-timestamp'] as string,
        });

        if (!result.valid) {
          const rejected = rejectRequest(result.reason);
          res.writeHead(rejected.status, rejected.headers);
          res.end(rejected.body);
          return;
        }

        res.writeHead(200);
        res.end('ok');
      });
    });

    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const { port } = server.address() as AddressInfo;
    const baseUrl = `http://127.0.0.1:${port}`;

    try {
      // Valid signed request should pass
      const payload = Buffer.from('hello');
      const ts = Date.now();
      const digest = computeDigest(payload, ts);
      const sig = secp256k1.sign(digest, priv, { prehash: false });
      const sigBytes = Buffer.from(sig).subarray(0, 64);
      const sigHex = '0x' + sigBytes.toString('hex');

      const okResp = await fetch(baseUrl, {
        method: 'POST',
        body: payload,
        headers: {
          'x-signature': sigHex,
          'x-public-key': pubHex,
          'x-signature-timestamp': String(ts),
        },
      });
      assert.equal(okResp.status, 200);

      // Invalid signature should get a mapped rejection
      const badResp = await fetch(baseUrl, {
        method: 'POST',
        body: payload,
        headers: {
          'x-signature': '0x' + '00'.repeat(64),
          'x-public-key': pubHex,
          'x-signature-timestamp': String(ts),
        },
      });
      assert.equal(badResp.status, 401);
      const badBody = await badResp.json();
      assert.equal(badBody.code, 'unauthenticated');
    } finally {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
});
