import { secp256k1 } from '@noble/curves/secp256k1.js';
import {
  NETWORK_PUBLIC_KEY_INVALID,
  NETWORK_PUBLIC_KEY_NOT_SET,
  PRIVATE_KEY_BYTES_LENGTH,
  PRIVATE_KEY_EMPTY,
  PRIVATE_KEY_MALFORMED,
  PRIVATE_KEY_OUT_OF_RANGE,
  PUBLIC_KEY_NOT_A_POINT,
  PUBLIC_KEY_NOT_HEX,
} from '../messages.js';

export function parsePrivateKey(privateKey: string | Buffer): Buffer {
  // Key bytes of any length but 32, none included, are PRIVATE_KEY_BYTES_LENGTH below, as in Java
  // Signer.fromBytes and C# Signer.FromBytes.
  if (privateKey === undefined || privateKey === null || privateKey === '') {
    throw new Error(PRIVATE_KEY_EMPTY);
  }
  if (typeof privateKey === 'string') {
    const hex = privateKey.startsWith('0x') || privateKey.startsWith('0X') ? privateKey.slice(2) : privateKey;
    // Checked before decoding: Buffer.from(hex, 'hex'), and Uint8Array.fromHex on Node 26 for 32 characters
    // or more, read a character above U+00FF by its low byte ("٦" as "f"), so a malformed key would
    // become a different, valid one.
    if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
      throw new Error(PRIVATE_KEY_MALFORMED);
    }
    privateKey = Buffer.from(hex, 'hex');
  } else if (privateKey.length !== 32) {
    throw new Error(PRIVATE_KEY_BYTES_LENGTH);
  }
  // A number in [1, n-1], n being the order of secp256k1.
  if (!secp256k1.utils.isValidSecretKey(privateKey)) {
    throw new Error(PRIVATE_KEY_OUT_OF_RANGE);
  }
  return privateKey;
}

export function uncompressedPublicKeyFromPrivateKey(privateKey: Buffer): Buffer {
  return Buffer.from(secp256k1.getPublicKey(privateKey, false));
}

export function publicKeyFromPrivateKey(hex: string): string {
  return `0x${uncompressedPublicKeyFromPrivateKey(parsePrivateKey(hex)).toString('hex')}`;
}

/**
 * Parses a secp256k1 public key by the rule the SDK applies to the network key and the
 * X-Public-Key header, and returns its 65-byte uncompressed encoding.
 *
 * @deprecated Not used by the SDK; will be removed in a future release.
 */
export function parsePublicKey(key: string | Buffer): Buffer {
  return parsePublicKeyPoint(key);
}

// The one parser of the configured network key and the X-Public-Key header (the deprecated
// parsePublicKey delegates to it): hex with an optional 0x or 0X prefix and nothing else, of a
// compressed (33 bytes, 02 or 03) or uncompressed (65 bytes, 04) point on secp256k1. Returns the
// 65-byte uncompressed encoding, so the two forms of a key compare equal. Not exported from the package.
export function parsePublicKeyPoint(key: string | Uint8Array): Buffer {
  if (typeof key === 'string') {
    const hex = key.startsWith('0x') || key.startsWith('0X') ? key.slice(2) : key;
    // Checked before decoding: Buffer.from(hex, 'hex') stops at the first bad character.
    if (!/^(?:[0-9a-fA-F]{2})+$/.test(hex)) {
      throw new Error(PUBLIC_KEY_NOT_HEX);
    }
    key = Buffer.from(hex, 'hex');
  }
  try {
    return Buffer.from(secp256k1.Point.fromBytes(key).toBytes(false));
  } catch {
    throw new Error(PUBLIC_KEY_NOT_A_POINT);
  }
}

// The configured network key is checked once, at startup, so a missing or
// mistyped key fails there rather than on every request with "unknown public key".
export function parseNetworkPublicKey(key: string | Buffer): Buffer {
  if (typeof key === 'string') {
    key = key.trim();
  }
  if (key === undefined || key === null || key.length === 0) {
    throw new Error(NETWORK_PUBLIC_KEY_NOT_SET);
  }
  try {
    return parsePublicKeyPoint(key);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(NETWORK_PUBLIC_KEY_INVALID(msg));
  }
}

export function publicKeysEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  return Buffer.from(a).compare(Buffer.from(b)) === 0;
}
