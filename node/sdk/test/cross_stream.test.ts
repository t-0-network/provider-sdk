import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { randomBytes } from 'node:crypto';
import { spawn, type ChildProcess } from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';
import type { AddressInfo } from 'node:net';
import { Code, ConnectError, createClient as createConnectClient } from '@connectrpc/connect';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createClient } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { transportOptions } from '../src/common/client/client.js';
import { StreamTest, bufferingFetchClient, stringValues } from './stream_helpers.js';

const GO_HELPER = path.resolve(import.meta.dirname, '..', '..', '..', 'cross_test', 'go_helper', 'go_helper');

const CLIENT_PRIVATE_KEY = '0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8';
const CLIENT_PUBLIC_KEY = '0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0';

function goAvailable(): boolean {
  try {
    return fs.existsSync(GO_HELPER) && fs.accessSync(GO_HELPER, fs.constants.X_OK) === undefined;
  } catch {
    return false;
  }
}

function waitForPort(port: number, timeout = 10_000): Promise<void> {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeout;
    const tryConnect = () => {
      const sock = net.createConnection({ host: '127.0.0.1', port }, () => {
        sock.destroy();
        resolve();
      });
      sock.on('error', () => {
        if (Date.now() > deadline) {
          reject(new Error(`Port ${port} not ready after ${timeout}ms`));
          return;
        }
        setTimeout(tryConnect, 100);
      });
    };
    tryConnect();
  });
}

async function freePort(): Promise<number> {
  const srv = net.createServer();
  await new Promise<void>((r) => srv.listen(0, '127.0.0.1', r));
  const { port } = srv.address() as AddressInfo;
  await new Promise<void>((r) => srv.close(() => r()));
  return port;
}

function isCode(code: Code) {
  return (err: unknown) => {
    assert.ok(err instanceof ConnectError, `want a ConnectError, got ${err}`);
    assert.equal(err.code, code, `code of ${err.message}`);
    return true;
  };
}

if (!goAvailable() && process.env.CI) {
  throw new Error(`Go helper binary required in CI but not found at ${GO_HELPER}`);
}

