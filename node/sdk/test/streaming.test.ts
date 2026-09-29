import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import {
  Code,
  ConnectError,
  createClient as createConnectClient,
  createConnectRouter,
  type ServiceImpl,
} from '@connectrpc/connect';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { universalRequestFromNodeRequest, universalResponseToNodeResponse } from '@connectrpc/connect-node';
import { createClient } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { streamTransportOptions } from '../src/common/client/client.js';
import { computeDigest, NetworkHeaders, parsePublicKey, publicKeysEqual, verifySignature } from '../src/crypto/index.js';
import { StreamTest, bufferingFetchClient, newKeypair, stringValues } from './stream_helpers.js';

// One request as the test server saw it.
interface Check {
  procedure: string;
  contentType: string;
  timeoutMs: string | null;
  // What the signature was checked against: the first envelope of a streaming request (empty if
  // it has no messages), the whole body of a unary one.
  signed: Buffer;
  valid: boolean;
}

interface StreamServer {
  url: string;
  checks: Check[];
  // Resolves once the ClientStream handler has read n messages, or rejects after ms.
  received(n: number, ms: number): Promise<void>;
  close(): Promise<void>;
}

const ENVELOPED = /^application\/connect\+/;

/**
 * Serves test.v1.StreamTest behind a verifier that works the way the network does: it reads a
 * streaming request's first envelope from the raw body, checks the signature over it and answers
 * 401 if that fails, and only then hands the handler the body, first envelope included. A unary
 * request is checked over its whole body. A stream without a first message is refused.
 */
