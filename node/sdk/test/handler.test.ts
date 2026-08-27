import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { createClient } from '../src/client/client.js';
import { createHandler } from '../src/index.js';
import { SDK_VERSION } from '../src/version.js';
import { SDK_VERSION_HEADER } from '../src/service/health.js';
import {
  Health,
  HealthCheckResponse_ServingStatus,
} from '../src/service/health_pb.js';
import {
  ConnectError,
  Code,
  createClient as createConnectClient,
} from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-web';
import { secp256k1 } from '@noble/curves/secp256k1.js';

function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return {
    privateKeyHex: '0x' + Buffer.from(priv).toString('hex'),
    publicKeyHex: '0x' + Buffer.from(pub).toString('hex'),
  };
}

async function bootServer(
  networkPublicKeyHex: string,
): Promise<{ url: string; close: () => Promise<void> }> {
  const handler = createHandler(networkPublicKeyHex, () => {});
  const server = http.createServer(handler);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

describe('createHandler', () => {
  it('signed health check returns SERVING', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const client = createClient(privateKeyHex, url, Health);
      const resp = await client.check({ service: '' });
      assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
    } finally {
      await close();
    }
  });

  it('stamps T0-Sdk-Version header equal to SDK_VERSION', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const client = createClient(privateKeyHex, url, Health);
      const headers = new Headers();
      await client.check({ service: '' }, { onHeader: (h) => h.forEach((v, k) => headers.set(k, v)) });
      assert.equal(headers.get(SDK_VERSION_HEADER.toLowerCase()), SDK_VERSION);
    } finally {
      await close();
    }
  });

  it('unsigned request is rejected', async () => {
    const { publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const transport = createConnectTransport({ baseUrl: url, fetch: globalThis.fetch });
      const client = createConnectClient(Health, transport);
      await assert.rejects(
        async () => client.check({ service: '' }),
        (err: unknown) => {
          assert.ok(err instanceof ConnectError);
          assert.equal((err as ConnectError).code, Code.InvalidArgument);
          return true;
        },
      );
    } finally {
      await close();
    }
  });
});
