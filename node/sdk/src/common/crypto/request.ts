import { verifySignature } from './verify.js';
import { computeDigest } from './hash.js';
import { parseNetworkPublicKey, parsePublicKeyPoint, publicKeysEqual } from './keys.js';

export const DEFAULT_TOLERANCE_MS = 60_000;

const INT64_MAX = 9223372036854775807n;

// The one parser of the X-Signature-Timestamp header, in milliseconds: ASCII digits only (leading
// zeros allowed) of a value that fits a signed 64-bit integer, else undefined. A bigint, so the
// value is exact. Not exported from the package.
export function parseTimestamp(value: string): bigint | undefined {
  if (!/^[0-9]+$/.test(value)) {
    return undefined;
  }
  const ts = BigInt(value);
  return ts <= INT64_MAX ? ts : undefined;
}

export interface CreateVerifierOptions {
  networkPublicKey: string | Buffer;
  /**
   * How far the signature timestamp may be from now, in milliseconds: a finite number greater
   * than 0 and at most 60000 (the default). Anything else throws when the verifier is created.
   */
  toleranceMs?: number;
}

export interface VerifyRequest {
  body: Uint8Array | ArrayBufferView | ArrayBufferLike;
  signatureHeader: string;
  publicKeyHeader: string;
  timestampHeader: string;
}

export type VerifyRequestFailure =
  | 'invalid_timestamp'
  | 'timestamp_out_of_range'
  // Returned only for a missing (empty) X-Public-Key header; any other value that is not the
  // network key is unknown_public_key.
  | 'invalid_public_key'
  | 'unknown_public_key'
  | 'invalid_signature_format'
  | 'signature_failed';

export type VerifyRequestResult =
  | { valid: true }
  | { valid: false; reason: VerifyRequestFailure };

export type RequestVerifier = (req: VerifyRequest) => VerifyRequestResult;

export function createRequestVerifier(opts: CreateVerifierOptions): RequestVerifier {
  const networkKey = parseNetworkPublicKey(opts.networkPublicKey);
  const tolerance = opts.toleranceMs ?? DEFAULT_TOLERANCE_MS;
  if (!Number.isFinite(tolerance) || tolerance <= 0 || tolerance > DEFAULT_TOLERANCE_MS) {
    throw new Error(`toleranceMs must be a finite number greater than 0 and at most ${DEFAULT_TOLERANCE_MS}`);
  }

  return (req: VerifyRequest): VerifyRequestResult => {
    const parsed = parseTimestamp(req.timestampHeader);
    if (parsed === undefined) {
      return { valid: false, reason: 'invalid_timestamp' };
    }

    // Exact for any timestamp inside the window, which is all the digest below sees.
    const ts = Number(parsed);
    if (Math.abs(Date.now() - ts) > tolerance) {
      return { valid: false, reason: 'timestamp_out_of_range' };
    }

    // A missing header is invalid_public_key. Anything else that is not a key is not the network key
    // either, so it is unknown_public_key (unauthenticated, as in every SDK). The compressed form of
    // the network key is the network key.
    if (req.publicKeyHeader === '') {
      return { valid: false, reason: 'invalid_public_key' };
    }
    let publicKey: Buffer;
    try {
      publicKey = parsePublicKeyPoint(req.publicKeyHeader);
    } catch {
      return { valid: false, reason: 'unknown_public_key' };
    }

    if (!publicKeysEqual(publicKey, networkKey)) {
      return { valid: false, reason: 'unknown_public_key' };
    }

    const sigHex = req.signatureHeader.startsWith('0x')
      ? req.signatureHeader.slice(2)
      : req.signatureHeader;
    if (!/^[0-9a-fA-F]*$/.test(sigHex) || sigHex.length % 2 !== 0) {
      return { valid: false, reason: 'invalid_signature_format' };
    }
    const signature = Buffer.from(sigHex, 'hex');

    if (signature.length !== 64 && signature.length !== 65) {
      return { valid: false, reason: 'invalid_signature_format' };
    }

    const body = req.body instanceof Uint8Array
      ? req.body
      : ArrayBuffer.isView(req.body)
        ? new Uint8Array(req.body.buffer, req.body.byteOffset, req.body.byteLength)
        : new Uint8Array(req.body as ArrayBufferLike);

    const digest = computeDigest(body, ts);

    if (!verifySignature(publicKey, digest, signature)) {
      return { valid: false, reason: 'signature_failed' };
    }

    return { valid: true };
  };
}

export interface RejectedRequest {
  status: number;
  headers: Record<string, string>;
  body: string;
}

export function rejectRequest(reason: VerifyRequestFailure): RejectedRequest {
  let status: number;
  let code: string;
  let message: string;

  switch (reason) {
    case 'invalid_timestamp':
      status = 400;
      code = 'invalid_argument';
      message = 'Invalid signature timestamp';
      break;
    case 'timestamp_out_of_range':
      status = 400;
      code = 'invalid_argument';
      message = 'Signature timestamp out of range';
      break;
    case 'invalid_public_key':
      status = 400;
      code = 'invalid_argument';
      message = 'Invalid public key format';
      break;
    case 'unknown_public_key':
      status = 401;
      code = 'unauthenticated';
      message = 'Unknown public key';
      break;
    case 'invalid_signature_format':
      status = 400;
      code = 'invalid_argument';
      message = 'Invalid signature format';
      break;
    case 'signature_failed':
      status = 401;
      code = 'unauthenticated';
      message = 'Signature verification failed';
      break;
    default: {
      const _exhaustive: never = reason;
      void _exhaustive;
      status = 401;
      code = 'unauthenticated';
      message = 'Request verification failed';
      break;
    }
  }

  return {
    status,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, message }),
  };
}