async function bootStreamServer(clientPublicKeyHex: string): Promise<StreamServer> {
  const trustedKey = parsePublicKey(clientPublicKeyHex);
  const checks: Check[] = [];
  const received: string[] = [];
  const waiters = new Set<() => void>();

  const impl: ServiceImpl<typeof StreamTest> = {
    async clientStream(reqs) {
      const got: string[] = [];
      for await (const req of reqs) {
        got.push(req.value);
        received.push(req.value);
        waiters.forEach((wake) => wake());
      }
      return { value: got.join(',') };
    },
    async *serverStream(req) {
      for (let i = 0; i < 3; i++) {
        yield { value: req.value };
      }
    },
    unary(req) {
      return { value: req.value };
    },
  };
  const router = createConnectRouter();
  router.service(StreamTest, impl);
  const handlers = new Map(router.handlers.map((h) => [h.requestPath, h]));

  const verify = (req: http.IncomingMessage, signed: Uint8Array): boolean => {
    const header = (name: string) => String(req.headers[name.toLowerCase()] ?? '');
    const ts = Number(header(NetworkHeaders.SignatureTimestamp));
    if (!Number.isSafeInteger(ts) || Math.abs(Date.now() - ts) > 60_000) {
      return false;
    }
    let publicKey: Buffer;
    try {
      publicKey = parsePublicKey(header(NetworkHeaders.PublicKey));
    } catch {
      return false;
    }
    const signature = Buffer.from(header(NetworkHeaders.Signature).replace(/^0x/, ''), 'hex');
    return publicKeysEqual(publicKey, trustedKey) && verifySignature(publicKey, computeDigest(signed, ts), signature);
  };

  const serve = async (req: http.IncomingMessage, res: http.ServerResponse) => {
    const path = req.url?.split('?')[0] ?? '';
    const handler = handlers.get(path);
    if (!handler) {
      res.writeHead(404).end();
      return;
    }
    const uReq = universalRequestFromNodeRequest(req, res, undefined, undefined);
    const body = (uReq.body as AsyncIterable<Uint8Array>)[Symbol.asyncIterator]();
    const contentType = String(req.headers['content-type'] ?? '');
    const enveloped = ENVELOPED.test(contentType);

    // Read the raw body up to the end of the first envelope (all of it for unary).
    let read = Buffer.alloc(0);
    let ended = false;
    const firstEnd = () => (read.length >= 5 ? 5 + read.readUInt32BE(1) : Infinity);
    while (!(enveloped && read.length >= firstEnd())) {
      const r = await body.next();
      if (r.done) {
        ended = true;
        break;
      }
      read = Buffer.concat([read, r.value]);
    }
    const hasFirstMessage = read.length >= firstEnd();
    const signed = enveloped && hasFirstMessage ? read.subarray(0, firstEnd()) : read;

    const valid = verify(req, signed);
    checks.push({
      procedure: path,
      contentType,
      timeoutMs: req.headers['connect-timeout-ms'] === undefined ? null : String(req.headers['connect-timeout-ms']),
      signed: Buffer.from(signed),
      valid,
    });
    if (!valid || (enveloped && !hasFirstMessage)) {
      res.writeHead(401, { 'Content-Type': 'text/plain' }).end(valid ? 'no first message' : 'signature does not verify');
      return;
    }

    async function* replay() {
      yield read;
      if (!ended) {
        for (let r = await body.next(); !r.done; r = await body.next()) {
          yield r.value;
        }
      }
    }
    const uRes = await handler({ ...uReq, body: replay() });
    await universalResponseToNodeResponse(uRes, res);
  };

  const server = http.createServer((req, res) => {
    serve(req, res).catch((err) => {
      if (!res.headersSent) {
        res.writeHead(500).end(String(err));
      }
    });
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    url: `http://127.0.0.1:${port}`,
    checks,
    received: (n, ms) => new Promise<void>((resolve, reject) => {
      const wake = () => {
        if (received.length >= n) {
          waiters.delete(wake);
          clearTimeout(timer);
          resolve();
        }
      };
      const timer = setTimeout(() => {
        waiters.delete(wake);
        reject(new Error(`the server did not get message ${n} before the stream ended: the request body is buffered`));
      }, ms);
      waiters.add(wake);
      wake();
    }),
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

// The first envelope of a StringValue message as connect sends it: flags 0, uint32be length,
// then field 1 (tag 0x0a) with the string's length and bytes.
function envelopeOf(value: string): string {
  const payload = Buffer.concat([Buffer.from([0x0a, value.length]), Buffer.from(value)]);
  const prefix = Buffer.alloc(5);
  prefix.writeUInt32BE(payload.length, 1);
  return Buffer.concat([prefix, payload]).toString('hex');
}

async function withServer(fn: (srv: StreamServer, key: ReturnType<typeof newKeypair>) => Promise<void>) {
  const key = newKeypair();
  const srv = await bootStreamServer(key.publicKeyHex);
  try {
    await fn(srv, key);
  } finally {
    await srv.close();
  }
}

function isCode(code: Code) {
  return (err: unknown) => {
    assert.ok(err instanceof ConnectError, `want a ConnectError, got ${err}`);
    assert.equal(err.code, code, `code of ${err.message}`);
    return true;
  };
}

describe('Streaming calls are signed over the first request envelope', { timeout: 20_000 }, () => {
  it('client stream: every message arrives, the signature covers only the first envelope', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const resp = await client.clientStream(stringValues('m1', 'm2', 'm3'));
      assert.equal(resp.value, 'm1,m2,m3');

      assert.equal(srv.checks.length, 1);
      const [check] = srv.checks;
      assert.equal(check.procedure, '/test.v1.StreamTest/ClientStream');
      assert.equal(check.contentType, 'application/connect+proto');
      assert.equal(check.signed.toString('hex'), envelopeOf('m1'));
      assert.equal(check.valid, true);
    });
  });

  it('server stream: every response arrives, the signature covers the request envelope', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const got: string[] = [];
      for await (const resp of client.serverStream({ value: 'hello' })) {
        got.push(resp.value);
      }
      assert.deepEqual(got, ['hello', 'hello', 'hello']);

      assert.equal(srv.checks.length, 1);
      const [check] = srv.checks;
      assert.equal(check.procedure, '/test.v1.StreamTest/ServerStream');
      assert.equal(check.contentType, 'application/connect+proto');
      assert.equal(check.signed.toString('hex'), envelopeOf('hello'));
      assert.equal(check.valid, true);
    });
  });

  // The request must reach the server as soon as the first message is signed. The caller's stream
  // produces m2 only once the server has read m1, and m3 once it has read m2: a transport that
  // buffers the body sends nothing until the stream ends, and the wait fails.
  it('client stream is not buffered: the server reads each message before the next is produced', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      async function* messages() {
        yield { value: 'm1' };
        await srv.received(1, 3_000);
        yield { value: 'm2' };
        await srv.received(2, 3_000);
        yield { value: 'm3' };
      }
      const resp = await client.clientStream(messages());
      assert.equal(resp.value, 'm1,m2,m3');
      assert.equal(srv.checks[0].valid, true);
    });
  });

  it('a first message larger than one transport chunk is signed whole', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const large = '0123456789abcdef'.repeat(4096); // 64 KiB
      const resp = await client.clientStream(stringValues(large, 'tail'));
      assert.equal(resp.value, `${large},tail`);

      const [check] = srv.checks;
      assert.equal(check.valid, true);
      assert.equal(check.signed.readUInt32BE(1), check.signed.length - 5);
      assert.ok(check.signed.length > large.length, 'the signed envelope holds the whole first message');
    });
  });

  // The network rejects a stream without a first message; the SDK still signs it, over nothing.
  it('an empty client stream is signed over empty bytes and sent', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      await assert.rejects(client.clientStream(stringValues()), isCode(Code.Unauthenticated));

      assert.equal(srv.checks.length, 1);
      assert.equal(srv.checks[0].signed.length, 0);
      assert.equal(srv.checks[0].valid, true, 'the signature verifies over empty bytes');
    });
  });

  it('an unsigned stream request is rejected', async () => {
    await withServer(async (srv) => {
      const signer = CreateSigner(newKeypair().privateKeyHex);
      const transport = createTransport({ ...streamTransportOptions(signer, srv.url), httpClient: bufferingFetchClient() });
      const client = createConnectClient(StreamTest, transport);
      await assert.rejects(client.clientStream(stringValues('m1', 'm2')), isCode(Code.Unauthenticated));

      assert.equal(srv.checks.length, 1);
      assert.equal(srv.checks[0].valid, false);
    });
  });

  it('a stream signed over its whole body is rejected', async () => {
    await withServer(async (srv, key) => {
      const signer = CreateSigner(key.privateKeyHex);
      const transport = createTransport({ ...streamTransportOptions(signer, srv.url), httpClient: bufferingFetchClient(signer) });
      const client = createConnectClient(StreamTest, transport);
      await assert.rejects(client.clientStream(stringValues('m1', 'm2')), isCode(Code.Unauthenticated));
      assert.equal(srv.checks[0].valid, false);
    });
  });
});

