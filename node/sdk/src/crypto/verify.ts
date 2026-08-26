import { secp256k1 } from '@noble/curves/secp256k1.js'

export function verifySignature(publicKey: Uint8Array, digest: Uint8Array, signature: Uint8Array): boolean {
  if (digest.length !== 32) {
    return false;
  }
  if (signature.length !== 64 && signature.length !== 65) {
    return false;
  }

  const sig64 = signature.length === 65 ? signature.subarray(0, 64) : signature;

  try {
    return secp256k1.verify(sig64, digest, publicKey, { prehash: false });
  } catch {
    return false;
  }
}
