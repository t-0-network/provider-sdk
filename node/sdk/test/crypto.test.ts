import { describe, it, assert } from 'node:test';
import * as nodeAssert from 'node:assert/strict';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { secp256k1 } from '@noble/curves/secp256k1.js';
import { CreateSigner } from '../src/client/signer.js';
import { verifySignature, keccak256, computeDigest, parsePublicKey, publicKeysEqual } from '../src/crypto/index.js';
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

// What the middleware hashes: the raw body with the little-endian millisecond timestamp
// appended. body_hex carries the bytes, so a body can be binary, framed or empty.
function requestDigest(bodyHex: string, timestampMs: number): Buffer {
  const tsBuf = Buffer.alloc(8);
  tsBuf.writeBigUInt64LE(BigInt(timestampMs));

  return Buffer.from(
    keccak_256.create()
      .update(Buffer.from(bodyHex, 'hex'))
      .update(tsBuf)
      .digest()
  );
}

describe('Request signing cases', () => {
  // Bodies the string-valued request_signing block cannot express: binary, gRPC-framed,
  // empty, and one whose signature has a leading zero byte — the case a signer that trims
  // instead of padding to 32 bytes fails, and only that case.
  for (const vec of vectors.request_signing_cases) {
    it(`${vec.name} hashes and signs to the vector bytes`, async () => {
      const digest = requestDigest(vec.body_hex, vec.timestamp_ms);
      nodeAssert.equal(digest.toString('hex'), vec.expected_hash);

      const signer = CreateSigner(vectors.keys.private_key);
      const sig = await signer(digest);
      nodeAssert.equal(sig.signature.subarray(0, 64).toString('hex'), vec.expected_signature);
    });
  }
});

describe('Signature verification cases', () => {
  for (const vec of vectors.signature_verification) {
    it(`${vec.name} verifies: ${vec.valid}`, () => {
      const digest = requestDigest(vec.body_hex, vec.timestamp_ms);
      const publicKey = Buffer.from(vec.public_key, 'hex');

      // service.ts truncates a 65-byte signature before verifying, and treats a throw from
      // noble the same as a false — both end as Unauthenticated. Mirror that here.
      const signature = Buffer.from(vec.signature, 'hex');
      const sig64 = signature.length === 65 ? signature.subarray(0, 64) : signature;

      let valid = false;
      try {
        valid = secp256k1.verify(sig64, digest, publicKey, { prehash: false });
      } catch {
        valid = false;
      }

      nodeAssert.equal(valid, vec.valid);
    });
  }
});

// ---- Public crypto module tests ----

describe('crypto/keccak256', () => {
  for (const vec of vectors.keccak256) {
    it(`hashes "${vec.input}" correctly`, () => {
      const hash = keccak256(Buffer.from(vec.input, 'utf-8')).toString('hex');
      nodeAssert.equal(hash, vec.hash);
    });
  }

  it('multi-input produces same result as single concatenated input', () => {
    const a = Buffer.from('hello ', 'utf-8');
    const b = Buffer.from('world', 'utf-8');
    const combined = Buffer.concat([a, b]);
    nodeAssert.equal(
      keccak256(a, b).toString('hex'),
      keccak256(combined).toString('hex'),
    );
  });

  it('empty input produces the Keccak-256 of nothing', () => {
    nodeAssert.equal(
      keccak256(Buffer.alloc(0)).toString('hex'),
      vectors.keccak256.find((v: any) => v.input === '').hash,
    );
  });

  it('no-arg call produces the Keccak-256 of nothing', () => {
    nodeAssert.equal(
      keccak256().toString('hex'),
      vectors.keccak256.find((v: any) => v.input === '').hash,
    );
  });

  it('always returns a 32-byte Buffer', () => {
    nodeAssert.equal(keccak256(Buffer.alloc(0)).length, 32);
    nodeAssert.equal(keccak256(Buffer.alloc(1000)).length, 32);
    nodeAssert.ok(Buffer.isBuffer(keccak256(Buffer.from('x'))));
  });

  it('matches the raw noble keccak_256 for all vector inputs', () => {
    for (const vec of vectors.keccak256) {
      const raw = Buffer.from(keccak_256(Buffer.from(vec.input, 'utf-8'))).toString('hex');
      const ours = keccak256(Buffer.from(vec.input, 'utf-8')).toString('hex');
      nodeAssert.equal(ours, raw);
    }
  });

  it('many small chunks match single-shot', () => {
    const full = Buffer.from('abcdefghijklmnopqrstuvwxyz0123456789', 'utf-8');
    const chunks = [];
    for (let i = 0; i < full.length; i++) {
      chunks.push(full.subarray(i, i + 1));
    }
    nodeAssert.equal(
      keccak256(...chunks).toString('hex'),
      keccak256(full).toString('hex'),
    );
  });
});

