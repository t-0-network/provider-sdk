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
  // Each reason's status, code and the text it sends when given no message.
  const expected: Record<VerifyRequestFailure, [number, string, string]> = {
    invalid_timestamp: [400, 'invalid_argument', 'invalid timestamp header: not a decimal number'],
    timestamp_out_of_range: [400, 'invalid_argument', 'timestamp is outside the allowed time window'],
    invalid_public_key: [400, 'invalid_argument', 'missing required header: X-Public-Key'],
    invalid_signature_format: [400, 'invalid_argument', 'invalid header encoding: X-Signature'],
    unknown_public_key: [401, 'unauthenticated', 'request signed with unknown public key'],
    signature_failed: [401, 'unauthenticated', 'signature verification failed'],
  };

  for (const [reason, [status, code, message]] of Object.entries(expected)) {
    it(`maps "${reason}" to ${status} ${code}`, () => {
      const result = rejectRequest(reason as VerifyRequestFailure);
      assert.equal(result.status, status);
      assert.equal(result.headers['Content-Type'], 'application/json');
      assert.deepEqual(JSON.parse(result.body), { code, message });
    });
  }

  it("sends the verifier's message", () => {
    const result = rejectRequest('invalid_timestamp', 'missing required header: X-Signature-Timestamp');
    assert.equal(result.status, 400);
    assert.deepEqual(JSON.parse(result.body), {
      code: 'invalid_argument',
      message: 'missing required header: X-Signature-Timestamp',
    });
  });

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
          const rejected = rejectRequest(result.reason, result.message);
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

      // The 0X prefix is accepted, as by the server (server_cases valid-signature-0X-prefix)
      const upperResp = await fetch(baseUrl, {
        method: 'POST',
        body: payload,
        headers: {
          'x-signature': '0X' + sigBytes.toString('hex'),
          'x-public-key': pubHex,
          'x-signature-timestamp': String(ts),
        },
      });
      assert.equal(upperResp.status, 200);

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
      assert.deepEqual(badBody, { code: 'unauthenticated', message: 'signature verification failed' });
    } finally {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
});
