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

// Thrown by parsePublicKeyPoint for a value that is not hex, as opposed to hex that is not a key.
export class PublicKeyHexError extends Error {}

// The one parser of the configured network key and the X-Public-Key header (parsePublicKey keeps its
// own rules): hex with an optional 0x or 0X prefix and nothing else, of a compressed (33 bytes, 02 or
// 03) or uncompressed (65 bytes, 04) point on secp256k1. Returns the 65-byte uncompressed encoding,
// so the two forms of a key compare equal. Not exported from the package.
export function parsePublicKeyPoint(key: string | Uint8Array): Buffer {
  if (typeof key === 'string') {
    const hex = key.startsWith('0x') || key.startsWith('0X') ? key.slice(2) : key;
    // Checked before decoding: Buffer.from(hex, 'hex') stops at the first bad character.
    if (!/^(?:[0-9a-fA-F]{2})+$/.test(hex)) {
      throw new PublicKeyHexError('must be hex, with an optional 0x or 0X prefix');
    }
    key = Buffer.from(hex, 'hex');
  }
  // The hybrid encodings (06, 07) name a point too; noble rejects them, and so does this check.
  const compressed = key.length === 33 && (key[0] === 0x02 || key[0] === 0x03);
  const uncompressed = key.length === 65 && key[0] === 0x04;
  if (!compressed && !uncompressed) {
    throw new Error('must be 33 bytes with prefix 02 or 03, or 65 bytes with prefix 04');
  }
  try {
    return Buffer.from(secp256k1.Point.fromBytes(key).toBytes(false));
  } catch {
    throw new Error('not a point on secp256k1');
  }
}

// The configured network key is checked once, at startup, so a missing or
// mistyped key fails there rather than on every request with "unknown public key".
export function parseNetworkPublicKey(key: string | Buffer): Buffer {
  if (typeof key === 'string') {
    key = key.trim();
  }
  if (key === undefined || key === null || key.length === 0) {
    throw new Error('network public key is not set');
  }
  try {
    return parsePublicKeyPoint(key);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(`invalid network public key: ${msg}`);
  }
}

export function publicKeysEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  return Buffer.from(a).compare(Buffer.from(b)) === 0;
}
