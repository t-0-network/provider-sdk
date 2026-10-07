import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import type { AddressInfo } from 'node:net';
import { create, createFileRegistry, setExtension, type Message, type Registry } from '@bufbuild/protobuf';
import type { GenMessage, GenService } from '@bufbuild/protobuf/codegenv2';
import {
  FieldDescriptorProto_Label,
  FieldDescriptorProto_Type,
  FieldOptionsSchema,
  FileDescriptorProtoSchema,
  file_google_protobuf_empty,
  type EmptySchema,
} from '@bufbuild/protobuf/wkt';
import { Code, ConnectError } from '@connectrpc/connect';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import {
  FieldRulesSchema,
  PredefinedRulesSchema,
  StringRulesSchema,
  field as fieldRules,
  file_buf_validate_validate,
  predefined,
} from '../src/common/gen/buf/validate/validate_pb.js';
import { DecimalSchema } from '../src/common/gen/tzero/v1/common/common_pb.js';
import { ApprovePaymentQuoteResponseSchema } from '../src/common/gen/tzero/v1/payment/provider_pb.js';
import { createClient } from '../src/client/client.js';
import { createHandler } from '../src/service/node.js';
import { validate } from '../src/service/validate.js';
import { createValidationInterceptor } from '../src/service/validate_response.js';

describe('validate() helper', () => {
  it('returns the same message on success', () => {
    const msg = create(DecimalSchema, { unscaled: 12345n, exponent: 2 });
    const out = validate(DecimalSchema, msg);
    assert.equal(out, msg);
    assert.equal(out.exponent, 2);
    assert.equal(out.unscaled, 12345n);
  });

  it('throws ConnectError Code.Internal on invalid message', () => {
    const msg = create(DecimalSchema, { exponent: 100 });
    assert.throws(
      () => validate(DecimalSchema, msg),
      (err: unknown) => {
        assert.ok(err instanceof ConnectError, 'expected ConnectError');
        assert.equal((err as ConnectError).code, Code.Internal);
        assert.match((err as ConnectError).message, /response validation failed/);
        return true;
      },
    );
  });

  it('names each violation by its field path, as every SDK does', () => {
    const msg = create(ApprovePaymentQuoteResponseSchema);
    assert.throws(() => validate(ApprovePaymentQuoteResponseSchema, msg), (err: unknown) => {
      assert.ok(err instanceof ConnectError);
      assert.equal(err.rawMessage, 'response validation failed: result: exactly one field is required in oneof');
      return true;
    });
  });

  it('preserves narrow TypeScript type', () => {
    // Compile-time check: out is typed as Decimal, not Message.
    const msg = create(DecimalSchema, { unscaled: 100n, exponent: 0 });
    const out = validate(DecimalSchema, msg);
    // Accessing a field that only exists on Decimal would fail to compile
    // if the generic was widened to Message.
    assert.equal(typeof out.exponent, 'number');
  });
});

describe('validate() helper — handler propagation', () => {
  // Regression guard: when a handler calls `validate(Schema, invalidResp)`
  // and lets the thrown error propagate, the wire response must match what
  // the safety-net interceptor produces — Code.Internal with the same
  // "response validation failed: ..." wording (see validation.test.ts:111-121).
  // This protects against a future change to the helper that might leak a
  // non-Connect error.
  it('propagates as Code.Internal with same wire wording as safety-net interceptor', async () => {
    // Silence the safety-net's default console.error log (handler already
    // produced the same shape; the interceptor catches it again on the way out).
    const silentLogger = { error: () => {} };
    const interceptor = createValidationInterceptor(silentLogger);

    function makeReq(message: ReturnType<typeof create<typeof DecimalSchema>>) {
      return {
        stream: false as const,
        service: { typeName: 'test.Service' },
        method: { name: 'Test', kind: 0, input: DecimalSchema, output: DecimalSchema, idempotency: undefined },
        header: new Headers(),
        contextValues: undefined,
        message,
      };
    }

    // Handler calls validate() with an invalid response and lets it throw.
    const handler = interceptor((_req: any) => {
      const invalid = create(DecimalSchema, { exponent: 100 });
      // validate() throws; the handler never reaches a return.
      const ok = validate(DecimalSchema, invalid);
      return Promise.resolve({
        stream: false,
        header: new Headers(),
        trailer: new Headers(),
        message: ok,
      });
    });

    await assert.rejects(
      () => handler(makeReq(create(DecimalSchema, { exponent: 2 }))),
      (err: unknown) => {
        assert.ok(err instanceof ConnectError, 'expected ConnectError on wire');
        assert.equal((err as ConnectError).code, Code.Internal);
        assert.match((err as ConnectError).message, /response validation failed/);
        return true;
      },
    );
  });
});

// A custom predefined rule, as a downstream SDK defines one and gives createService in `registry`,
// described by hand so nothing is generated:
//
//   // test/v1/rules.proto (proto2)
//   extend buf.validate.StringRules {
//     optional bool valid_tx_hash = 1161 [(buf.validate.predefined).cel = {
//       id: "string.valid_tx_hash", message: "must start with 0x", expression: "!rule || this.startsWith('0x')"}];
//   }
//   // test/v1/transfer.proto
//   message Transfer { string tx_hash = 1 [(buf.validate.field).string.(valid_tx_hash) = true]; }
//   service TransferService { rpc Get(google.protobuf.Empty) returns (Transfer); }
function withExtension<T extends Message>(message: T, apply: (message: T) => void): T {
  apply(message);
  return message;
}

