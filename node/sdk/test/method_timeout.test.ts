import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { create, createFileRegistry, setExtension, type MessageInitShape } from '@bufbuild/protobuf';
import type { GenService } from '@bufbuild/protobuf/codegenv2';
import {
  DurationSchema,
  FileDescriptorProtoSchema,
  MethodOptionsSchema,
  file_google_protobuf_empty,
  type EmptySchema,
} from '@bufbuild/protobuf/wkt';
import { createClient, MAX_TIMEOUT_MS, NetworkService, ProviderService } from '../src/index.js';
import { methodTimeoutMs } from '../src/common/client/client.js';
import { timeout } from '../src/common/gen/tzero/v1/common/timeout_pb.js';
import { Health } from '../src/service/health_pb.js';
import { newKeypair } from './stream_helpers.js';

// The timeout option of each unary method of timeouttest.S; undefined is none.
const OPTIONS: Record<string, MessageInitShape<typeof DurationSchema> | undefined> = {
  None: undefined,
  Zero: {},
  Negative: { seconds: -1n },
  Longer: { seconds: 90n },
  Nanos: { seconds: 1n, nanos: 500_000_000 },
  SubMillisecond: { nanos: 1 },
  TooLong: { seconds: 10n ** 12n },
};

function optionsWith(duration: MessageInitShape<typeof DurationSchema> | undefined) {
  const options = create(MethodOptionsSchema);
  if (duration !== undefined) {
    setExtension(options, timeout, create(DurationSchema, duration));
  }
  return options;
}

// timeouttest.S, described by hand: the unary methods of OPTIONS, and a server stream with an option of 2 s.
const S = createFileRegistry(
  create(FileDescriptorProtoSchema, {
    name: 'timeouttest/s.proto',
    package: 'timeouttest',
    dependency: [file_google_protobuf_empty.proto.name],
    syntax: 'proto3',
    service: [{
      name: 'S',
      method: [
        ...Object.entries(OPTIONS).map(([name, duration]) => (
          { name, inputType: '.google.protobuf.Empty', outputType: '.google.protobuf.Empty', options: optionsWith(duration) }
        )),
        { name: 'Stream', inputType: '.google.protobuf.Empty', outputType: '.google.protobuf.Empty', serverStreaming: true, options: optionsWith({ seconds: 2n }) },
      ],
    }],
  }),
  (name) => (name === file_google_protobuf_empty.proto.name ? file_google_protobuf_empty : undefined),
).getService('timeouttest.S') as GenService<{
  stream: { methodKind: 'server_streaming'; input: typeof EmptySchema; output: typeof EmptySchema };
}>;

// Records the Connect-Timeout-Ms of every request and answers it with an empty message, or a
// server stream without a message.
async function withRecordingServer(fn: (url: string, timeouts: (string | undefined)[]) => Promise<void>) {
  const timeouts: (string | undefined)[] = [];
  const server = http.createServer((req, res) => {
    timeouts.push(req.headers['connect-timeout-ms'] as string | undefined);
    req.on('end', () => {
      if (String(req.headers['content-type']).startsWith('application/connect+')) {
        // The end-of-stream envelope: flags 0x02, length 2, "{}".
        res.writeHead(200, { 'Content-Type': 'application/connect+proto' }).end(Buffer.from([2, 0, 0, 0, 2, 0x7b, 0x7d]));
      } else {
        res.writeHead(200, { 'Content-Type': 'application/proto' }).end();
      }
    });
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
  it('an option that is missing, or not greater than 0, counts as none; one is read in whole ms, rounded up, at most MAX_TIMEOUT_MS', () => {
    const got = Object.fromEntries(S.methods.filter((m) => m.name in OPTIONS).map((m) => [m.name, methodTimeoutMs(m)]));
    assert.deepEqual(got, {
      None: undefined,
      Zero: undefined,
      Negative: undefined,
      Longer: 90_000,
      Nanos: 1_500,
      SubMillisecond: 1,
      TooLong: MAX_TIMEOUT_MS,
    });
  });

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

  it('a stream reads the option the same way', async () => {
    await withRecordingServer(async (url, timeouts) => {
      const client = createClient(newKeypair().privateKeyHex, url, S, { streamTimeoutMs: 8_765 });
      for await (const _ of client.stream({})) { /* none */ }
      for await (const _ of client.stream({}, { timeoutMs: 60_000 })) { /* none */ }
      assert.deepEqual(timeouts, ['2000', '60000']);
    });
  });
});
