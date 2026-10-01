import { secp256k1 } from '@noble/curves/secp256k1.js';

export function parsePrivateKey(privateKey: string | Buffer): Buffer {
  if (privateKey === undefined || privateKey === null || privateKey.length === 0) {
    throw new Error('private key must not be null or empty');
  }
  if (typeof privateKey === 'string') {
    const hex = privateKey.startsWith('0x') || privateKey.startsWith('0X') ? privateKey.slice(2) : privateKey;
    // Checked before decoding: Buffer.from(hex, 'hex'), and Uint8Array.fromHex on Node 26 for 32 characters
    // or more, read a character above U+00FF by its low byte ("٦" as "f"), so a malformed key would
    // become a different, valid one.
    if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
      throw new Error('private key must be 32 bytes (64 hex characters)');
    }
    privateKey = Buffer.from(hex, 'hex');
  } else if (privateKey.length !== 32) {
    throw new Error('private key must be 32 bytes');
  }
  // A number in [1, n-1], n being the order of secp256k1.
  if (!secp256k1.utils.isValidSecretKey(privateKey)) {
    throw new Error('private key must be in range [1, n-1]');
  }
  return privateKey;
}

export function uncompressedPublicKeyFromPrivateKey(privateKey: Buffer): Buffer {
  return Buffer.from(secp256k1.getPublicKey(privateKey, false));
}

export function publicKeyFromPrivateKey(hex: string): string {
  return `0x${uncompressedPublicKeyFromPrivateKey(parsePrivateKey(hex)).toString('hex')}`;
}

export function parsePublicKey(key: string | Buffer): Buffer {
  if (typeof key === 'string') {
    const hex = key.startsWith('0x') ? key.slice(2) : key;
    if (!/^[0-9a-fA-F]*$/.test(hex) || hex.length % 2 !== 0) {
      throw new Error('Public key contains invalid hex characters');
    }
    key = Buffer.from(hex, 'hex');
  }
  if (key.length !== 65 || key[0] !== 0x04) {
    throw new Error('Public key must be 65 bytes in uncompressed format (0x04 prefix)');
  }
  return Buffer.from(key);
}

export function publicKeysEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  return Buffer.from(a).compare(Buffer.from(b)) === 0;
}
