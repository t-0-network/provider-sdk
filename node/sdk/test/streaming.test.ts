import { describe, it, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import type { AddressInfo } from 'node:net';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Code, createClient as createConnectClient, createConnectRouter, type Client, type ServiceImpl } from '@connectrpc/connect';
import type { UniversalClientFn } from '@connectrpc/connect/protocol';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { universalRequestFromNodeRequest, universalResponseToNodeResponse } from '@connectrpc/connect-node';
import { createClient, WireFormat, type Signature } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { createSigningHttpClient } from '../src/common/client/signing-http-client.js';
import { computeDigest, NetworkHeaders, parsePublicKey, publicKeysEqual, verifySignature } from '../src/crypto/index.js';
import { StreamTest, isCode, newKeypair, stringValues } from './stream_helpers.js';

const vectors = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../../cross_test/test_vectors.json'), 'utf-8'));

interface Check {
  procedure: string;
  contentType: string;
  timeoutMs: string | null;
  // The first envelope of a stream (empty without one), the whole body of a unary call.
  signed: Buffer;
  // The X-Signature header as sent.
  signature: string;
  valid: boolean;
}

interface StreamServer {
  url: string;
  checks: Check[];
  received(n: number, ms: number): Promise<void>;
  close(): Promise<void>;
}

const ENVELOPED = /^application\/connect\+/;

// Like the network, verifies a stream over its first envelope before the handler reads the body.
// Serves the procedures under pathPrefix, such as "/prefix"; a check's procedure includes it.
async function bootStreamServer(clientPublicKeyHex: string, pathPrefix = ''): Promise<StreamServer> {
  const trustedKey = parsePublicKey(clientPublicKeyHex);
  const checks: Check[] = [];
  const received: string[] = [];
  const waiters = new Set<() => void>();

  // No bidi handler, but a bidi request that got through would still be recorded in checks.
  const impl: Omit<ServiceImpl<typeof StreamTest>, 'bidi'> = {
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
    const handler = path.startsWith(`${pathPrefix}/`) ? handlers.get(path.slice(pathPrefix.length)) : undefined;
    if (!handler) {
      res.writeHead(404).end();
      return;
    }
    const uReq = universalRequestFromNodeRequest(req, res, undefined, undefined);
    const body = (uReq.body as AsyncIterable<Uint8Array>)[Symbol.asyncIterator]();
    const contentType = String(req.headers['content-type'] ?? '');
    const enveloped = ENVELOPED.test(contentType);

    // Up to the end of the first envelope; all of a unary body.
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
      signature: String(req.headers['x-signature'] ?? ''),
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

// flags 0, uint32be length, StringValue field 1 (tag 0x0a); one length byte, so value < 128 bytes.
function envelopeOf(value: string): string {
  const payload = Buffer.concat([Buffer.from([0x0a, value.length]), Buffer.from(value)]);
  const prefix = Buffer.alloc(5);
  prefix.writeUInt32BE(payload.length, 1);
  return Buffer.concat([prefix, payload]).toString('hex');
}

// StringValue's JSON is a bare string.
function jsonEnvelopeOf(value: string): string {
  const payload = Buffer.from(JSON.stringify(value));
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

// Answers a server stream with one "ok" and never ends it.
async function withNeverEndingServer(fn: (url: string, server: { requestArrived: Promise<void>; closed: Promise<void> }) => Promise<void>) {
  let arrived = () => {};
  let closed = () => {};
  const events = {
    requestArrived: new Promise<void>((resolve) => { arrived = resolve; }),
    closed: new Promise<void>((resolve) => { closed = resolve; }),
  };
  const server = http.createServer(async (req, res) => {
    for await (const _ of req) { /* the request */ }
    res.on('close', () => closed());
    res.writeHead(200, { 'Content-Type': 'application/connect+proto' });
    res.write(Buffer.from(envelopeOf('ok'), 'hex'));
    arrived();
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await fn(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, events);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

// Fails after 5 s, so that a call left open fails the test instead of hanging it.
function giveUp(what: string): Promise<never> {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error(what)), 5_000).unref();
  });
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

  // m2 is produced only once the server has read m1: a transport that buffers the body stalls here.
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

  it('an empty client stream is signed over empty bytes and sent', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      await assert.rejects(client.clientStream(stringValues()), isCode(Code.Unauthenticated));

      assert.equal(srv.checks.length, 1);
      assert.equal(srv.checks[0].signed.length, 0);
      assert.equal(srv.checks[0].valid, true, 'the signature verifies over empty bytes');
    });
  });
});

