import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { createClient, NetworkService, ProviderService } from '../src/index.js';
import { Health } from '../src/service/health_pb.js';
import { newKeypair } from './stream_helpers.js';

// Records the Connect-Timeout-Ms of every request and answers it with an empty message.
async function withRecordingServer(fn: (url: string, timeouts: (string | undefined)[]) => Promise<void>) {
  const timeouts: (string | undefined)[] = [];
  const server = http.createServer((req, res) => {
    timeouts.push(req.headers['connect-timeout-ms'] as string | undefined);
    req.on('end', () => res.writeHead(200, { 'Content-Type': 'application/proto' }).end());
    req.resume();
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await fn(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, timeouts);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

describe('the timeout option of a method (tzero.v1.common.timeout)', () => {
  it('replaces the client timeout, shorter or longer; a call\'s own timeoutMs replaces it; a method without it gets the client timeout', async () => {
    await withRecordingServer(async (url, timeouts) => {
      const key = newKeypair().privateKeyHex;
      await createClient(key, url, ProviderService, { timeoutMs: 10_000 }).approvePaymentQuotes({});
      const provider = createClient(key, url, ProviderService, { timeoutMs: 45_000 });
      await provider.approvePaymentQuotes({});
      await provider.approvePaymentQuotes({}, { timeoutMs: 60_000 });
      await provider.approvePaymentQuotes({}, { timeoutMs: 1_000 });
      await createClient(key, url, NetworkService, { timeoutMs: 45_000 }).updateQuote({});
      await createClient(key, url, Health, { timeoutMs: 45_000 }).check({});
      assert.deepEqual(timeouts, ['29000', '29000', '60000', '1000', '30000', '45000']);
    });
  });
});
