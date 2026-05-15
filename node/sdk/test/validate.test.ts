import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { create } from '@bufbuild/protobuf';
import { Code, ConnectError } from '@connectrpc/connect';
import { DecimalSchema } from '../src/common/gen/tzero/v1/common/common_pb.js';
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
