import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { connectNodeAdapter } from '@connectrpc/connect-node';
import { createClient } from '../src/client/client.js';
import { createService } from '../src/service/service.js';
import { createSystemServiceImpl } from '../src/service/system.js';
import { signatureValidation } from '../src/service/node.js';
import { SDK_VERSION } from '../src/version.js';
import {
  HealthRequestSchema,
  SdkEcosystem,
  SystemService,
} from '../src/common/gen/tzero/v1/system/system_pb.js';
import { create } from '@bufbuild/protobuf';
import { ConnectError, Code, createClient as createConnectClient } from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-web';

function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return {
    privateKeyHex: '0x' + Buffer.from(priv).toString('hex'),
    publicKeyHex: '0x' + Buffer.from(pub).toString('hex'),
  };
}

async function bootServer(networkPublicKeyHex: string): Promise<{ url: string; close: () => Promise<void> }> {
  const handler = connectNodeAdapter(
    createService(networkPublicKeyHex, () => {
      // No customer services — SystemService still gets auto-registered.
    }),
  );
  const server = http.createServer(signatureValidation(handler));
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

describe('SystemService impl', () => {
  it('Health response shape', () => {
    const impl = createSystemServiceImpl(['example.v1.Foo', SystemService.typeName]);
    const resp = impl.health(create(HealthRequestSchema, {}));
    assert.deepEqual(resp.services, ['example.v1.Foo', SystemService.typeName]);
    assert.equal(resp.sdkVersion, SDK_VERSION);
    assert.equal(resp.sdkEcosystem, SdkEcosystem.NODE);
    assert.ok(resp.currentTime, 'currentTime is set');
    const skewMs = Math.abs(Date.now() - Number(resp.currentTime!.seconds) * 1000);
    assert.ok(skewMs < 5_000, `currentTime skew = ${skewMs}ms`);
  });
});

describe('SystemService auto-registration', () => {
  it('signed Health call returns SystemService FQN and Node ecosystem', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const client = createClient(privateKeyHex, url, SystemService);
      const resp = await client.health({});
      assert.ok(
        resp.services.includes(SystemService.typeName),
        `services missing SystemService: ${JSON.stringify(resp.services)}`,
      );
      assert.equal(resp.sdkVersion, SDK_VERSION);
      assert.equal(resp.sdkEcosystem, SdkEcosystem.NODE);
      assert.ok(resp.currentTime);
    } finally {
      await close();
    }
  });

  it('unsigned Health call is rejected with InvalidArgument', async () => {
    const { publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const transport = createConnectTransport({ baseUrl: url, fetch: globalThis.fetch });
      const client = createConnectClient(SystemService, transport);
      await assert.rejects(
        async () => client.health({}),
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
