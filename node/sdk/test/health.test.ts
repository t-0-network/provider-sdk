import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { connectNodeAdapter, createConnectTransport } from '@connectrpc/connect-node';
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
import { newKeypair } from './stream_helpers.js';

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

async function bootServer(
  networkPublicKeyHex: string,
  register: RegisterRoutes = () => {},
  options?: Parameters<typeof createService>[2],
): Promise<{ url: string; close: () => Promise<void> }> {
  const handler = connectNodeAdapter(createService(networkPublicKeyHex, register, options));
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

  // Every Check reply carries them, NotFound included. A blank version reports the SDK's own, as in
  // every SDK.
  for (const [version, reported] of [['9.9.9', '9.9.9'], ['', SDK_VERSION], ['  ', SDK_VERSION]]) {
    it(`reports version ${JSON.stringify(reported)} for the option ${JSON.stringify(version)}, NotFound included`, async () => {
      const { privateKeyHex, publicKeyHex } = newKeypair();
      const { url, close } = await bootServer(publicKeyHex, () => {}, { version });
      try {
        const client = createClient(privateKeyHex, url, Health);
        for (const service of ['', 'no.such.Service']) {
          const headers = new Headers();
          const onHeader = (h: Headers) => h.forEach((v, k) => headers.set(k, v));
          await client.check({ service }, { onHeader }).catch((err: unknown) => {
            assert.ok(err instanceof ConnectError && err.code === Code.NotFound, String(err));
            assert.equal(err.rawMessage, "unknown service 'no.such.Service'");
            err.metadata.forEach((v, k) => headers.set(k, v));
          });
          assert.equal(headers.get(SDK_ECOSYSTEM_HEADER.toLowerCase()), 'node', service);
          assert.equal(headers.get(SDK_VERSION_HEADER.toLowerCase()), reported, service);
        }
      } finally {
        await close();
      }
    });
  }

  // The probe is signed like every other call the Network makes. Without this
  // the transport would be publishing an unauthenticated endpoint on a
  // partner's port.
  it('unsigned check is rejected with InvalidArgument', async () => {
    const { publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const transport = createConnectTransport({ baseUrl: url, httpVersion: '1.1' });
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
