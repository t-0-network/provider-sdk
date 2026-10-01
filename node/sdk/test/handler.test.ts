import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { createClient, WireFormat } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { createSigningHttpClient } from '../src/common/client/signing-http-client.js';
import { createHandler, createRequestVerifier, NetworkHeaders } from '../src/index.js';
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
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createConnectTransport, createNodeHttpClient } from '@connectrpc/connect-node';
import { secp256k1 } from '@noble/curves/secp256k1.js';

function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return {
    privateKeyHex: '0x' + Buffer.from(priv).toString('hex'),
    publicKeyHex: '0x' + Buffer.from(pub).toString('hex'),
    compressedPublicKeyHex: '0x' + Buffer.from(secp256k1.getPublicKey(priv, true)).toString('hex'),
  };
}

// The hybrid encoding of an uncompressed key: prefix 06 for an even y, 07 for an odd one.
function hybrid(publicKeyHex: string): string {
  const key = Buffer.from(publicKeyHex.slice(2), 'hex');
  key[0] = 0x06 | (key[64] & 1);
  return '0x' + key.toString('hex');
}

// A health check signed with privateKeyHex whose X-Public-Key header is publicKeyHeader, as given.
async function checkWithPublicKeyHeader(url: string, privateKeyHex: string, publicKeyHeader: string) {
  const signer = CreateSigner(privateKeyHex);
  const send = createNodeHttpClient({ httpVersion: '1.1' });
  const transport = createTransport({
    ...transportOptions(signer, url, 15_000, WireFormat.Binary),
    httpClient: createSigningHttpClient(signer, (req) => {
      req.header.set(NetworkHeaders.PublicKey, publicKeyHeader);
      return send(req);
    }),
  });
  return createConnectClient(Health, transport).check({ service: 'grpc.health.v1.Health' });
}

async function assertRejected(call: Promise<unknown>, code: Code) {
  await assert.rejects(call, (err: unknown) => {
    assert.ok(err instanceof ConnectError);
    assert.equal(err.code, code);
    return true;
  });
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

  describe('network public key is checked at startup', () => {
    for (const [name, key] of [['empty', ''], ['whitespace only', '  \n']]) {
      it(`rejects ${name} as not set`, () => {
        assert.throws(() => createHandler(key, () => {}), { message: 'network public key is not set' });
      });
      it(`createRequestVerifier rejects ${name} as not set`, () => {
        assert.throws(() => createRequestVerifier({ networkPublicKey: key }), { message: 'network public key is not set' });
      });
    }

    const malformed: [string, string | Buffer][] = [
      ['non-hex', '0xnot-a-key'],
      ['truncated', newKeypair().publicKeyHex.slice(0, -2)],
      ['0x02 prefix on 65 bytes', '0x02' + newKeypair().publicKeyHex.slice(4)],
      ['hybrid', hybrid(newKeypair().publicKeyHex)],
      ['off-curve', '0x04' + '00'.repeat(64)],
      ['33-byte Buffer', Buffer.alloc(33, 1)],
    ];
    for (const [name, key] of malformed) {
      it(`rejects ${name}`, () => {
        assert.throws(() => createHandler(key, () => {}), /invalid network public key/);
      });
      it(`createRequestVerifier rejects ${name}`, () => {
        assert.throws(() => createRequestVerifier({ networkPublicKey: key }), /invalid network public key/);
      });
    }

    it('accepts a key with surrounding whitespace', () => {
      const { publicKeyHex } = newKeypair();
      assert.doesNotThrow(() => createHandler(`  ${publicKeyHex}\n`, () => {}));
    });

    it('accepts a Buffer', () => {
      const { publicKeyHex } = newKeypair();
      assert.doesNotThrow(() => createHandler(Buffer.from(publicKeyHex.slice(2), 'hex'), () => {}));
    });

    it('accepts a compressed key', () => {
      const { compressedPublicKeyHex } = newKeypair();
      assert.doesNotThrow(() => createHandler(compressedPublicKeyHex, () => {}));
      assert.doesNotThrow(() => createRequestVerifier({ networkPublicKey: compressedPublicKeyHex }));
    });

    it('a server with a compressed key answers a call signed with the uncompressed one', async () => {
      const { privateKeyHex, compressedPublicKeyHex } = newKeypair();
      const { url, close } = await bootServer(compressedPublicKeyHex);
      try {
        const client = createClient(privateKeyHex, url, Health);
        const resp = await client.check({ service: '' });
        assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
      } finally {
        await close();
      }
    });

    it('a server with a whitespace-padded key answers a signed call', async () => {
      const { privateKeyHex, publicKeyHex } = newKeypair();
      const { url, close } = await bootServer(`  ${publicKeyHex}\n`);
      try {
        const client = createClient(privateKeyHex, url, Health);
        const resp = await client.check({ service: '' });
        assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
      } finally {
        await close();
      }
    });
  });

  describe('X-Public-Key header', () => {
    const network = newKeypair();
    const accepted: [string, string][] = [
      ['the compressed network key', network.compressedPublicKeyHex],
      ['a 0X prefix', '0X' + network.publicKeyHex.slice(2)],
    ];
    for (const [name, header] of accepted) {
      it(`accepts ${name} on a signed call`, async () => {
        const { url, close } = await bootServer(network.publicKeyHex);
        try {
          const resp = await checkWithPublicKeyHeader(url, network.privateKeyHex, header);
          assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
        } finally {
          await close();
        }
      });
    }

    const rejected: [string, string, Code][] = [
      ['trailing junk', network.publicKeyHex + 'zz', Code.InvalidArgument],
      ['odd length', network.publicKeyHex.slice(0, -1), Code.InvalidArgument],
      ['wrong length', network.publicKeyHex.slice(0, -2), Code.Unauthenticated],
      ['hybrid', hybrid(network.publicKeyHex), Code.Unauthenticated],
      ['off-curve', '0x04' + '00'.repeat(64), Code.Unauthenticated],
      ['another key', newKeypair().compressedPublicKeyHex, Code.Unauthenticated],
    ];
    for (const [name, header, code] of rejected) {
      it(`rejects ${name} with ${Code[code]}`, async () => {
        const { url, close } = await bootServer(network.publicKeyHex);
        try {
          await assertRejected(checkWithPublicKeyHeader(url, network.privateKeyHex, header), code);
        } finally {
          await close();
        }
      });
    }
  });

  it('unsigned request is rejected', async () => {
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