describe('crypto/computeDigest', () => {
  for (const vec of vectors.request_signing_cases) {
    it(`${vec.name} produces correct digest`, () => {
      const body = Buffer.from(vec.body_hex, 'hex');
      const digest = computeDigest(body, vec.timestamp_ms);
      nodeAssert.equal(digest.toString('hex'), vec.expected_hash);
    });
  }

  it('always returns a 32-byte Buffer', () => {
    const d = computeDigest(Buffer.from('test'), 1706000000000);
    nodeAssert.equal(d.length, 32);
    nodeAssert.ok(Buffer.isBuffer(d));
  });

  it('timestamp 0 produces a valid digest', () => {
    const d = computeDigest(Buffer.from('test'), 0);
    nodeAssert.equal(d.length, 32);
  });

  it('different timestamps produce different digests', () => {
    const body = Buffer.from('same body');
    const d1 = computeDigest(body, 1000);
    const d2 = computeDigest(body, 1001);
    nodeAssert.notEqual(d1.toString('hex'), d2.toString('hex'));
  });

  it('different bodies produce different digests', () => {
    const d1 = computeDigest(Buffer.from('body-a'), 1000);
    const d2 = computeDigest(Buffer.from('body-b'), 1000);
    nodeAssert.notEqual(d1.toString('hex'), d2.toString('hex'));
  });

  it('matches manual keccak256(body || LE_uint64(ts))', () => {
    const body = Buffer.from('manual check');
    const ts = 1706000000000;
    const tsBuf = Buffer.alloc(8);
    tsBuf.writeBigUInt64LE(BigInt(ts));
    nodeAssert.equal(
      computeDigest(body, ts).toString('hex'),
      keccak256(body, tsBuf).toString('hex'),
    );
  });
});

