import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import type { AddressInfo } from 'node:net';
import { Code, createConnectRouter, type ServiceImpl } from '@connectrpc/connect';
import { universalRequestFromNodeRequest, universalResponseToNodeResponse } from '@connectrpc/connect-node';
import { createClient, WireFormat, type Signature } from '../src/client/client.js';
import { computeDigest, NetworkHeaders, parsePublicKey, publicKeysEqual, verifySignature } from '../src/crypto/index.js';
import { StreamTest, isCode, newKeypair, stringValues } from './stream_helpers.js';

interface Check {
  procedure: string;
  contentType: string;
  timeoutMs: string | null;
  // The first envelope of a stream (empty without one), the whole body of a unary call.
  signed: Buffer;
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

  it('a fractional timeout is rounded up to whole milliseconds', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest, { timeoutMs: 4_320.2, streamTimeoutMs: 8_764.5 });
      await client.unary({ value: 'u' });
      await client.clientStream(stringValues('c'));
      await client.unary({ value: 'u' }, { timeoutMs: 999.1 });

      assert.deepEqual(srv.checks.map((c) => c.timeoutMs), ['4321', '8765', '1000']);
    });
  });

  // 0 and null would mean no deadline; values from 2^31 ms (Infinity included) make Node fire the
  // timer at once, so every call would fail at once.
  const notTimeouts = [0, null, Infinity, 2 ** 31] as number[];

  it('a timeout that is 0, null, or too large for a Node timer is refused', () => {
    for (const ms of notTimeouts) {
      for (const [name, opts] of [['timeoutMs', { timeoutMs: ms }], ['streamTimeoutMs', { streamTimeoutMs: ms }]] as const) {
        assert.throws(() => createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest, opts), {
          name: 'RangeError',
          message: `${name} must be a positive duration of at most 2147483647 ms`,
        });
      }
    }
    assert.doesNotThrow(() => createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest, { timeoutMs: 2 ** 31 - 1, streamTimeoutMs: 2 ** 31 - 1 }));
  });

  it('a call timeoutMs that is 0, null, or too large for a Node timer is refused, and nothing is sent', async () => {
    await withServer(async (srv, key) => {
      const client = createClient(key.privateKeyHex, srv.url, StreamTest);
      const drain = async (stream: AsyncIterable<unknown>) => {
        for await (const _ of stream) { /* drain */ }
      };
      const refused = { name: 'RangeError', message: 'timeoutMs must be a positive duration of at most 2147483647 ms' };
      for (const timeoutMs of notTimeouts) {
        await assert.rejects(client.unary({ value: 'u' }, { timeoutMs }), refused);
        await assert.rejects(client.clientStream(stringValues('c'), { timeoutMs }), refused);
        await assert.rejects(drain(client.serverStream({ value: 's' }, { timeoutMs })), refused);
      }
      assert.equal(srv.checks.length, 0, 'nothing is sent');
    });
  });

  it('the base URL must be http or https with a host; without one the default applies', () => {
    const key = newKeypair().privateKeyHex;
    for (const url of ['', null] as unknown as string[]) {
      assert.throws(() => createClient(key, url, StreamTest), { message: 'base URL is not set' });
    }
    for (const url of [
      'ftp://h', 'http://', 'http://user@h', 'http://my_host:8080', 'https://api.t-0.network?x', 'http://h:0',
      'http://h:99999', 'http://1.2.3', 'http://h:080', 'http://h\t', 'https://api.t-0.network//',
      'https://api.t-0.network/v1//', 'https://api.t-0.network/a//b', 'https://api.t-0.network/v1/..',
      'https://api.t-0.network/v%31', 'https://api.t-0.network/v1?x', 'http://[::1%1]', 'http://[v1.fe]',
      'http://h.', 'http://01.2.3.4', 'http://1abc',
    ]) {
      assert.throws(() => createClient(key, url, StreamTest), { message: 'base URL is not valid' }, url);
    }
    for (const url of [
      undefined,
      'https://api.t-0.network', 'https://api.t-0.network/', 'http://localhost:8080', 'http://127.0.0.1:1234',
      'http://[::1]:8080', 'api.t-0.network', 'api.t-0.network:443',
      'https://api.t-0.network/v1', 'https://api.t-0.network/v1/', 'https://api.t-0.network/sda/payments/t0',
      'HTTPS://api.t-0.network', 'http://[::1]', 'http://[::ffff:1.2.3.4]:8080', 'https://xn--bcher-kva.example',
    ]) {
      assert.doesNotThrow(() => createClient(key, url, StreamTest), String(url));
    }
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

  it('a missing private key is refused', () => {
    for (const key of ['', null, undefined] as unknown as string[]) {
      assert.throws(() => createClient(key, 'http://127.0.0.1:9', StreamTest), { message: 'private key must not be null or empty' });
    }
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

  it('a refused server stream rejects only when iterated, never unhandled', async () => {
    const unhandled: unknown[] = [];
    const onUnhandled = (reason: unknown) => { unhandled.push(reason); };
    process.on('unhandledRejection', onUnhandled);
    try {
      const client = createClient(newKeypair().privateKeyHex, 'http://127.0.0.1:9', StreamTest);
      const drain = async (stream: AsyncIterable<unknown>) => {
        for await (const _ of stream) { /* drain */ }
      };
      const refusedTimeout = client.serverStream({ value: 's' }, { timeoutMs: 0 });
      const refusedBidi = client.bidi(stringValues('m1'));
      await new Promise((resolve) => setTimeout(resolve, 50)); // time for an unhandled rejection to be reported
      assert.deepEqual(unhandled, []);
      await assert.rejects(drain(refusedTimeout), { name: 'RangeError', message: 'timeoutMs must be a positive duration of at most 2147483647 ms' });
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
