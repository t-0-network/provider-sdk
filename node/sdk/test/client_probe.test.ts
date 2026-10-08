import { after, before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { once } from 'node:events';
import { createInterface } from 'node:readline';
import path from 'node:path';
import fs from 'node:fs';
import { ConnectError } from '@connectrpc/connect';
import { codeToString } from '@connectrpc/connect/protocol-connect';
import { createClient, newSignerFromHex, type Signature, type SignerFunction } from '../src/index.js';
import { Health, HealthCheckResponse_ServingStatus } from '../src/service/health_pb.js';

const CROSS_TEST = path.resolve(import.meta.dirname, '..', '..', '..', 'cross_test');
const GO_HELPER = path.join(CROSS_TEST, 'go_helper', 'go_helper');
const VECTORS = path.join(CROSS_TEST, 'test_vectors.json');

const goAvailable = fs.existsSync(GO_HELPER);
if (!goAvailable && process.env.CI) {
  throw new Error(`Go helper binary required in CI but not found at ${GO_HELPER}`);
}

const vectors = JSON.parse(fs.readFileSync(VECTORS, 'utf8'));

interface ClientCase {
  name: string;
  client_timeout_ms?: number;
  call_timeout_ms?: number;
  signer?: 'factory';
  custom_signer?: { signature?: 'r_s' | 'v_plus_27' | 'first_63_bytes'; public_key?: string; error?: string };
  expect: { code: string; message?: string };
}

// The case's signer argument: the hex key, the factory's signer, or a custom signer that signs
// with the impostor key and changes its output as the case says.
function signerOf(c: ClientCase): string | SignerFunction {
  if (c.signer === 'factory') {
    return newSignerFromHex(vectors.keys.private_key);
  }
  const custom = c.custom_signer;
  if (custom === undefined) {
    return vectors.keys.private_key;
  }
  const impostor = newSignerFromHex(vectors.impostor_keys.private_key);
  return async (digest: Buffer): Promise<Signature> => {
    if (custom.error !== undefined) {
      throw new Error(custom.error);
    }
    let { signature, publicKey } = await impostor(digest);
    if (custom.signature === 'r_s') {
      signature = signature.subarray(0, 64);
    } else if (custom.signature === 'v_plus_27') {
      signature = Buffer.concat([signature.subarray(0, 64), Buffer.of(signature[64] + 27)]);
    } else if (custom.signature === 'first_63_bytes') {
      signature = signature.subarray(0, 63);
    }
    if (custom.public_key !== undefined) {
      publicKey = Buffer.from(custom.public_key, 'hex');
    }
    return { signature, publicKey };
  };
}

// What the client sends, checked by the reference server of `go_helper client-probe`: every case of
// client_cases in cross_test/test_vectors.json, over Connect, the one protocol the Node client speaks.
describe('shared client cases', { skip: goAvailable ? undefined : `Go helper not found at ${GO_HELPER}` }, () => {
  let probe: ChildProcess;
  let closed: Promise<unknown>;
  let baseUrl = '';
  // Its PASS and FAIL lines: "PASS <case>" or "FAIL <case>: <reason>".
  let log = '';

  // Stops the probe; once it has closed its stderr, log holds every line it wrote.
  async function stopProbe(): Promise<void> {
    probe.kill();
    await closed;
  }

  before(async () => {
    probe = spawn(GO_HELPER, ['client-probe', '--sdk', 'node', '--vectors', VECTORS], { stdio: ['ignore', 'pipe', 'pipe'] });
    closed = once(probe, 'close');
    probe.stderr!.on('data', (chunk: Buffer) => {
      log += chunk.toString();
    });
    const lines = createInterface({ input: probe.stdout! });
    const ready = new Promise<string>((resolve) => lines.once('line', resolve));
    const line = await Promise.race([
      ready,
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error(`client-probe did not start:\n${log}`)), 10_000).unref()),
    ]);
    const m = /^READY (\S+)$/.exec(line);
    assert.ok(m, `client-probe's first line is not READY: ${line}`);
    baseUrl = m[1];
  });

  after(async () => {
    if (probe !== undefined) {
      await stopProbe();
    }
  });

  for (const c of vectors.client_cases as ClientCase[]) {
    it(c.name, async () => {
      const logged = log.length;
      const options = c.client_timeout_ms === undefined ? undefined : { timeoutMs: c.client_timeout_ms };
      const client = createClient(signerOf(c), `${baseUrl}/${c.name}`, Health, options);
      const callOptions = c.call_timeout_ms === undefined ? undefined : { timeoutMs: c.call_timeout_ms };
      let code: string;
      let message = '';
      try {
        const reply = await client.check({ service: '' }, callOptions);
        code = reply.status === HealthCheckResponse_ServingStatus.SERVING ? 'ok' : `status ${reply.status}`;
      } catch (e) {
        const err = ConnectError.from(e);
        code = codeToString(err.code);
        message = err.rawMessage;
      }
      assert.equal(code, c.expect.code, `${message}\n${log}`);
      if (c.expect.message !== undefined) {
        assert.equal(message, c.expect.message);
      }
      const fails = failLines(log.slice(logged), c.name);
      assert.equal(fails.length, 0, `client-probe logged:\n${fails.join('\n')}`);
    });
  }

  // A call that ends on its own deadline can end before the probe logs its request, so the whole log
  // is checked again once the probe has stopped.
  it('client-probe logged no FAIL line', async () => {
    await stopProbe();
    const fails = failLines(log);
    assert.equal(fails.length, 0, `client-probe logged:\n${fails.join('\n')}`);
  });
});

// The FAIL lines of the case name ("FAIL <name>: <reason>"), or every FAIL line without a name.
function failLines(text: string, name?: string): string[] {
  const prefix = name === undefined ? 'FAIL ' : `FAIL ${name}:`;
  return text.split('\n').filter((line) => line.startsWith(prefix));
}
