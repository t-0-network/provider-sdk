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
