import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { DEFAULT_MAX_BODY_SIZE, KYC_FILE_CHUNK_MAX_BYTES, NetworkHeaders, TIMESTAMP_WINDOW_MS, createService, newSignerFromHex } from '../src/index.js';
import type { PathItem } from '@bufbuild/protobuf/reflect';
import * as messages from '../src/common/messages.js';
import { fieldPathString } from '../src/common/field-path.js';
import { DEFAULT_BASE_URL, DEFAULT_STREAM_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS } from '../src/index.js';

// The named constants of the SDK against `constants` in cross_test/test_vectors.json, the file
// every SDK compares its constants with.
const vectors = JSON.parse(
  fs.readFileSync(path.resolve(import.meta.dirname, '..', '..', '..', 'cross_test', 'test_vectors.json'), 'utf8'),
);
const constants: Record<string, unknown> = vectors.constants;

const sdk: Record<string, unknown> = {
  default_max_body_size: DEFAULT_MAX_BODY_SIZE,
  timestamp_window_ms: TIMESTAMP_WINDOW_MS,
  public_key_header: NetworkHeaders.PublicKey,
  signature_header: NetworkHeaders.Signature,
  signature_timestamp_header: NetworkHeaders.SignatureTimestamp,
  default_base_url: DEFAULT_BASE_URL,
  default_timeout_ms: DEFAULT_TIMEOUT_MS,
  default_stream_timeout_ms: DEFAULT_STREAM_TIMEOUT_MS,
  max_timeout_ms: MAX_TIMEOUT_MS,
  kyc_file_chunk_max_bytes: KYC_FILE_CHUNK_MAX_BYTES,
};

describe('shared constants', () => {
  it('defines every shared constant', () => {
    assert.deepEqual(Object.keys(sdk).sort(), Object.keys(constants).sort());
  });
  for (const [name, value] of Object.entries(constants)) {
    it(name, () => assert.equal(sdk[name], value));
  }
});

// The messages the SDK raises itself against `messages`: one constant per message that applies to
// Node, named as in the file in upper case. A message with a placeholder is a function of it, filled
// here with a sample value.
describe('shared messages', () => {
  const scope: Record<string, string[]> = vectors.messages_scope;
  const applies = Object.keys(vectors.messages).filter((name) => scope[name] === undefined || scope[name].includes('node'));
  const sdk = messages as Record<string, unknown>;

  it('defines every message that applies to Node, and no other', () => {
    assert.deepEqual(Object.keys(sdk).sort(), applies.map((name) => name.toUpperCase()).sort());
  });
  for (const name of applies) {
    it(name, () => {
      const text: string = vectors.messages[name];
      const value = sdk[name.toUpperCase()];
      const placeholder = /\{(\w+)\}/.exec(text);
      if (placeholder === null) {
        assert.equal(value, text);
      } else {
        const sample = placeholder[1] === 'limit' ? 10485760 : `<${placeholder[1]}>`;
        assert.equal(typeof value, 'function');
        assert.equal((value as (v: unknown) => string)(sample), text.replace(placeholder[0], String(sample)));
      }
    });
  }
});

// The signer-from-hex factory against signer_cases: its signer's output for each digest, or the
// error for a digest that is not 32 bytes.
describe('shared signer cases', () => {
  for (const c of vectors.signer_cases as { name: string; private_key: string; digest: string; signature?: string; public_key?: string; error?: string }[]) {
    it(c.name, async () => {
      const sign = newSignerFromHex(c.private_key);
      const digest = Buffer.from(c.digest, 'hex');
      if (c.error !== undefined) {
        await assert.rejects(sign(digest), { message: c.error });
        return;
      }
      const sig = await sign(digest);
      assert.equal(sig.signature.toString('hex'), c.signature);
      assert.equal(sig.publicKey.toString('hex'), c.public_key);
    });
  }
});

// The field path of a violation, as the server and validate() write it, against field_path_cases.
// The formatter takes the path as protovalidate-es gives it: a field item, then a list_sub or
// map_sub item for its subscript (a 64-bit map key is a bigint).
describe('shared field path cases', () => {
  type Element = { field_name: string; index?: number; bool_key?: boolean; int_key?: string; uint_key?: string; string_key?: string };
  for (const c of vectors.field_path_cases as { name: string; path: Element[]; expected: string }[]) {
    it(c.name, () => {
      const path: PathItem[] = [];
      for (const e of c.path) {
        path.push({ kind: 'field', name: e.field_name } as unknown as PathItem);
        if (e.index !== undefined) {
          path.push({ kind: 'list_sub', index: e.index });
        } else if (e.bool_key !== undefined) {
          path.push({ kind: 'map_sub', key: e.bool_key });
        } else if (e.int_key !== undefined) {
          path.push({ kind: 'map_sub', key: BigInt(e.int_key) });
        } else if (e.uint_key !== undefined) {
          path.push({ kind: 'map_sub', key: BigInt(e.uint_key) });
        } else if (e.string_key !== undefined) {
          path.push({ kind: 'map_sub', key: e.string_key });
        }
      }
      assert.equal(fieldPathString(path), c.expected);
    });
  }
});

// The body limit a server uses for the max body size it is given (max_body_size_cases); NaN too
// keeps the default, in Node only.
describe('shared max body size cases', () => {
  const networkPublicKey = '0x' + vectors.keys.public_key;
  for (const c of [...vectors.max_body_size_cases, { name: 'nan', input: NaN, limit: DEFAULT_MAX_BODY_SIZE }] as { name: string; input: number; limit: number }[]) {
    it(c.name, () => {
      assert.equal(createService(networkPublicKey, () => {}, { maxBodySize: c.input }).readMaxBytes, c.limit);
    });
  }
});