describe('crypto/verifySignature', () => {
  const pubKey = Buffer.from(vectors.keys.public_key, 'hex');

  for (const vec of vectors.signature_verification) {
    it(`${vec.name}: ${vec.valid}`, () => {
      const digest = computeDigest(
        Buffer.from(vec.body_hex, 'hex'),
        vec.timestamp_ms,
      );
      const key = Buffer.from(vec.public_key, 'hex');
      const sig = Buffer.from(vec.signature, 'hex');
      nodeAssert.equal(verifySignature(key, digest, sig), vec.valid);
    });
  }

  it('returns false for wrong digest length (16 bytes)', () => {
    const sig = Buffer.from(vectors.request_signing.expected_signature, 'hex');
    nodeAssert.equal(verifySignature(pubKey, Buffer.alloc(16), sig), false);
  });

  it('returns false for wrong digest length (0 bytes)', () => {
    const sig = Buffer.from(vectors.request_signing.expected_signature, 'hex');
    nodeAssert.equal(verifySignature(pubKey, Buffer.alloc(0), sig), false);
  });

  it('returns false for wrong digest length (64 bytes)', () => {
    const sig = Buffer.from(vectors.request_signing.expected_signature, 'hex');
    nodeAssert.equal(verifySignature(pubKey, Buffer.alloc(64), sig), false);
  });

  it('returns false for wrong signature length (32 bytes)', () => {
    const digest = computeDigest(Buffer.from('test', 'utf-8'), 1706000000000);
    nodeAssert.equal(verifySignature(pubKey, digest, Buffer.alloc(32)), false);
  });

  it('returns false for wrong signature length (0 bytes)', () => {
    const digest = computeDigest(Buffer.from('test', 'utf-8'), 1706000000000);
    nodeAssert.equal(verifySignature(pubKey, digest, Buffer.alloc(0)), false);
  });

  it('returns false for wrong signature length (63 bytes)', () => {
    const digest = computeDigest(Buffer.from('test', 'utf-8'), 1706000000000);
    nodeAssert.equal(verifySignature(pubKey, digest, Buffer.alloc(63)), false);
  });

  it('returns false for wrong signature length (66 bytes)', () => {
    const digest = computeDigest(Buffer.from('test', 'utf-8'), 1706000000000);
    nodeAssert.equal(verifySignature(pubKey, digest, Buffer.alloc(66)), false);
  });

  it('end-to-end: sign with CreateSigner then verify with verifySignature', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const body = Buffer.from('round-trip test body');
    const ts = 1706000099000;
    const digest = computeDigest(body, ts);
    const { signature, publicKey: signerPubKey } = await signer(digest);
    nodeAssert.equal(verifySignature(signerPubKey, digest, signature), true);
  });

  it('end-to-end: all request_signing_cases round-trip', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    for (const vec of vectors.request_signing_cases) {
      const digest = computeDigest(Buffer.from(vec.body_hex, 'hex'), vec.timestamp_ms);
      const { signature, publicKey: signerPubKey } = await signer(digest);
      nodeAssert.equal(
        verifySignature(signerPubKey, digest, signature),
        true,
        `round-trip failed for ${vec.name}`,
      );
    }
  });

  it('rejects signature from a different key pair', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const impostorKey = Buffer.from(vectors.impostor_keys.public_key, 'hex');
    const digest = computeDigest(Buffer.from('test'), 1706000000000);
    const { signature } = await signer(digest);
    nodeAssert.equal(verifySignature(impostorKey, digest, signature), false);
  });

  it('rejects when digest has a single bit flipped', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const digest = computeDigest(Buffer.from('bit flip test'), 1706000000000);
    const { signature, publicKey: signerPubKey } = await signer(digest);
    const tampered = Buffer.from(digest);
    tampered[0] ^= 0x01;
    nodeAssert.equal(verifySignature(signerPubKey, tampered, signature), false);
  });

  it('rejects when signature has a single bit flipped', async () => {
    const signer = CreateSigner(vectors.keys.private_key);
    const digest = computeDigest(Buffer.from('bit flip sig'), 1706000000000);
    const { signature, publicKey: signerPubKey } = await signer(digest);
    const tampered = Buffer.from(signature);
    tampered[31] ^= 0x01;
    nodeAssert.equal(verifySignature(signerPubKey, tampered, digest), false);
  });

  it('65-byte signature with v=0x00 verifies the same as 64-byte truncation', () => {
    const digest = computeDigest(
      Buffer.from(vectors.signature_verification[0].body_hex, 'hex'),
      vectors.signature_verification[0].timestamp_ms,
    );
    const sig64 = Buffer.from(vectors.signature_verification[0].signature, 'hex');
    const sig65 = Buffer.concat([sig64, Buffer.from([0x00])]);
    nodeAssert.equal(verifySignature(pubKey, digest, sig64), true);
    nodeAssert.equal(verifySignature(pubKey, digest, sig65), true);
  });

  it('all-0xff signature returns false without throwing', () => {
    const digest = computeDigest(Buffer.from('test'), 1706000000000);
    const sig = Buffer.alloc(64, 0xff);
    nodeAssert.equal(verifySignature(pubKey, digest, sig), false);
  });

  it('all-zero digest returns false', () => {
    const sig = Buffer.from(vectors.request_signing.expected_signature, 'hex');
    nodeAssert.equal(verifySignature(pubKey, Buffer.alloc(32, 0x00), sig), false);
  });
});