describe('createClient routes unary and streaming calls to their own transport', { timeout: 20_000 }, () => {
  it('unary calls use Connect in binary, signed over the whole body', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const resp = await client.unary({ value: 'hello' });
      assert.equal(resp.value, 'hello');
      await client.clientStream(stringValues('m1'));

      assert.equal(srv.checks.length, 2);
      const [unary, stream] = srv.checks;
      assert.equal(unary.procedure, '/test.v1.StreamTest/Unary');
      assert.equal(unary.contentType, 'application/proto');
      assert.equal(unary.signed.toString('hex'), '0a0568656c6c6f');
      assert.equal(unary.valid, true);
      assert.equal(stream.contentType, 'application/connect+proto');
      assert.equal(stream.valid, true);
    });
  });

  it('with WireFormat.Json, calls use Connect JSON, signed the same way', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest, { wireFormat: WireFormat.Json });
      assert.equal((await client.unary({ value: 'hello' })).value, 'hello');
      assert.equal((await client.clientStream(stringValues('m1', 'm2', 'm3'))).value, 'm1,m2,m3');
      const got: string[] = [];
      for await (const resp of client.serverStream({ value: 'hi' })) {
        got.push(resp.value);
      }
      assert.deepEqual(got, ['hi', 'hi', 'hi']);

      const [unary, clientStream, serverStream] = srv.checks;
      assert.equal(unary.contentType, 'application/json');
      assert.equal(unary.signed.toString(), '"hello"');
      assert.equal(clientStream.contentType, 'application/connect+json');
      assert.equal(clientStream.signed.toString('hex'), jsonEnvelopeOf('m1'));
      assert.equal(serverStream.contentType, 'application/connect+json');
      assert.equal(serverStream.signed.toString('hex'), jsonEnvelopeOf('hi'));
      assert.ok(srv.checks.every((c) => c.valid));
    });
  });

  it('each call sends its deadline: the defaults, the options, or its own timeoutMs, shorter or longer', async () => {
    await withServer(async (srv, key) => {
      const byDefault = createClient(key.privateKeyHex, srv.url, StreamTest);
      await byDefault.unary({ value: 'u' });
      await byDefault.clientStream(stringValues('c'));
      const configured = createClient(key.privateKeyHex, srv.url, StreamTest, { timeoutMs: 4_321, streamTimeoutMs: 8_765 });
      await configured.unary({ value: 'u' });
      await configured.clientStream(stringValues('c'));
      for await (const _ of configured.serverStream({ value: 's' })) { /* drain */ }
      await byDefault.unary({ value: 'u' }, { timeoutMs: 20_000 });
      await byDefault.clientStream(stringValues('c'), { timeoutMs: 1_000 });

      assert.deepEqual(srv.checks.map((c) => [c.procedure.split('/').pop(), c.timeoutMs]), [
        ['Unary', '15000'], ['ClientStream', '300000'],
        ['Unary', '4321'], ['ClientStream', '8765'], ['ServerStream', '8765'],
        ['Unary', '20000'], ['ClientStream', '1000'],
      ]);
    });
  });

  it('a call timeout of 0 or less fails at once with deadline_exceeded, unsent; a larger one than 2^31 − 1 is cut to it', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      for (const timeoutMs of [0, -1, NaN]) {
        await assert.rejects(client.unary({ value: 'u' }, { timeoutMs }), isCode(Code.DeadlineExceeded));
        await assert.rejects(client.clientStream(stringValues('c'), { timeoutMs }), isCode(Code.DeadlineExceeded));
        await assert.rejects(
          (async () => { for await (const _ of client.serverStream({ value: 's' }, { timeoutMs })) { /* drain */ } })(),
          isCode(Code.DeadlineExceeded),
        );
      }
      assert.equal(srv.checks.length, 0, 'nothing is sent');

      await client.unary({ value: 'u' }, { timeoutMs: 2 ** 31 });
      assert.deepEqual(srv.checks.map((c) => c.timeoutMs), ['2147483647']);
    });
  });

  // From 2^31 ms Node fires a timer at once, so every call would fail at once.
  it('a configured timeout that is 0 or too large for a Node timer is refused', () => {
    for (const ms of [0, -1, NaN, 2 ** 31]) {
      for (const [message, opts] of [
        ['timeout must be a positive duration of at most 2147483647 ms', { timeoutMs: ms }],
        ['stream timeout must be a positive duration of at most 2147483647 ms', { streamTimeoutMs: ms }],
      ] as const) {
        assert.throws(() => createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest, opts), {
          name: 'RangeError',
          message,
        });
      }
    }
    assert.doesNotThrow(() => createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest, { timeoutMs: 2 ** 31 - 1, streamTimeoutMs: 2 ** 31 - 1 }));
  });

  for (const row of vectors.base_url_parsing as { name: string; input: string; valid: boolean; error: string }[]) {
    it(`base URL vector ${row.name}: ${JSON.stringify(row.input)} is ${row.valid ? 'valid' : row.error}`, () => {
      const create = () => createClient(newKeypair().privateKeyHex, row.input, StreamTest);
      if (row.valid) {
        assert.doesNotThrow(create);
      } else {
        assert.throws(create, { message: row.error });
      }
    });
  }

  it('a base URL that is undefined or null is not set', () => {
    const key = newKeypair().privateKeyHex;
    assert.throws(() => createClient(key, undefined, StreamTest), { message: 'base URL is not set' });
    assert.throws(() => createClient(key, null, StreamTest), { message: 'base URL is not set' });
  });

  it('checks the base URL, then the timeouts, then the key, as every SDK does', () => {
    const url = 'http://127.0.0.1:9';
    assert.throws(() => createClient('bad key', ' ', StreamTest, { timeoutMs: 0 }), { message: 'base URL is not valid' });
    assert.throws(() => createClient('bad key', undefined, StreamTest, { timeoutMs: 0 }), { message: 'base URL is not set' });
    assert.throws(() => createClient('bad key', url, StreamTest, { timeoutMs: 0 }), {
      message: 'timeout must be a positive duration of at most 2147483647 ms',
    });
    assert.throws(() => createClient('bad key', url, StreamTest, { streamTimeoutMs: 0 }), {
      message: 'stream timeout must be a positive duration of at most 2147483647 ms',
    });
    assert.throws(() => createClient('bad key', url, StreamTest), { message: 'private key must be 32 bytes (64 hex characters)' });
  });

  it('a path in the base URL prefixes every call, with or without a trailing "/"; the signature is unchanged', async () => {
    for (const path of ['/prefix', '/prefix/', '/sda/payments/t0']) {
      const prefix = path.endsWith('/') ? path.slice(0, -1) : path;
      const key = newKeypair();
      const srv = await bootStreamServer(key.publicKeyHex, prefix);
      try {
        const client = createClient(key.privateKeyHex, srv.url + path, StreamTest);
        assert.equal((await client.unary({ value: 'u' })).value, 'u');
        assert.equal((await client.clientStream(stringValues('c'))).value, 'c');
        assert.deepEqual(srv.checks.map((c) => [c.procedure, c.valid]), [
          [`${prefix}/test.v1.StreamTest/Unary`, true],
          [`${prefix}/test.v1.StreamTest/ClientStream`, true],
        ], path);
      } finally {
        await srv.close();
      }
    }
  });

  it('a base URL without a scheme is read as https', async () => {
    let firstByte: number | undefined;
    const server = net.createServer((socket) => {
      socket.once('data', (data: Buffer) => {
        firstByte = data[0];
        socket.destroy();
      });
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
      const client = createClient(newKeypair().privateKeyHex, `127.0.0.1:${(server.address() as AddressInfo).port}`, StreamTest);
      await assert.rejects(client.unary({ value: 'u' }));
      assert.equal(firstByte, 0x16, 'the client opened a TLS handshake, not a plain HTTP request');
    } finally {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });

  it('an empty private key or a missing signer is refused', () => {
    assert.throws(() => createClient('', 'http://127.0.0.1:9', StreamTest), { message: 'private key must not be null or empty' });
    for (const signer of [null, undefined] as unknown as string[]) {
      assert.throws(() => createClient(signer, 'http://127.0.0.1:9', StreamTest), { name: 'TypeError', message: 'signer must not be null' });
    }
  });

  it('a private key given as bytes of any length but 32, none included, is "private key must be 32 bytes", as in Java and C#', () => {
    for (const key of [Buffer.alloc(0), Buffer.alloc(31, 1), Buffer.alloc(33, 1)]) {
      assert.throws(() => createClient(key, 'http://127.0.0.1:9', StreamTest), { message: vectors.messages.private_key_bytes_length }, `${key.length} bytes`);
    }
  });

  it('a private key given as 32 bytes signs a call that the server verifies', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(Buffer.from(key.privateKeyHex.replace(/^0x/, ''), 'hex'), srv.url, StreamTest);
      assert.equal((await client.unary({ value: 'u' })).value, 'u');
      assert.deepEqual(srv.checks.map((c) => c.valid), [true]);
    });
  });

  it("a signer's failure or malformed output fails the call with internal, and nothing is sent", async () => {
    await withServer(async (srv, key) => {
      const sign = CreateSigner(key.privateKeyHex);
      const output = (change: (sig: Signature) => Partial<Signature>) => async (digest: Buffer) => {
        const sig = await sign(digest);
        return { ...sig, ...change(sig) } as Signature;
      };
      const SIGNATURE = 'signature must be 64 or 65 bytes';
      const PUBLIC_KEY = 'public key must be 65 bytes, uncompressed';
      const refused: [string, (digest: Buffer) => Promise<Signature>][] = [
        ['boom', async () => { throw new Error('boom'); }],
        [SIGNATURE, output((sig) => ({ signature: sig.signature.subarray(0, 63) }))],
        [SIGNATURE, output((sig) => ({ signature: Buffer.concat([sig.signature, Buffer.of(0)]) }))],
        [SIGNATURE, output(() => ({ signature: undefined }))],
        [PUBLIC_KEY, output((sig) => ({ publicKey: Buffer.concat([Buffer.of(2), sig.publicKey.subarray(1, 33)]) }))],
        [PUBLIC_KEY, output((sig) => ({ publicKey: Buffer.concat([Buffer.of(6), sig.publicKey.subarray(1)]) }))],
      ];
      for (const [cause, signer] of refused) {
        const client = createClient(signer, srv.url, StreamTest);
        for (const call of [() => client.unary({ value: 'u' }), () => client.clientStream(stringValues('c'))]) {
          await assert.rejects(call(), (err: unknown) => {
            assert.ok(isCode(Code.Internal)(err), String(err));
            assert.equal((err as { rawMessage: string }).rawMessage, `signing the request failed: ${cause}`);
            return true;
          });
        }
      }
      assert.equal(srv.checks.length, 0, 'nothing is sent');

      // A Uint8Array is as good as a Buffer, a 64-byte signature is accepted, and a 65-byte one is
      // sent as it is, whatever its last byte.
      const plain = output((sig) => ({ signature: new Uint8Array(sig.signature.subarray(0, 64)) as Buffer, publicKey: new Uint8Array(sig.publicKey) as Buffer }));
      assert.equal((await createClient(plain, srv.url, StreamTest).unary({ value: 'u' })).value, 'u');
      const v27 = output((sig) => ({ signature: Buffer.concat([sig.signature.subarray(0, 64), Buffer.of(sig.signature[64] + 27)]) }));
      assert.equal((await createClient(v27, srv.url, StreamTest).unary({ value: 'u' })).value, 'u');
      assert.deepEqual(srv.checks.map((c) => c.valid), [true, true]);
      assert.match(srv.checks[1].signature, /^0x[0-9a-f]{128}(1b|1c)$/);
    });
  });

  it('the stream timeout covers the wait for the first message', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest, { streamTimeoutMs: 200 });
      async function* stalled() {
        await new Promise(() => {});
        yield { value: 'never' };
      }
      const timedOut = giveUp('the stream timeout did not end the wait for the first message');
      await assert.rejects(Promise.race([client.clientStream(stalled()), timedOut]), isCode(Code.DeadlineExceeded));
      assert.equal(srv.checks.length, 0, 'nothing is sent without a first message');
    });
  });

  it('a refused bidirectional stream rejects only when iterated, never unhandled', async () => {
    const unhandled: unknown[] = [];
    const onUnhandled = (reason: unknown) => { unhandled.push(reason); };
    process.on('unhandledRejection', onUnhandled);
    try {
      const client = createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest);
      const drain = async (stream: AsyncIterable<unknown>) => {
        for await (const _ of stream) { /* drain */ }
      };
      const refusedBidi = client.bidi(stringValues('m1'));
      await new Promise((resolve) => setTimeout(resolve, 50)); // time for an unhandled rejection to be reported
      assert.deepEqual(unhandled, []);
      await assert.rejects(drain(refusedBidi), isCode(Code.Unimplemented));
    } finally {
      process.off('unhandledRejection', onUnhandled);
    }
  });

  it('a signing function that never returns is ended by the deadline, and nothing is sent', async () => {
    await withServer(async (srv) => {
      const never = () => new Promise<Signature>(() => {});
      const client = createClient(never, srv.url, StreamTest, { timeoutMs: 100, streamTimeoutMs: 100 });
      const drain = async (stream: AsyncIterable<unknown>) => {
        for await (const _ of stream) { /* drain */ }
      };
      const timedOut = giveUp('the deadline did not end the wait for the signature');
      await assert.rejects(Promise.race([client.unary({ value: 'u' }), timedOut]), isCode(Code.DeadlineExceeded));
      await assert.rejects(Promise.race([client.clientStream(stringValues('c')), timedOut]), isCode(Code.DeadlineExceeded));
      await assert.rejects(Promise.race([drain(client.serverStream({ value: 's' })), timedOut]), isCode(Code.DeadlineExceeded));
      assert.equal(srv.checks.length, 0, 'nothing is sent');
    });
  });

  it('the caller\'s signal cancels a running server stream', async () => {
    await withNeverEndingServer(async (url, server) => {
      const client = createClient(newKeypair().privateKeyHex, url, StreamTest);
      const caller = new AbortController();
      const it = client.serverStream({ value: 's' }, { signal: caller.signal })[Symbol.asyncIterator]();
      await Promise.race([server.requestArrived, giveUp('the request did not arrive')]);
      caller.abort();
      await Promise.race([server.closed, giveUp('the request stayed open after the caller aborted')]);
      await assert.rejects(it.next(), isCode(Code.Canceled));
    });
  });

  it('leaving a server stream early cancels the call', async () => {
    await withNeverEndingServer(async (url, server) => {
      const client = createClient(newKeypair().privateKeyHex, url, StreamTest);
      const timers = () => process.getActiveResourcesInfo().filter((r) => r === 'Timeout').length;
      const before = timers();
      for await (const resp of client.serverStream({ value: 's' })) {
        assert.equal(resp.value, 'ok');
        break;
      }
      assert.equal(timers(), before, 'the call\'s deadline timer is cleared');
      await Promise.race([server.closed, giveUp('the call stayed open after the loop was left')]);
    });
  });

  it('a bidirectional stream fails with unimplemented and sends nothing', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const drain = async () => {
        for await (const _ of client.bidi(stringValues('m1'))) { /* drain */ }
      };
      await assert.rejects(drain(), isCode(Code.Unimplemented));
      assert.equal(srv.checks.length, 0, 'nothing is sent');
    });
  });
});