const rulesFile = createFileRegistry(
  create(FileDescriptorProtoSchema, {
    name: 'test/v1/rules.proto',
    package: 'test.v1',
    dependency: [file_buf_validate_validate.proto.name],
    syntax: 'proto2',
    extension: [{
      name: 'valid_tx_hash',
      number: 1161,
      label: FieldDescriptorProto_Label.OPTIONAL,
      type: FieldDescriptorProto_Type.BOOL,
      extendee: '.buf.validate.StringRules',
      options: withExtension(create(FieldOptionsSchema), (o) => setExtension(o, predefined, create(PredefinedRulesSchema, {
        cel: [{ id: 'string.valid_tx_hash', message: 'must start with 0x', expression: "!rule || this.startsWith('0x')" }],
      }))),
    }],
  }),
  (name) => (name === file_buf_validate_validate.proto.name ? file_buf_validate_validate : undefined),
);

const validTxHash = rulesFile.getExtension('test.v1.valid_tx_hash')!;

const customRules: Registry = createFileRegistry(
  create(FileDescriptorProtoSchema, {
    name: 'test/v1/transfer.proto',
    package: 'test.v1',
    dependency: ['test/v1/rules.proto', file_buf_validate_validate.proto.name, file_google_protobuf_empty.proto.name],
    syntax: 'proto3',
    messageType: [{
      name: 'Transfer',
      field: [{
        name: 'tx_hash',
        jsonName: 'txHash',
        number: 1,
        label: FieldDescriptorProto_Label.OPTIONAL,
        type: FieldDescriptorProto_Type.STRING,
        options: withExtension(create(FieldOptionsSchema), (o) => setExtension(o, fieldRules, create(FieldRulesSchema, {
          type: { case: 'string', value: withExtension(create(StringRulesSchema), (r) => setExtension(r, validTxHash, true)) },
        }))),
      }],
    }],
    service: [{
      name: 'TransferService',
      method: [{ name: 'Get', inputType: '.google.protobuf.Empty', outputType: '.test.v1.Transfer' }],
    }],
  }),
  (name) => rulesFile.getFile(name)
    ?? (name === file_google_protobuf_empty.proto.name ? file_google_protobuf_empty : undefined),
);

type Transfer = Message<'test.v1.Transfer'> & { txHash: string };
const TransferSchema = customRules.getMessage('test.v1.Transfer') as GenMessage<Transfer>;
const TransferService = customRules.getService('test.v1.TransferService') as GenService<{
  get: { methodKind: 'unary'; input: typeof EmptySchema; output: typeof TransferSchema };
}>;

// A server built with createHandler whose TransferService.Get answers `respond()`, and a signed client.
async function bootTransferServer(registry: Registry | undefined, respond: () => Transfer) {
  const priv = Uint8Array.from(randomBytes(32));
  const handler = createHandler(
    '0x' + Buffer.from(secp256k1.getPublicKey(priv, false)).toString('hex'),
    (router) => router.service(TransferService, { get: () => respond() }),
    { registry, logger: { error: () => {} } },
  );
  const server = http.createServer(handler);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    client: createClient('0x' + Buffer.from(priv).toString('hex'), `http://127.0.0.1:${port}`, TransferService),
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

// The rawMessage of the Internal ConnectError that `call` throws or rejects with.
async function internalMessage(call: () => unknown): Promise<string> {
  try {
    await call();
  } catch (err) {
    assert.ok(err instanceof ConnectError, `want a ConnectError, got ${err}`);
    assert.equal(err.code, Code.Internal, `code of ${err.message}`);
    return err.rawMessage;
  }
  assert.fail('want an Internal ConnectError, got none');
}

describe('validate() helper — custom predefined rules', () => {
  it('the fixture registry holds the predefined rule', () => {
    assert.equal(customRules.getExtension('test.v1.valid_tx_hash')?.number, 1161);
  });

  it('a valid message passes validate() with the registry and the server given it', async () => {
    const msg = create(TransferSchema, { txHash: '0xabc' });
    assert.equal(validate(TransferSchema, msg, { registry: customRules }), msg);

    const { client, close } = await bootTransferServer(customRules, () => msg);
    try {
      assert.equal((await client.get({})).txHash, '0xabc');
    } finally {
      await close();
    }
  });

  it('an invalid message fails validate() with the registry and the server given it, with the same message', async () => {
    const msg = create(TransferSchema, { txHash: 'abc' });
    const want = 'response validation failed: tx_hash: must start with 0x';
    assert.equal(await internalMessage(() => validate(TransferSchema, msg, { registry: customRules })), want);

    const { client, close } = await bootTransferServer(customRules, () => msg);
    try {
      assert.equal(await internalMessage(() => client.get({})), want);
    } finally {
      await close();
    }
  });

  it('without the registry, validate() cannot resolve the rule, as before and as a server without one', async () => {
    const msg = create(TransferSchema, { txHash: '0xabc' });
    for (const options of [undefined, {}, { registry: undefined }]) {
      const got = await internalMessage(() => validate(TransferSchema, msg, options));
      assert.ok(got.startsWith('response validation error: Unknown extension'), got);
    }
    const helper = await internalMessage(() => validate(TransferSchema, msg));

    const { client, close } = await bootTransferServer(undefined, () => msg);
    try {
      assert.equal(await internalMessage(() => client.get({})), helper);
    } finally {
      await close();
    }
  });

  it('with a registry, the standard rules of other messages give the same messages as without one', async () => {
    const msg = create(DecimalSchema, { exponent: 100 });
    const without = await internalMessage(() => validate(DecimalSchema, msg));
    assert.ok(without.startsWith('response validation failed: exponent: '), without);
    assert.equal(await internalMessage(() => validate(DecimalSchema, msg, { registry: customRules })), without);
  });
});