describe('createClient routes unary and streaming calls to their own transport', { timeout: 20_000 }, () => {
  it('unary calls keep Connect JSON, signed over the whole body', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const resp = await client.unary({ value: 'hello' });
      assert.equal(resp.value, 'hello');
      await client.clientStream(stringValues('m1'));

      assert.equal(srv.checks.length, 2);
      const [unary, stream] = srv.checks;
      assert.equal(unary.procedure, '/test.v1.StreamTest/Unary');
      assert.equal(unary.contentType, 'application/json');
      assert.equal(unary.signed.toString(), '"hello"');
      assert.equal(unary.valid, true);
      assert.equal(stream.contentType, 'application/connect+proto');
      assert.equal(stream.valid, true);
    });
  });

  it('timeouts are applied per transport', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest, { unaryTimeoutMs: 4_321, streamTimeoutMs: 8_765 });
      await client.unary({ value: 'u' });
      await client.clientStream(stringValues('c'));
      for await (const _ of client.serverStream({ value: 's' })) { /* drain */ }

      assert.deepEqual(srv.checks.map((c) => [c.procedure, c.timeoutMs]), [
        ['/test.v1.StreamTest/Unary', '4321'],
        ['/test.v1.StreamTest/ClientStream', '8765'],
        ['/test.v1.StreamTest/ServerStream', '8765'],
      ]);
    });
  });

  it('no timeouts by default', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      await client.unary({ value: 'u' });
      await client.clientStream(stringValues('c'));

      assert.deepEqual(srv.checks.map((c) => c.timeoutMs), [null, null]);
    });
  });

  // The request goes out only once the first message is there. The stream timeout still ends the
  // wait when the caller never produces one.
  it('the stream timeout covers the wait for the first message', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest, { streamTimeoutMs: 200 });
      async function* stalled() {
        await new Promise(() => {});
        yield { value: 'never' };
      }
      // Bounded, so that a transport that ignores the deadline fails here instead of hanging.
      const giveUp = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new Error('the stream timeout did not end the wait for the first message')), 5_000).unref();
      });
      await assert.rejects(Promise.race([client.clientStream(stalled()), giveUp]), isCode(Code.DeadlineExceeded));
      assert.equal(srv.checks.length, 0, 'nothing is sent without a first message');
    });
  });
});

// streamTransportOptions fills connect-es's CommonTransportOptions, which is internal API that does
// not follow semantic versioning. The build type-checks the object against it; this checks that a
// transport made from it still works end to end.
describe('streamTransportOptions guard', { timeout: 20_000 }, () => {
  it('builds a working Connect transport', async () => {
    await withServer(async (srv, key) => {
      const transport = createTransport(streamTransportOptions(CreateSigner(key.privateKeyHex), srv.url));
      const client = createConnectClient(StreamTest, transport);

      const got: string[] = [];
      for await (const resp of client.serverStream({ value: 'guard' })) {
        got.push(resp.value);
      }
      assert.deepEqual(got, ['guard', 'guard', 'guard']);
      assert.equal((await client.clientStream(stringValues('a', 'b'))).value, 'a,b');
      assert.ok(srv.checks.every((c) => c.valid));
    });
  });
});
