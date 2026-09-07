import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';
import { test } from 'node:test';

const privateKey = '0000000000000000000000000000000000000000000000000000000000000001';
const publicKey = '0x0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8';

test('root and crypto package exports work from ESM and CommonJS builds', async () => {
  const esmRoot = await import('@t-0/provider-sdk');
  const esmCrypto = await import('@t-0/provider-sdk/crypto');
  const require = createRequire(import.meta.url);
  const cjsRoot = require('@t-0/provider-sdk');
  const cjsCrypto = require('@t-0/provider-sdk/crypto');

  for (const entrypoint of [esmRoot, esmCrypto, cjsRoot, cjsCrypto]) {
    assert.equal(typeof entrypoint.publicKeyFromPrivateKey, 'function');
    assert.equal(entrypoint.publicKeyFromPrivateKey(privateKey), publicKey);
  }
});
