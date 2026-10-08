import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { spawn, type ChildProcess } from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';
import type { AddressInfo } from 'node:net';
import { Code, ConnectError } from '@connectrpc/connect';
import { createClient, downloadFile, KycFileService, uploadFile } from '../src/index.js';

const GO_HELPER = path.resolve(import.meta.dirname, '..', '..', '..', 'cross_test', 'go_helper', 'go_helper');
const CLIENT_PRIVATE_KEY = '0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8';
const CLIENT_PUBLIC_KEY = '0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0';
const SHAPE = 'download stream must be one metadata message followed by chunks';

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

function patterned(n: number): Uint8Array {
  const data = new Uint8Array(n);
  for (let i = 0; i < n; i++) data[i] = i & 0xff;
  return data;
}

if (!goAvailable() && process.env.CI) {
  throw new Error(`Go helper binary required in CI but not found at ${GO_HELPER}`);
}

describe('KYC file helpers: Node client → Go server', { skip: !goAvailable() ? `Go helper not found at ${GO_HELPER}` : undefined, timeout: 30_000 }, () => {
  let goServer: ChildProcess;
  let url: string;

  before(async () => {
    const port = await freePort();
    goServer = spawn(GO_HELPER, ['serve', String(port), CLIENT_PUBLIC_KEY], { stdio: ['ignore', 'pipe', 'pipe'] });
    goServer.stderr!.resume();
    goServer.stdout!.resume();
    await waitForPort(port);
    url = `http://127.0.0.1:${port}`;
  });

  after(() => {
    goServer?.kill();
  });

  const options = { timeoutMs: 30_000 };

  it('uploads 2.5 MiB and downloads the same bytes', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, KycFileService);
    const data = patterned(2621440);
    const fileId = await uploadFile(client, {
      payoutProviderId: 7,
      clientId: 'applicant-1',
      fileName: 'passport.pdf',
      declaredContentType: 'application/pdf',
      uploadId: 'upload-1',
    }, data, options);
    assert.ok(fileId > 0n);
    const downloaded = await downloadFile(client, {
      fileId, payoutRequesterId: 3, payoutProviderId: 7, clientId: 'applicant-1',
    }, options);
    assert.equal(downloaded.metadata.contentType, 'application/pdf');
    assert.equal(downloaded.metadata.fileName, 'passport.pdf');
    assert.deepEqual(downloaded.data, data);
  });

  it('download of an unknown id fails with not found', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, KycFileService);
    await assert.rejects(
      downloadFile(client, { fileId: 42n, payoutRequesterId: 3, payoutProviderId: 7, clientId: 'applicant-1' }, options),
      (err: unknown) => err instanceof ConnectError && err.code === Code.NotFound,
    );
  });

  it('a chunk before metadata fails with the shared shape message', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, KycFileService);
    await assert.rejects(
      downloadFile(client, {
        fileId: 9223372036854775807n, payoutRequesterId: 3, payoutProviderId: 7, clientId: 'applicant-1',
      }, options),
      (err: unknown) => err instanceof ConnectError && err.code === Code.InvalidArgument && err.rawMessage === SHAPE,
    );
  });

  it('a denied upload fails with permission denied, not a write error', async () => {
    const client = createClient(CLIENT_PRIVATE_KEY, url, KycFileService);
    await assert.rejects(
      uploadFile(client, {
        payoutProviderId: 7, clientId: 'kyc-file-permission-denied',
      }, patterned(8388608), options),
      (err: unknown) => err instanceof ConnectError && err.code === Code.PermissionDenied,
    );
  });
});
