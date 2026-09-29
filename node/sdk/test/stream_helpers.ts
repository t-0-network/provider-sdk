import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { create, createFileRegistry } from '@bufbuild/protobuf';
import type { GenService } from '@bufbuild/protobuf/codegenv2';
import {
  FileDescriptorProtoSchema,
  file_google_protobuf_wrappers,
  type StringValueSchema,
} from '@bufbuild/protobuf/wkt';
import { Code, ConnectError } from '@connectrpc/connect';
import { secp256k1 } from '@noble/curves/secp256k1.js';

// test.v1.StreamTest (cross_test/stream_test.proto), described by hand so nothing is generated.
// Unary and Bidi are not in stream_test.proto; go_helper serves neither.
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
        { name: 'Bidi', inputType: '.google.protobuf.StringValue', outputType: '.google.protobuf.StringValue', clientStreaming: true, serverStreaming: true },
      ],
    }],
  }),
  (name) => (name === file_google_protobuf_wrappers.proto.name ? file_google_protobuf_wrappers : undefined),
);

export const StreamTest = streamTestFile.getService('test.v1.StreamTest') as GenService<{
  clientStream: { methodKind: 'client_streaming'; input: typeof StringValueSchema; output: typeof StringValueSchema };
  serverStream: { methodKind: 'server_streaming'; input: typeof StringValueSchema; output: typeof StringValueSchema };
  unary: { methodKind: 'unary'; input: typeof StringValueSchema; output: typeof StringValueSchema };
  bidi: { methodKind: 'bidi_streaming'; input: typeof StringValueSchema; output: typeof StringValueSchema };
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

// For assert.rejects: the error is a ConnectError with this code.
export function isCode(code: Code) {
  return (err: unknown) => {
    assert.ok(err instanceof ConnectError, `want a ConnectError, got ${err}`);
    assert.equal(err.code, code, `code of ${err.message}`);
    return true;
  };
}
