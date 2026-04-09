import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { createValidator } from '@bufbuild/protovalidate';
import { Code, ConnectError } from '@connectrpc/connect';
import { DecimalSchema, Decimal } from '../src/common/gen/tzero/v1/common/common_pb.js';
import {
  UpdatePaymentResponseSchema,
  AppendLedgerEntriesRequestSchema,
  AppendLedgerEntriesRequest_TransactionSchema,
  AppendLedgerEntriesRequest_LedgerEntrySchema,
  AppendLedgerEntriesRequest_Transaction_PayoutSchema,
} from '../src/common/gen/tzero/v1/payment/provider_pb.js';
import { create } from '@bufbuild/protobuf';
import { createValidationInterceptor } from '../src/service/validate_response.js';

const validator = createValidator();

describe('Response validation', () => {
  it('valid Decimal response passes', () => {
    const msg = create(DecimalSchema, { unscaled: 12345n, exponent: 2 });
    const result = validator.validate(DecimalSchema, msg);
    assert.equal(result.kind, 'valid');
  });

  it('invalid Decimal response fails - exponent too high', () => {
    const msg = create(DecimalSchema, { exponent: 100 });
    const result = validator.validate(DecimalSchema, msg);
    assert.equal(result.kind, 'invalid');
  });

  it('invalid Decimal response fails - exponent too low', () => {
    const msg = create(DecimalSchema, { exponent: -20 });
    const result = validator.validate(DecimalSchema, msg);
    assert.equal(result.kind, 'invalid');
  });

  it('boundary exponent values pass', () => {
    for (const exp of [-8, 0, 8]) {
      const msg = create(DecimalSchema, { exponent: exp });
      const result = validator.validate(DecimalSchema, msg);
      assert.equal(result.kind, 'valid', `exponent ${exp} should pass`);
    }
  });

  it('boundary exponent values fail', () => {
    for (const exp of [-9, 9]) {
      const msg = create(DecimalSchema, { exponent: exp });
      const result = validator.validate(DecimalSchema, msg);
      assert.equal(result.kind, 'invalid', `exponent ${exp} should fail`);
    }
  });

  it('empty response without constraints passes', () => {
    const msg = create(UpdatePaymentResponseSchema);
    const result = validator.validate(UpdatePaymentResponseSchema, msg);
    assert.equal(result.kind, 'valid');
  });
});

describe('Request validation', () => {
  it('valid AppendLedgerEntriesRequest passes', () => {
    const msg = create(AppendLedgerEntriesRequestSchema, {
      transactions: [
        create(AppendLedgerEntriesRequest_TransactionSchema, {
          transactionId: 1n,
          entries: [create(AppendLedgerEntriesRequest_LedgerEntrySchema)],
          transactionDetails: {
            case: 'payout',
            value: create(AppendLedgerEntriesRequest_Transaction_PayoutSchema, { paymentId: 1n }),
          },
        }),
      ],
    });
    const result = validator.validate(AppendLedgerEntriesRequestSchema, msg);
    assert.equal(result.kind, 'valid');
  });

  it('invalid AppendLedgerEntriesRequest fails - empty transactions', () => {
    const msg = create(AppendLedgerEntriesRequestSchema);
    const result = validator.validate(AppendLedgerEntriesRequestSchema, msg);
    assert.equal(result.kind, 'invalid');
  });

  it('valid Decimal request passes', () => {
    const msg = create(DecimalSchema, { unscaled: 100n, exponent: 2 });
    const result = validator.validate(DecimalSchema, msg);
    assert.equal(result.kind, 'valid');
  });

  it('invalid Decimal request fails', () => {
    const msg = create(DecimalSchema, { exponent: 100 });
    const result = validator.validate(DecimalSchema, msg);
    assert.equal(result.kind, 'invalid');
  });
});

describe('Validation interceptor', () => {
  const interceptor = createValidationInterceptor();

  function makeReq(message: Decimal) {
    return {
      stream: false as const,
      service: { typeName: 'test.Service' },
      method: { name: 'Test', kind: 0, input: DecimalSchema, output: DecimalSchema, idempotency: undefined },
      header: new Headers(),
      contextValues: undefined,
      message,
    };
  }

  it('rejects invalid response with Code.Internal', async () => {
    const handler = interceptor((req: any) =>
      Promise.resolve({ stream: false, header: new Headers(), trailer: new Headers(), message: create(DecimalSchema, { exponent: 100 }) })
    );
    await assert.rejects(() => handler(makeReq(create(DecimalSchema, { exponent: 2 }))), (err: any) => {
      assert.ok(err instanceof ConnectError);
      assert.equal(err.code, Code.Internal);
      assert.match(err.message, /response validation failed/);
      return true;
    });
  });

  it('passes valid response through', async () => {
    const handler = interceptor((req: any) =>
      Promise.resolve({ stream: false, header: new Headers(), trailer: new Headers(), message: create(DecimalSchema, { exponent: 2 }) })
    );
    const resp = await handler(makeReq(create(DecimalSchema, { exponent: 2 })));
    assert.equal(resp.message.exponent, 2);
  });

  it('rejects invalid request with Code.InvalidArgument', async () => {
    let called = false;
    const handler = interceptor((req: any) => {
      called = true;
      return Promise.resolve({ stream: false, header: new Headers(), trailer: new Headers(), message: create(DecimalSchema, { exponent: 2 }) });
    });
    await assert.rejects(() => handler(makeReq(create(DecimalSchema, { exponent: 100 }))), (err: any) => {
      assert.ok(err instanceof ConnectError);
      assert.equal(err.code, Code.InvalidArgument);
      return true;
    });
    assert.equal(called, false, 'next should not be called for invalid request');
  });
});
