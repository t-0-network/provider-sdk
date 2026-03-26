import { describe, it, assert } from 'node:test';
import * as nodeAssert from 'node:assert/strict';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { CreateSigner } from '../src/client/signer.js';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const vectors = JSON.parse(readFileSync(resolve(__dirname, '../../../cross_test/test_vectors.json'), 'utf-8'));

describe('Keccak-256 hashing', () => {
  for (const vec of vectors.keccak256) {
    it(`hashes "${vec.input}" correctly`, () => {
      const hash = Buffer.from(keccak_256(Buffer.from(vec.input, 'utf-8'))).toString('hex');
      nodeAssert.equal(hash, vec.hash);
    });
  }
});

describe('CreateSigner', () => {
  it('derives the correct public key from private key', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const hash = Buffer.from(keccak_256(Buffer.from('please sign me!', 'utf-8')));
    const sig = await signer(hash);
    nodeAssert.equal(sig.publicKey.toString('hex'), vectors.keys.public_key);
  });

  it('accepts 0x-prefixed private key', async () => {
    const signer = CreateSigner('0x' + vectors.keys.private_key);
    const hash = Buffer.from(keccak_256(Buffer.from('test', 'utf-8')));
    const sig = await signer(hash);
    nodeAssert.equal(sig.publicKey.toString('hex'), vectors.keys.public_key);
  });

  it('produces a 64-byte compact signature', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const hash = Buffer.from(keccak_256(Buffer.from('test', 'utf-8')));
    const sig = await signer(hash);
    nodeAssert.equal(sig.signature.length, 64);
  });

  it('rejects non-32-byte input', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    await nodeAssert.rejects(
      () => signer(Buffer.from('short', 'utf-8')),
      { message: 'Message hash must be 32 bytes' }
    );
  });

  it('rejects invalid private key format', () => {
    nodeAssert.throws(() => CreateSigner('not-a-valid-key'), { message: /Private key must be 64 hex characters/ });
  });
});

describe('Request signing', () => {
  it('computes correct hash for body + timestamp', () => {
    const { body, timestamp_ms, expected_hash } = vectors.request_signing;

    const tsBuf = Buffer.alloc(8);
    tsBuf.writeBigUInt64LE(BigInt(timestamp_ms));

    const hash = keccak_256.create()
      .update(Buffer.from(body, 'utf-8'))
      .update(tsBuf);
    const result = Buffer.from(hash.digest()).toString('hex');

    nodeAssert.equal(result, expected_hash);
  });

  it('produces signature matching cross-language test vector', async () => {
    const { body, timestamp_ms, expected_hash, expected_signature } = vectors.request_signing;

    const signer = CreateSigner(vectors.keys.private_key);

    const tsBuf = Buffer.alloc(8);
    tsBuf.writeBigUInt64LE(BigInt(timestamp_ms));

    const digest = Buffer.from(
      keccak_256.create()
        .update(Buffer.from(body, 'utf-8'))
        .update(tsBuf)
        .digest()
    );
    nodeAssert.equal(digest.toString('hex'), expected_hash);

    const sig = await signer(digest);
    nodeAssert.equal(sig.signature.subarray(0, 64).toString('hex'), expected_signature);
  });
});