describe('crypto/parsePublicKey', () => {
  const hexKey = vectors.keys.public_key;

  it('parses hex string', () => {
    const key = parsePublicKey(hexKey);
    nodeAssert.equal(key.toString('hex'), hexKey);
  });

  it('parses 0x-prefixed hex string', () => {
    const key = parsePublicKey('0x' + hexKey);
    nodeAssert.equal(key.toString('hex'), hexKey);
  });

  it('parses Buffer', () => {
    const buf = Buffer.from(hexKey, 'hex');
    const key = parsePublicKey(buf);
    nodeAssert.equal(key.toString('hex'), hexKey);
  });

  it('returns a copy, not the original buffer', () => {
    const buf = Buffer.from(hexKey, 'hex');
    const key = parsePublicKey(buf);
    buf[1] ^= 0xff;
    nodeAssert.notEqual(key[1], buf[1]);
  });

  it('throws on compressed key (33 bytes, 0x02 prefix)', () => {
    nodeAssert.throws(() => parsePublicKey(Buffer.alloc(33, 0x02)), {
      message: /65 bytes/,
    });
  });

  it('throws on compressed key (33 bytes, 0x03 prefix)', () => {
    const buf = Buffer.alloc(33, 0x03);
    nodeAssert.throws(() => parsePublicKey(buf), {
      message: /65 bytes/,
    });
  });

  it('throws on wrong prefix (0x00)', () => {
    const bad = Buffer.alloc(65, 0x00);
    nodeAssert.throws(() => parsePublicKey(bad), {
      message: /0x04 prefix/,
    });
  });

  it('throws on wrong prefix (0x02)', () => {
    const bad = Buffer.alloc(65, 0x02);
    nodeAssert.throws(() => parsePublicKey(bad), {
      message: /0x04 prefix/,
    });
  });

  it('throws on empty buffer', () => {
    nodeAssert.throws(() => parsePublicKey(Buffer.alloc(0)), {
      message: /65 bytes/,
    });
  });

  it('throws on empty string', () => {
    nodeAssert.throws(() => parsePublicKey(''), {
      message: /65 bytes/,
    });
  });

  it('throws on 64-byte buffer (just shy of valid)', () => {
    const buf = Buffer.alloc(64, 0x04);
    nodeAssert.throws(() => parsePublicKey(buf), {
      message: /65 bytes/,
    });
  });

  it('parses the impostor key correctly', () => {
    const key = parsePublicKey(vectors.impostor_keys.public_key);
    nodeAssert.equal(key.toString('hex'), vectors.impostor_keys.public_key);
  });
});

describe('crypto/publicKeysEqual', () => {
  it('returns true for identical keys', () => {
    const key = Buffer.from(vectors.keys.public_key, 'hex');
    nodeAssert.equal(publicKeysEqual(key, Buffer.from(key)), true);
  });

  it('returns false for different keys', () => {
    const key1 = Buffer.from(vectors.keys.public_key, 'hex');
    const key2 = Buffer.from(vectors.impostor_keys.public_key, 'hex');
    nodeAssert.equal(publicKeysEqual(key1, key2), false);
  });

  it('returns false for different lengths', () => {
    const key = Buffer.from(vectors.keys.public_key, 'hex');
    nodeAssert.equal(publicKeysEqual(key, key.subarray(0, 32)), false);
  });

  it('returns true for Uint8Array vs Buffer with same bytes', () => {
    const key = Buffer.from(vectors.keys.public_key, 'hex');
    const u8 = new Uint8Array(key);
    nodeAssert.equal(publicKeysEqual(key, u8), true);
  });

  it('returns false for both empty', () => {
    nodeAssert.equal(publicKeysEqual(Buffer.alloc(0), Buffer.alloc(0)), true);
  });

  it('returns false when one byte differs', () => {
    const a = Buffer.from(vectors.keys.public_key, 'hex');
    const b = Buffer.from(a);
    b[64] ^= 0x01;
    nodeAssert.equal(publicKeysEqual(a, b), false);
  });
});

describe('crypto module cross-consistency', () => {
  it('computeDigest matches the raw noble streaming pattern used by the interceptor', () => {
    for (const vec of vectors.request_signing_cases) {
      const body = Buffer.from(vec.body_hex, 'hex');
      const tsBuf = Buffer.alloc(8);
      tsBuf.writeBigUInt64LE(BigInt(vec.timestamp_ms));
      const streamedDigest = Buffer.from(
        keccak_256.create().update(body).update(tsBuf).digest()
      );
      const moduleDigest = computeDigest(body, vec.timestamp_ms);
      nodeAssert.equal(
        moduleDigest.toString('hex'),
        streamedDigest.toString('hex'),
        `streaming vs computeDigest mismatch for ${vec.name}`,
      );
    }
  });

  it('verifySignature matches the raw noble secp256k1.verify call for all vectors', () => {
    for (const vec of vectors.signature_verification) {
      const digest = computeDigest(Buffer.from(vec.body_hex, 'hex'), vec.timestamp_ms);
      const key = Buffer.from(vec.public_key, 'hex');
      const sig = Buffer.from(vec.signature, 'hex');
      const sig64 = sig.length === 65 ? sig.subarray(0, 64) : sig;

      let rawResult = false;
      try {
        rawResult = secp256k1.verify(sig64, digest, key, { prehash: false });
      } catch {
        rawResult = false;
      }

      nodeAssert.equal(
        verifySignature(key, digest, sig),
        rawResult,
        `module vs raw noble mismatch for ${vec.name}`,
      );
    }
  });
});
