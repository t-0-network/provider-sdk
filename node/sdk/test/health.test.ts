import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { connectNodeAdapter } from '@connectrpc/connect-node';
import { createClient } from '../src/client/client.js';
import { createService } from '../src/service/service.js';
import { SDK_ECOSYSTEM_HEADER, SDK_VERSION_HEADER } from '../src/service/health.js';
import { signatureValidation } from '../src/service/node.js';
import { SDK_VERSION } from '../src/version.js';
import {
  Health,
  HealthCheckResponse_ServingStatus,
} from '../src/service/health_pb.js';
import { ProviderService } from '../src/common/gen/tzero/v1/payment/provider_pb.js';
import {
  ConnectError,
  Code,
  createClient as createConnectClient,
  type ServiceImpl,
} from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-web';

type RegisterRoutes = Parameters<typeof createService>[1];

// Registered only so its FQN shows up in the health registry; never invoked.
const unimplementedProviderService: ServiceImpl<typeof ProviderService> = {
  payOut() {
    throw new ConnectError('unimplemented', Code.Unimplemented);
  },
  updatePayment() {
    throw new ConnectError('unimplemented', Code.Unimplemented);
  },
  updateLimit() {
    throw new ConnectError('unimplemented', Code.Unimplemented);
  },
  appendLedgerEntries() {
    throw new ConnectError('unimplemented', Code.Unimplemented);
  },
  approvePaymentQuotes() {
    throw new ConnectError('unimplemented', Code.Unimplemented);
  },
};

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
  register: RegisterRoutes = () => {},
): Promise<{ url: string; close: () => Promise<void> }> {
  const handler = connectNodeAdapter(createService(networkPublicKeyHex, register));
  const server = http.createServer(signatureValidation(handler));
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

describe('health is mounted by the transport', () => {
  // The no-code-change guarantee: a starter using only the public API gets
  // health mounted, behind the same signature verification as its own services.
  it('signed check answers for registered services and refuses the rest', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex, (router) => {
      router.service(ProviderService, unimplementedProviderService);
    });
    try {
      const client = createClient(privateKeyHex, url, Health);

      // The customer's own service, health itself, and the whole-process query.
      for (const service of [ProviderService.typeName, Health.typeName, '']) {
        const resp = await client.check({ service });
        assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING, service);
      }

      await assert.rejects(
        async () => client.check({ service: 'example.v1.NotRegistered' }),
        (err: unknown) => err instanceof ConnectError && err.code === Code.NotFound,
      );
    } finally {
      await close();
    }
  });

  // Response headers are the only place the SDK reports what it is: the health
  // contract has a single status field and names its service in the request, so
  // the message itself has no room for this.
  it('stamps the SDK identity onto the check response', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const client = createClient(privateKeyHex, url, Health);
      const headers = new Headers();
      await client.check({ service: '' }, { onHeader: (h) => h.forEach((v, k) => headers.set(k, v)) });

      assert.equal(headers.get(SDK_ECOSYSTEM_HEADER.toLowerCase()), 'node');
      assert.equal(headers.get(SDK_VERSION_HEADER.toLowerCase()), SDK_VERSION);
    } finally {
      await close();
    }
  });

  // The probe is signed like every other call the Network makes. Without this
  // the transport would be publishing an unauthenticated endpoint on a
  // partner's port.
  it('unsigned check is rejected with InvalidArgument', async () => {
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
