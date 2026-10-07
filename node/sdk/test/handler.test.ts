import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import http2 from 'node:http2';
import { once } from 'node:events';
import { gzipSync } from 'node:zlib';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { create, toBinary } from '@bufbuild/protobuf';
import { createClient, WireFormat } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { createSigningHttpClient } from '../src/common/client/signing-http-client.js';
import { BodyHashes, createService, type CreateServiceOptions } from '../src/common/service.js';
import { computeDigest, createHandler, createRequestVerifier, NetworkHeaders } from '../src/index.js';
import { SDK_VERSION } from '../src/version.js';
import { SDK_VERSION_HEADER } from '../src/service/health.js';
import {
  Health,
  HealthCheckRequestSchema,
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

// A health check signed with privateKeyHex whose header `name` is edit(the value the client set).
async function checkWithHeader(url: string, privateKeyHex: string, name: NetworkHeaders, edit: (value: string) => string) {
  const signer = CreateSigner(privateKeyHex);
  const send = createNodeHttpClient({ httpVersion: '1.1' });
  const transport = createTransport({
    ...transportOptions(signer, url, 15_000, WireFormat.Binary),
    httpClient: createSigningHttpClient(signer, (req) => {
      req.header.set(name, edit(req.header.get(name) ?? ''));
      return send(req);
    }),
  });
  return createConnectClient(Health, transport).check({ service: 'grpc.health.v1.Health' });
}

// The signature headers for `signed`, made with privateKeyHex now.
function signatureHeaders(privateKeyHex: string, signed: Uint8Array): Record<string, string> {
  const priv = Buffer.from(privateKeyHex.slice(2), 'hex');
  const ts = Date.now();
  const signature = secp256k1.sign(computeDigest(signed, ts), priv, { prehash: false });
  return {
    [NetworkHeaders.PublicKey.toLowerCase()]: '0x' + Buffer.from(secp256k1.getPublicKey(priv, false)).toString('hex'),
    [NetworkHeaders.Signature.toLowerCase()]: '0x' + Buffer.from(signature).toString('hex'),
    [NetworkHeaders.SignatureTimestamp.toLowerCase()]: String(ts),
  };
}

// A gRPC message frame: flag, uint32be(length), payload.
function frame(flag: number, payload: Uint8Array): Buffer {
  const out = Buffer.alloc(5 + payload.length);
  out[0] = flag;
  out.writeUInt32BE(payload.length, 1);
  out.set(payload, 5);
  return out;
}

const healthCheckPayload = toBinary(HealthCheckRequestSchema, create(HealthCheckRequestSchema, { service: 'grpc.health.v1.Health' }));

async function assertRejected(call: Promise<unknown>, code: Code) {
  await assert.rejects(call, (err: unknown) => {
    assert.ok(err instanceof ConnectError);
    assert.equal(err.code, code);
    return true;
  });
}

async function bootServer(
  networkPublicKeyHex: string,
  options?: CreateServiceOptions,
): Promise<{ url: string; close: () => Promise<void> }> {
  const handler = createHandler(networkPublicKeyHex, () => {}, options);
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
          const resp = await checkWithHeader(url, network.privateKeyHex, NetworkHeaders.PublicKey, () => header);
          assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
        } finally {
          await close();
        }
      });
    }

    // Whatever is not the network key, hex or not, is Unauthenticated.
    const rejected: [string, string][] = [
      ['trailing junk', network.publicKeyHex + 'zz'],
      ['odd length', network.publicKeyHex.slice(0, -1)],
      ['wrong length', network.publicKeyHex.slice(0, -2)],
      ['off-curve', '0x04' + '00'.repeat(64)],
      ['another key', newKeypair().compressedPublicKeyHex],
    ];
    for (const [name, header] of rejected) {
      it(`rejects ${name} with Unauthenticated`, async () => {
        const { url, close } = await bootServer(network.publicKeyHex);
        try {
          await assertRejected(checkWithHeader(url, network.privateKeyHex, NetworkHeaders.PublicKey, () => header), Code.Unauthenticated);
        } finally {
          await close();
        }
      });
    }
  });

  describe('X-Signature-Timestamp header', () => {
    const network = newKeypair();

    it('accepts leading zeros on a signed call', async () => {
      const { url, close } = await bootServer(network.publicKeyHex);
      try {
        const resp = await checkWithHeader(url, network.privateKeyHex, NetworkHeaders.SignatureTimestamp, (ts) => '000' + ts);
        assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
      } finally {
        await close();
      }
    });

    // Each one parseInt read as the signed timestamp.
    const rejected: [string, (ts: string) => string][] = [
      ['trailing junk', (ts) => ts + 'abc'],
      ['a decimal point', (ts) => ts + '.0'],
      ['a plus sign', (ts) => '+' + ts],
      ['hex', (ts) => '0x' + Number(ts).toString(16)],
    ];
    for (const [name, edit] of rejected) {
      it(`rejects ${name} with InvalidArgument`, async () => {
        const { url, close } = await bootServer(network.publicKeyHex);
        try {
          await assert.rejects(checkWithHeader(url, network.privateKeyHex, NetworkHeaders.SignatureTimestamp, edit), (err: unknown) => {
            assert.ok(err instanceof ConnectError);
            assert.equal(err.code, Code.InvalidArgument);
            assert.match(err.rawMessage, /must be a number/);
            return true;
          });
        } finally {
          await close();
        }
      });
    }
  });

  it('refuses a body over maxBodySize with ResourceExhausted', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex, { maxBodySize: 16 });
    try {
      const client = createClient(privateKeyHex, url, Health);
      await assertRejected(client.check({ service: 'grpc.health.v1.Health' }), Code.ResourceExhausted);
    } finally {
      await close();
    }
  });

  describe('gRPC framing fallback (HTTP/2)', () => {
    const network = newKeypair();

    async function bootGrpcServer(): Promise<{ url: string; close: () => Promise<void> }> {
      const handler = createHandler(network.publicKeyHex, () => {});
      const server = http2.createServer(handler as unknown as (req: http2.Http2ServerRequest, res: http2.Http2ServerResponse) => void);
      await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
      const { port } = server.address() as AddressInfo;
      return {
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise<void>((resolve) => server.close(() => resolve())),
      };
    }

    // A gRPC health check with this body, signed over `signed`. Returns the grpc-status.
    async function grpcCheck(url: string, body: Uint8Array, signed: Uint8Array, headers: http2.OutgoingHttpHeaders = {}): Promise<string | undefined> {
      const session = http2.connect(url);
      try {
        const stream = session.request({
          ':method': 'POST',
          ':path': '/grpc.health.v1.Health/Check',
          'content-type': 'application/grpc',
          te: 'trailers',
          ...signatureHeaders(network.privateKeyHex, signed),
          ...headers,
        });
        let status: string | undefined;
        stream.on('response', (h) => { status = h['grpc-status'] as string | undefined; });
        stream.on('trailers', (t) => { status = (t['grpc-status'] as string | undefined) ?? status; });
        stream.resume();
        stream.end(body);
        await once(stream, 'close');
        return status;
      } finally {
        session.close();
      }
    }

    const body = frame(0, healthCheckPayload);
    const cases: [string, Uint8Array, Uint8Array, http2.OutgoingHttpHeaders, string][] = [
      ['passes signed over the framed body', body, body, {}, '0'],
      ['passes signed over the payload alone', body, body.subarray(5), {}, '0'],
      ['passes a compressed frame (flag 1) signed over the framed body', frame(1, gzipSync(healthCheckPayload)), frame(1, gzipSync(healthCheckPayload)), { 'grpc-encoding': 'gzip' }, '0'],
      ['refuses a compressed frame (flag 1) signed over its payload', frame(1, gzipSync(healthCheckPayload)), gzipSync(healthCheckPayload), { 'grpc-encoding': 'gzip' }, String(Code.Unauthenticated)],
    ];
    for (const [name, sent, signed, headers, status] of cases) {
      it(name, async () => {
        const { url, close } = await bootGrpcServer();
        try {
          assert.equal(await grpcCheck(url, sent, signed, headers), status);
        } finally {
          await close();
        }
      });
    }
  });

  // On the wire, connect refuses these bodies before the signature is checked (a body that starts
  // with a 0 byte is not a protobuf message; a unary gRPC call takes one message), so the cases
  // call the signature interceptor directly, with the body hashed one byte at a time.
  describe('gRPC framing fallback: when it applies', () => {
    const network = newKeypair();

    function intercept(contentType: string, body: Uint8Array, signed: Uint8Array) {
      const service = createService(network.publicKeyHex, () => {});
      const bodyHashes = new BodyHashes();
      for (let i = 0; i < body.length; i++) {
        bodyHashes.update(Buffer.from(body.subarray(i, i + 1)));
      }
      const req = {
        header: new Headers({ ...signatureHeaders(network.privateKeyHex, signed), 'content-type': contentType }),
        contextValues: service.contextValues({ bodyHashes }),
      };
      return service.interceptors[0](async () => ({}) as never)(req as never);
    }

    const body = frame(0, healthCheckPayload);

    it('a gRPC body of one frame signed over its payload passes', async () => {
      await intercept('application/grpc+proto', body, body.subarray(5));
    });

    it('a non-gRPC body that looks like a frame signed over all but its first 5 bytes is refused', async () => {
      await assertRejected(intercept('application/proto', body, body.subarray(5)), Code.Unauthenticated);
    });

    it('a gRPC body of two frames signed over all but its first 5 bytes is refused', async () => {
      const twoFrames = Buffer.concat([body, body]);
      await assertRejected(intercept('application/grpc', twoFrames, twoFrames.subarray(5)), Code.Unauthenticated);
    });
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

  it('rejects Health/Watch with unimplemented and still serves unary calls', async () => {
    const { privateKeyHex, publicKeyHex } = newKeypair();
    const { url, close } = await bootServer(publicKeyHex);
    try {
      const client = createClient(privateKeyHex, url, Health);
      await assertRejected(client.watch({ service: '' })[Symbol.asyncIterator]().next(), Code.Unimplemented);
      const resp = await client.check({ service: '' });
      assert.equal(resp.status, HealthCheckResponse_ServingStatus.SERVING);
    } finally {
      await close();
    }
  });
});
