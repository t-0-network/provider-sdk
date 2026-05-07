import { describe, it, assert } from 'node:test';
import * as nodeAssert from 'node:assert/strict';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { secp256k1 } from '@noble/curves/secp256k1.js';
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

  // Mirrors the streaming pattern in src/service/node.ts where the
  // signatureValidation middleware feeds N request chunks into hasher.update().
  // Asserts incremental hashing across many chunk boundaries matches the
  // one-shot digest, guarding against a regression in noble-hashes' sha3
  // streaming state across releases (the 2.2.0 sha3 unrolling speedup is the
  // exact kind of change this catches).
  it('multi-chunk streaming digest matches one-shot for same bytes', () => {
    const full = Buffer.from('the quick brown fox jumps over the lazy dog 0123456789', 'utf-8');
    const oneShot = Buffer.from(keccak_256(full)).toString('hex');

    for (const splits of [
      [1, 2, 3, 4, 5],
      [10, 20, 30],
      [0, full.length],
      [1, 1, 1, 1, 1, 1, 1, 1],
    ]) {
      const hasher = keccak_256.create();
      let offset = 0;
      for (const len of splits) {
        hasher.update(full.subarray(offset, offset + len));
        offset += len;
      }
      hasher.update(full.subarray(offset));
      const streamed = Buffer.from(hasher.digest()).toString('hex');
      nodeAssert.equal(streamed, oneShot, `splits=${splits.join(',')}`);
    }
  });
});

describe('secp256k1 verification', () => {
  // service.ts line ~40 calls secp256k1.verify(signature, hash, publicKey, {prehash: false})
  // to authenticate every inbound request. Without a direct test, regressions in
  // noble-curves' verify path would only surface through the end-to-end Health
  // test in system.test.ts — and only on the rejection branch we exercise there.
  // These cases pin down the function's contract on the exact arguments the SDK
  // hands it.

  const publicKey = Buffer.from(vectors.keys.public_key, 'hex');
  const validHash = Buffer.from(vectors.request_signing.expected_hash, 'hex');
  const validSig = Buffer.from(vectors.request_signing.expected_signature, 'hex');

  it('accepts cross-language signature against matching public key + hash', () => {
    const ok = secp256k1.verify(validSig, validHash, publicKey, { prehash: false });
    nodeAssert.equal(ok, true);
  });

  it('rejects signature with one bit flipped', () => {
    const tampered = Buffer.from(validSig);
    tampered[63] ^= 0x01;
    const ok = secp256k1.verify(tampered, validHash, publicKey, { prehash: false });
    nodeAssert.equal(ok, false);
  });

  it('rejects when hash does not match what was signed', () => {
    const tamperedHash = Buffer.from(validHash);
    tamperedHash[0] ^= 0xff;
    const ok = secp256k1.verify(validSig, tamperedHash, publicKey, { prehash: false });
    nodeAssert.equal(ok, false);
  });

  it('rejects against a different public key', () => {
    // Derive a second uncompressed public key from a known good private key.
    const otherPriv = Buffer.alloc(32);
    otherPriv[31] = 0x01;
    const otherPub = Buffer.from(secp256k1.getPublicKey(otherPriv, false));
    nodeAssert.notEqual(otherPub.toString('hex'), publicKey.toString('hex'));

    const ok = secp256k1.verify(validSig, validHash, otherPub, { prehash: false });
    nodeAssert.equal(ok, false);
  });

  // SDK callers may submit a 65-byte (r||s||recoveryId) signature; service.ts
  // truncates to the first 64 bytes before calling verify(). Asserts noble's
  // 64-byte verify succeeds on bytes that were sliced out of a 65-byte buffer
  // — i.e. the slice path leaves the signature byte-identical.
  it('verifies signature sliced from a synthetic 65-byte buffer', () => {
    const sig65 = Buffer.alloc(65);
    validSig.copy(sig65, 0);
    sig65[64] = 0x01; // recovery id; will be discarded
    const truncated = sig65.subarray(0, 64);
    const ok = secp256k1.verify(truncated, validHash, publicKey, { prehash: false });
    nodeAssert.equal(ok, true);
  });
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
