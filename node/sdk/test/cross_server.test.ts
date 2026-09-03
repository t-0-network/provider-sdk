import { describe, it, before } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import fs from 'node:fs';
import type { AddressInfo } from 'node:net';
import { createClient } from '../src/client/client.js';
import { createHandler } from '../src/index.js';
import { Health } from '../src/service/health_pb.js';

const execFileAsync = promisify(execFile);

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

async function bootNodeServer(
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

if (!goAvailable() && process.env.CI) {
  throw new Error(`Go helper binary required in CI but not found at ${GO_HELPER}`);
}

describe('Cross-language: Node ↔ Go', { skip: !goAvailable() ? `Go helper not found at ${GO_HELPER}` : undefined }, () => {

  describe('Node client → Go server', () => {
    it('Health check round-trip', async () => {
      const srv = net.createServer();
      await new Promise<void>(r => srv.listen(0, '127.0.0.1', r));
      const port = (srv.address() as AddressInfo).port;
      srv.close();

      const goServer = spawn(GO_HELPER, ['serve', String(port), CLIENT_PUBLIC_KEY], {
        stdio: ['ignore', 'pipe', 'pipe'],
      });

      try {
        await waitForPort(port);

        const client = createClient(CLIENT_PRIVATE_KEY, `http://127.0.0.1:${port}`, Health);
        const resp = await client.check({ service: '' });
        assert.ok(resp, 'Health check response should not be null');
      } finally {
        goServer.kill();
      }
    });
  });

  describe('Go client → Node server', () => {
    it('Health check round-trip', async () => {
      const { url, close } = await bootNodeServer(CLIENT_PUBLIC_KEY);

      try {
        const { stdout, stderr } = await execFileAsync(GO_HELPER, [
          'call-health', url, CLIENT_PRIVATE_KEY,
        ], { timeout: 15_000 });

        assert.ok(
          stdout.toLowerCase().includes('status=serving'),
          `Go health check failed: stdout=${stdout}, stderr=${stderr}`,
        );
      } finally {
        await close();
      }
    });
  });
});
