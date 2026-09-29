// Shared by streaming.test.ts and cross_stream.test.ts.
import { randomBytes } from 'node:crypto';
import { create, createFileRegistry } from '@bufbuild/protobuf';
import type { GenService } from '@bufbuild/protobuf/codegenv2';
import {
  FileDescriptorProtoSchema,
  file_google_protobuf_wrappers,
  type StringValueSchema,
} from '@bufbuild/protobuf/wkt';
import { universalClientResponseFromFetch, type UniversalClientFn } from '@connectrpc/connect/protocol';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { signatureHeaders } from '../src/common/client/sign.js';
import type { SignerFunction } from '../src/common/client/client.js';

// test.v1.StreamTest (cross_test/stream_test.proto), built by hand on google.protobuf.StringValue
// so that no code has to be generated for it. Unary is not served by the Go helper, only by the
// local test servers in streaming.test.ts and unary_wire.test.ts, which check unary calls.
const streamTestFile = createFileRegistry(
  create(FileDescriptorProtoSchema, {
    name: 'test/v1/stream_test.proto',
    package: 'test.v1',
    dependency: [file_google_protobuf_wrappers.proto.name],
    syntax: 'proto3',
    service: [{
      name: 'StreamTest',
      method: [
        { name: 'ClientStream', inputType: '.google.protobuf.StringValue', outputType: '.google.protobuf.StringValue', clientStreaming: true },
        { name: 'ServerStream', inputType: '.google.protobuf.StringValue', outputType: '.google.protobuf.StringValue', serverStreaming: true },
        { name: 'Unary', inputType: '.google.protobuf.StringValue', outputType: '.google.protobuf.StringValue' },
      ],
    }],
  }),
  (name) => (name === file_google_protobuf_wrappers.proto.name ? file_google_protobuf_wrappers : undefined),
);

export const StreamTest = streamTestFile.getService('test.v1.StreamTest') as GenService<{
  clientStream: { methodKind: 'client_streaming'; input: typeof StringValueSchema; output: typeof StringValueSchema };
  serverStream: { methodKind: 'server_streaming'; input: typeof StringValueSchema; output: typeof StringValueSchema };
  unary: { methodKind: 'unary'; input: typeof StringValueSchema; output: typeof StringValueSchema };
}>;

export function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return {
    privateKeyHex: '0x' + Buffer.from(priv).toString('hex'),
    publicKeyHex: '0x' + Buffer.from(pub).toString('hex'),
  };
}

export async function* stringValues(...values: string[]) {
  for (const value of values) {
    yield { value };
  }
}

/**
 * An HTTP client for connect's streaming transport that reads the whole request body before it
 * sends it. With a signer it signs that whole body, which is what the network must reject for a
 * stream of two or more messages; without one it sends the request unsigned.
 */
export function bufferingFetchClient(signer?: SignerFunction): UniversalClientFn {
  return async (req) => {
    const chunks: Uint8Array[] = [];
    for await (const chunk of req.body ?? []) {
      chunks.push(chunk);
    }
    const body = Buffer.concat(chunks);
    if (signer) {
      for (const [name, value] of await signatureHeaders(signer, body)) {
        req.header.set(name, value);
      }
    }
    const res = await fetch(req.url, { method: req.method, headers: req.header, body, signal: req.signal });
    return universalClientResponseFromFetch(res);
  };
}
