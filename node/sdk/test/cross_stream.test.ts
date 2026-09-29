import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { spawn, type ChildProcess } from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';
import type { AddressInfo } from 'node:net';
import { Code, ConnectError, createClient as createConnectClient } from '@connectrpc/connect';
import { createTransport } from '@connectrpc/connect/protocol-connect';
import { createClient } from '../src/client/client.js';
import { CreateSigner } from '../src/client/signer.js';
import { streamTransportOptions } from '../src/common/client/client.js';
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
// does: it answers 401 unless the signature verifies over the first request envelope.
describe('Cross-language streaming: Node client → Go server', { skip: !goAvailable() ? `Go helper not found at ${GO_HELPER}` : undefined, timeout: 30_000 }, () => {
  let goServer: ChildProcess;
  let url: string;

  before(async () => {
    const port = await freePort();
    goServer = spawn(GO_HELPER, ['serve', String(port), CLIENT_PUBLIC_KEY], {
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    await waitForPort(port);
    url = `http://127.0.0.1:${port}`;
  });

  after(() => {
    goServer?.kill();
  });

  it('ClientStream: every message arrives', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    const resp = await client.clientStream(stringValues('m1', 'm2', 'm3'));
    assert.equal(resp.value, 'm1,m2,m3');
  });

  it('ServerStream: every response arrives', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    const got: string[] = [];
    for await (const resp of client.serverStream({ value: 'hello' })) {
      got.push(resp.value);
    }
    assert.deepEqual(got, ['hello', 'hello', 'hello']);
  });

  it('a client stream signed over its whole body is rejected', async () => {
    const signer = CreateSigner(CLIENT_PRIVATE_KEY);
    const transport = createTransport({ ...streamTransportOptions(signer, url), httpClient: bufferingFetchClient(signer) });
    const client = createConnectClient(StreamTest, transport);
    await assert.rejects(client.clientStream(stringValues('m1', 'm2', 'm3')), isCode(Code.Unauthenticated));
  });

  // The SDK signs an empty client stream over empty bytes and sends it; the network refuses it.
  it('an empty client stream is rejected', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, StreamTest);
    await assert.rejects(client.clientStream(stringValues()), isCode(Code.Unauthenticated));
  });
});