// The Go helper serves test.v1.StreamTest behind a verifier that works the way the T-0 Network
// does: it answers 401 unless the signature verifies over the first request envelope. It logs its
// verdict on each request to stderr before the handler reads the rest of the body.
describe('Cross-language streaming: Node client → Go server', { skip: !goAvailable() ? `Go helper not found at ${GO_HELPER}` : undefined, timeout: 30_000 }, () => {
  let goServer: ChildProcess;
  let url: string;
  let log = '';
  const onLog = new Set<() => void>();

  // Resolves once the helper's log contains text at or after offset from. The helper serves every
  // test here, so a test passes log.length from before its call: a line an earlier test caused
  // never satisfies it. The log and the HTTP response arrive on separate pipes, in either order.
  function waitForLog(text: string, from: number, ms = 10_000): Promise<void> {
    return new Promise((resolve, reject) => {
      const check = () => {
        if (log.includes(text, from)) {
          onLog.delete(check);
          clearTimeout(timer);
          resolve();
        }
      };
      const timer = setTimeout(() => {
        onLog.delete(check);
        reject(new Error(`the helper did not log "${text}" within ${ms}ms, it logged:\n${log.slice(from)}`));
      }, ms);
      onLog.add(check);
      check();
    });
  }

  before(async () => {
    const port = await freePort();
    goServer = spawn(GO_HELPER, ['serve', String(port), CLIENT_PUBLIC_KEY], {
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    goServer.stderr!.setEncoding('utf8');
    goServer.stderr!.on('data', (chunk: string) => {
      log += chunk;
      onLog.forEach((check) => check());
    });
    await waitForPort(port);
    url = `http://127.0.0.1:${port}`;
  });

  after(() => {
    goServer?.kill();
  });

  it('ClientStream: every message arrives', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    const resp = await client.clientStream(stringValues('m1', 'm2', 'm3'));
    assert.equal(resp.value, 'm1,m2,m3');
    await waitForLog('/test.v1.StreamTest/ClientStream verified over the first envelope', mark);
  });

  it('ServerStream: every response arrives', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    const got: string[] = [];
    for await (const resp of client.serverStream({ value: 'hello' })) {
      got.push(resp.value);
    }
    assert.deepEqual(got, ['hello', 'hello', 'hello']);
    await waitForLog('/test.v1.StreamTest/ServerStream verified over the first envelope', mark);
  });

  // The signature covers the bytes as sent, whatever the format: Connect JSON streams are
  // verified over their first envelope too.
  it('Connect JSON: client and server streams are verified over the first envelope', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest, { useBinaryFormat: false });
    const resp = await client.clientStream(stringValues('m1', 'm2', 'm3'));
    assert.equal(resp.value, 'm1,m2,m3');
    await waitForLog('/test.v1.StreamTest/ClientStream verified over the first envelope', mark);

    const got: string[] = [];
    for await (const reply of client.serverStream({ value: 'hello' })) {
      got.push(reply.value);
    }
    assert.deepEqual(got, ['hello', 'hello', 'hello']);
    await waitForLog('/test.v1.StreamTest/ServerStream verified over the first envelope', mark);
  });

  // The helper verifies the signature as soon as it has read the first envelope. The caller's
  // stream produces m2 only once the helper has logged that: a transport that buffers the body
  // sends nothing until the stream ends, and the wait fails.
  it('ClientStream is not buffered: the helper verifies m1 before m2 is produced', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    async function* messages() {
      yield { value: 'm1' };
      await waitForLog('/test.v1.StreamTest/ClientStream verified over the first envelope', mark);
      yield { value: 'm2' };
      yield { value: 'm3' };
    }
    const resp = await client.clientStream(messages());
    assert.equal(resp.value, 'm1,m2,m3');
  });

  // A first message far larger than one read: the helper reads it in many chunks, and the
  // signature covers all of them.
  it('ClientStream: a large first message is signed whole', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    const large = randomBytes(192 * 1024).toString('base64'); // 256 KiB
    const resp = await client.clientStream(stringValues(large, 'tail'));
    assert.equal(resp.value, `${large},tail`);
    await waitForLog('/test.v1.StreamTest/ClientStream verified over the first envelope', mark);
  });

  it('an unsigned client stream is rejected', async () => {
    const mark = log.length;
    const transport = createTransport({ ...transportOptions(CreateSigner(CLIENT_PRIVATE_KEY), url), httpClient: bufferingFetchClient() });
    const client = createConnectClient(StreamTest, transport);
    await assert.rejects(client.clientStream(stringValues('m1', 'm2')), isCode(Code.Unauthenticated));
    await waitForLog('/test.v1.StreamTest/ClientStream rejected: unknown public key', mark);
  });

  it('a client stream signed over its whole body is rejected', async () => {
    const mark = log.length;
    const signer = CreateSigner(CLIENT_PRIVATE_KEY);
    const transport = createTransport({ ...transportOptions(signer, url), httpClient: bufferingFetchClient(signer) });
    const client = createConnectClient(StreamTest, transport);
    await assert.rejects(client.clientStream(stringValues('m1', 'm2', 'm3')), isCode(Code.Unauthenticated));
    await waitForLog('/test.v1.StreamTest/ClientStream rejected: signature does not verify over the first message', mark);
  });

  // The signature timestamp is Date.now() when the first message is signed. Two minutes back is
  // outside the helper's ±60 s window; the mock is restored when the test ends.
  it('a client stream signed with a stale timestamp is rejected', async (t) => {
    const mark = log.length;
    const real = Date.now();
    t.mock.method(Date, 'now', () => real - 120_000);
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    await assert.rejects(client.clientStream(stringValues('m1', 'm2')), isCode(Code.Unauthenticated));
    await waitForLog('/test.v1.StreamTest/ClientStream rejected: timestamp is outside the allowed time window', mark);
  });

  // The SDK signs an empty client stream over empty bytes and sends it; the network refuses it.
  it('an empty client stream is rejected', async () => {
    const mark = log.length;
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    await assert.rejects(client.clientStream(stringValues()), isCode(Code.Unauthenticated));
    await waitForLog('/test.v1.StreamTest/ClientStream rejected: no first message', mark);
  });
});
