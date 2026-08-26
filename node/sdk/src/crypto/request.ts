import { verifySignature } from './verify.js';
import { computeDigest } from './hash.js';
import { parsePublicKey, publicKeysEqual } from './keys.js';

export const DEFAULT_TOLERANCE_MS = 60_000;

export interface CreateVerifierOptions {
  networkPublicKey: string | Buffer;
  toleranceMs?: number;
}

export interface VerifyRequest {
  body: Uint8Array;
  signatureHeader: string;
  publicKeyHeader: string;
  timestampHeader: string;
}

export type VerifyRequestFailure =
  | 'invalid_timestamp'
  | 'timestamp_out_of_range'
  | 'invalid_public_key'
  | 'unknown_public_key'
  | 'invalid_signature_format'
  | 'signature_failed';

export type VerifyRequestResult =
  | { valid: true }
  | { valid: false; reason: VerifyRequestFailure };

export type RequestVerifier = (req: VerifyRequest) => VerifyRequestResult;

export function createRequestVerifier(opts: CreateVerifierOptions): RequestVerifier {
  const networkKey = parsePublicKey(opts.networkPublicKey);
  const tolerance = opts.toleranceMs ?? DEFAULT_TOLERANCE_MS;

  return (req: VerifyRequest): VerifyRequestResult => {
    const ts = parseInt(req.timestampHeader, 10);
    if (!Number.isFinite(ts) || ts < 0) {
      return { valid: false, reason: 'invalid_timestamp' };
    }

    if (Math.abs(Date.now() - ts) > tolerance) {
      return { valid: false, reason: 'timestamp_out_of_range' };
    }

    let publicKey: Buffer;
    try {
      publicKey = parsePublicKey(req.publicKeyHeader);
    } catch {
      return { valid: false, reason: 'invalid_public_key' };
    }

    if (!publicKeysEqual(publicKey, networkKey)) {
      return { valid: false, reason: 'unknown_public_key' };
    }

    const sigHex = req.signatureHeader.startsWith('0x')
      ? req.signatureHeader.slice(2)
      : req.signatureHeader;
    if (!/^[0-9a-fA-F]*$/.test(sigHex)) {
      return { valid: false, reason: 'invalid_signature_format' };
    }
    const signature = Buffer.from(sigHex, 'hex');

    if (signature.length !== 64 && signature.length !== 65) {
      return { valid: false, reason: 'invalid_signature_format' };
    }

    const body = req.body instanceof Buffer || ArrayBuffer.isView(req.body)
      ? req.body
      : new Uint8Array(req.body as ArrayBufferLike);

    const digest = computeDigest(body, ts);

    if (!verifySignature(publicKey, digest, signature)) {
      return { valid: false, reason: 'signature_failed' };
    }

    return { valid: true };
  };
}