// ---- Streaming requests as connect-es builds them ----

interface SentStream {
  url: string;
  method: string;
  header: Headers;
  chunks: Buffer[];
}

// flags 0x02 (end of stream), length 2, "{}"
const END_STREAM_HEX = '02000000027b7d';

// Records what the signing client hands the HTTP client, and answers with one "ok" and the end of
// the stream.
function recordingStreamClient(sent: SentStream[]): UniversalClientFn {
  return async (req) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req.body ?? []) {
      chunks.push(Buffer.from(chunk));
    }
    sent.push({ url: req.url, method: req.method, header: new Headers(req.header), chunks });
    return {
      status: 200,
      header: new Headers({ 'Content-Type': 'application/connect+proto' }),
      body: (async function* () { yield Buffer.from(envelopeOf('ok') + END_STREAM_HEX, 'hex'); })(),
      trailer: new Headers(),
    };
  };
}

function splitEnvelopes(body: Buffer): Buffer[] {
  const envelopes: Buffer[] = [];
  for (let at = 0; at < body.length;) {
    const end = at + 5 + body.readUInt32BE(at + 1);
    envelopes.push(body.subarray(at, end));
    at = end;
  }
  return envelopes;
}

// The signing HTTP client signs the first chunk that connect-es hands it, so each chunk must be
// exactly one envelope. These calls go through connect-es's createTransport with the options the
// SDK gives it, and the bytes it builds must be the vector's.
describe('Streaming requests as connect-es builds them, pinned to stream_signing_cases', () => {
  const BASE_URL = 'http://127.0.0.1:9'; // never dialed: only used with a recording client
  const STREAM_TIMEOUT_MS = 300_000;
  const signer = CreateSigner(vectors.keys.private_key);

  const cases: [string, string, (client: Client<typeof StreamTest>) => Promise<unknown>][] = [
    ['connect-client-stream', 'ClientStream', (c) => c.clientStream(stringValues('chunk-1', 'chunk-2', 'chunk-3'))],
    ['multi-byte-length', 'ClientStream', (c) => c.clientStream(stringValues('x'.repeat(300), 'chunk-2'))],
    ['empty-client-stream', 'ClientStream', (c) => c.clientStream(stringValues())],
    ['server-stream-request', 'ServerStream', async (c) => { for await (const _ of c.serverStream({ value: 'hello' })) { /* drain */ } }],
  ];
  for (const [name, procedure, call] of cases) {
    it(`${name}: the body is the vector's, one envelope per chunk, the first signed with the vector signature`, async (t: TestContext) => {
      const vec = vectors.stream_signing_cases.find((v: { name: string }) => v.name === name);
      t.mock.method(Date, 'now', () => vec.timestamp_ms);
      const sent: SentStream[] = [];
      const transport = createTransport({
        ...transportOptions(signer, BASE_URL, STREAM_TIMEOUT_MS, WireFormat.Binary),
        httpClient: createSigningHttpClient(signer, recordingStreamClient(sent)),
      });
      await call(createConnectClient(StreamTest, transport));

      assert.equal(sent.length, 1);
      const [s] = sent;
      assert.equal(s.url, `${BASE_URL}/test.v1.StreamTest/${procedure}`);
      assert.equal(s.method, 'POST');
      const plain = new Headers(s.header);
      for (const header of [NetworkHeaders.Signature, NetworkHeaders.PublicKey, NetworkHeaders.SignatureTimestamp, 'User-Agent']) {
        plain.delete(header);
      }
      assert.deepEqual(Object.fromEntries(plain), {
        'connect-accept-encoding': 'gzip',
        'connect-protocol-version': '1',
        'connect-timeout-ms': String(STREAM_TIMEOUT_MS),
        'content-type': vec.content_type,
      });

      const body = Buffer.concat(s.chunks);
      assert.equal(body.toString('hex'), vec.body_hex);
      assert.deepEqual(s.chunks.map((c) => c.toString('hex')), splitEnvelopes(body).map((e) => e.toString('hex')), 'one envelope per chunk');
      assert.equal((s.chunks[0] ?? Buffer.alloc(0)).toString('hex'), vec.signed_hex);
      assert.match(s.header.get(NetworkHeaders.Signature) ?? '', new RegExp(`^0x${vec.expected_signature}0[01]$`));
      assert.equal(s.header.get(NetworkHeaders.PublicKey), '0x' + vectors.keys.public_key);
      assert.equal(s.header.get(NetworkHeaders.SignatureTimestamp), String(vec.timestamp_ms));
    });
  }
});

