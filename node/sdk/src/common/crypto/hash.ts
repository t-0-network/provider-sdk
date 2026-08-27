import { keccak_256 } from '@noble/hashes/sha3.js'

export function keccak256(...inputs: Uint8Array[]): Buffer {
  const h = keccak_256.create();
  for (const input of inputs) {
    h.update(input);
  }
  return Buffer.from(h.digest());
}

export function computeDigest(body: Uint8Array, timestampMs: number): Buffer {
  const tsBuf = Buffer.alloc(8);
  tsBuf.writeBigUInt64LE(BigInt(timestampMs));
  return keccak256(body, tsBuf);
}
