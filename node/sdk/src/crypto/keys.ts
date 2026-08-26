export function parsePublicKey(key: string | Buffer): Buffer {
  if (typeof key === 'string') {
    key = Buffer.from(key.startsWith('0x') ? key.slice(2) : key, 'hex');
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