// Settles `outcome` with "end" when a request's whole body arrived, or "aborted" when the client
// cut it off. A request that ends gets one "ok" and the end of the stream.
async function withBodyEndServer(fn: (url: string, server: { firstChunk: Promise<void>; outcome: Promise<string> }) => Promise<void>) {
  let gotFirstChunk = () => {};
  let settle: (outcome: string) => void = () => {};
  const events = {
    firstChunk: new Promise<void>((resolve) => { gotFirstChunk = resolve; }),
    outcome: new Promise<string>((resolve) => { settle = resolve; }),
  };
  const server = http.createServer((req, res) => {
    req.once('data', () => gotFirstChunk());
    req.on('end', () => {
      settle('end');
      res.writeHead(200, { 'Content-Type': 'application/connect+proto' }).end(Buffer.from(envelopeOf('ok') + END_STREAM_HEX, 'hex'));
    });
    req.on('error', () => settle('aborted'));
    req.on('close', () => {
      if (!req.complete) {
        settle('aborted');
      }
    });
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await fn(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, events);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

// docs/STREAMING.md: after the first message was sent, a failed source or a cancellation fails the
// call and aborts the request, so the server never handles a partial upload as complete.
describe('A client stream that fails after its first message is aborted, never ended', { timeout: 20_000 }, () => {
  it('a source that throws fails the call, and the server sees the request aborted', async () => {
    await withBodyEndServer(async (url, server) => {
      const client = createClient(newKeypair().privateKeyHex, url, StreamTest);
      async function* throwsAfterFirst() {
        yield { value: 'm1' };
        await server.firstChunk;
        throw new Error('source failed');
      }
      await assert.rejects(
        Promise.race([client.clientStream(throwsAfterFirst()), giveUp('the call did not fail')]),
        isCode(Code.Internal, 'source failed'),
      );
      assert.equal(await Promise.race([server.outcome, giveUp('the request neither ended nor was aborted')]), 'aborted');
    });
  });

  it('a caller that cancels fails the call with canceled, and the server sees the request aborted', async () => {
    await withBodyEndServer(async (url, server) => {
      const client = createClient(newKeypair().privateKeyHex, url, StreamTest);
      const caller = new AbortController();
      async function* cancelledAfterFirst() {
        yield { value: 'm1' };
        await server.firstChunk;
        caller.abort();
        await new Promise(() => {}); // the source never ends by itself
      }
      await assert.rejects(
        Promise.race([client.clientStream(cancelledAfterFirst(), { signal: caller.signal }), giveUp('the call did not fail')]),
        isCode(Code.Canceled),
      );
      assert.equal(await Promise.race([server.outcome, giveUp('the request neither ended nor was aborted')]), 'aborted');
    });
  });
});
