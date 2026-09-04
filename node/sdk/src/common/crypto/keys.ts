import { secp256k1 } from '@noble/curves/secp256k1.js';

export function parsePrivateKey(privateKey: string | Buffer): Buffer {
  if (typeof privateKey === 'string') {
    privateKey = privateKey.replace(/^0x/, '');
    if (!/^[0-9a-fA-F]{64}$/.test(privateKey)) {
      throw new Error('Private key must be 64 hex characters');
    }

    privateKey = Buffer.from(privateKey, 'hex');
  }

  if (!secp256k1.utils.isValidSecretKey(privateKey)) {
    throw new Error('Invalid private key');
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
