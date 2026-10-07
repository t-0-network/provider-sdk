import { verifySignature } from './verify.js';
import { computeDigest } from './hash.js';
import { parseNetworkPublicKey, parsePublicKeyPoint } from './keys.js';
import NetworkHeaders from '../headers.js';
import {
  INVALID_HEADER_ENCODING,
  MISSING_HEADER,
  SIGNATURE_VERIFICATION_FAILED,
  TIMESTAMP_NOT_DECIMAL,
  TIMESTAMP_OUTSIDE_WINDOW,
  TIMESTAMP_OUT_OF_RANGE,
  UNKNOWN_PUBLIC_KEY,
} from '../messages.js';

import { TIMESTAMP_WINDOW_MS } from '../limits.js';

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

export type SignatureHeaders =
  | { ok: true; publicKey: Buffer; signature: Buffer; timestamp: bigint }
  | { ok: false; reason: VerifyRequestFailure; message: string };

// Everything the signature headers alone decide, in the order of every SDK (rule V10): the three
// are present and well-formed, the timestamp window, that the public key is the network key, and
// the signature length. An empty, null or undefined header is missing. The server and
// createRequestVerifier both check with it. Not exported from the package.
export function checkSignatureHeaders(
  publicKeyValue: string | null | undefined,
  signatureValue: string | null | undefined,
  timestampValue: string | null | undefined,
  networkPublicKey: Buffer,
): SignatureHeaders {
  if (!publicKeyValue) {
    return { ok: false, reason: 'invalid_public_key', message: MISSING_HEADER(NetworkHeaders.PublicKey) };
  }
  if (!signatureValue) {
    return { ok: false, reason: 'invalid_signature_format', message: MISSING_HEADER(NetworkHeaders.Signature) };
  }
  const signatureHex =
    signatureValue.startsWith('0x') || signatureValue.startsWith('0X') ? signatureValue.slice(2) : signatureValue;
  // Checked before decoding: Buffer.from(hex, 'hex') stops at the first bad character.
  if (!/^(?:[0-9a-fA-F]{2})+$/.test(signatureHex)) {
    return { ok: false, reason: 'invalid_signature_format', message: INVALID_HEADER_ENCODING(NetworkHeaders.Signature) };
  }
  if (!timestampValue) {
    return { ok: false, reason: 'invalid_timestamp', message: MISSING_HEADER(NetworkHeaders.SignatureTimestamp) };
  }
  if (!/^[0-9]+$/.test(timestampValue)) {
    return { ok: false, reason: 'invalid_timestamp', message: TIMESTAMP_NOT_DECIMAL };
  }
  const timestamp = parseTimestamp(timestampValue);
  if (timestamp === undefined) {
    return { ok: false, reason: 'invalid_timestamp', message: TIMESTAMP_OUT_OF_RANGE };
  }
  // Number(timestamp) is exact for any timestamp inside the window.
  if (Math.abs(Date.now() - Number(timestamp)) > TIMESTAMP_WINDOW_MS) {
    return { ok: false, reason: 'timestamp_out_of_range', message: TIMESTAMP_OUTSIDE_WINDOW };
  }
  // A value that is not a key is not the network key either. Both are 65-byte uncompressed
  // encodings, so the compressed form of the network key matches.
  let publicKey: Buffer;
  try {
    publicKey = parsePublicKeyPoint(publicKeyValue);
  } catch {
    return { ok: false, reason: 'unknown_public_key', message: UNKNOWN_PUBLIC_KEY };
  }
  if (!publicKey.equals(networkPublicKey)) {
    return { ok: false, reason: 'unknown_public_key', message: UNKNOWN_PUBLIC_KEY };
  }
  const signature = Buffer.from(signatureHex, 'hex');
  if (signature.length !== 64 && signature.length !== 65) {
    return { ok: false, reason: 'signature_failed', message: SIGNATURE_VERIFICATION_FAILED };
  }
  return { ok: true, publicKey, signature, timestamp };
}

export interface CreateVerifierOptions {
  networkPublicKey: string | Buffer;
}

export interface VerifyRequest {
  body: Uint8Array | ArrayBufferView | ArrayBufferLike;
  signatureHeader: string;
  publicKeyHeader: string;
  timestampHeader: string;
}

export type VerifyRequestFailure =
  // A missing X-Signature-Timestamp header, or one that is not a decimal number or out of range.
  | 'invalid_timestamp'
  // A timestamp outside the window.
  | 'timestamp_out_of_range'
  // Returned only for a missing (empty) X-Public-Key header; any other value that is not the
  // network key is unknown_public_key.
  | 'invalid_public_key'
  | 'unknown_public_key'
  // A missing X-Signature header, or one that is not hex.
  | 'invalid_signature_format'
  // A signature that is not 64 or 65 bytes, or that does not verify.
  | 'signature_failed';

export type VerifyRequestResult =
  | { valid: true }
  // message is the rejection's text, the same in every SDK; pass it to rejectRequest.
  | { valid: false; reason: VerifyRequestFailure; message?: string };

export type RequestVerifier = (req: VerifyRequest) => VerifyRequestResult;

export function createRequestVerifier(opts: CreateVerifierOptions): RequestVerifier {
  const networkKey = parseNetworkPublicKey(opts.networkPublicKey);

  return (req: VerifyRequest): VerifyRequestResult => {
    const headers = checkSignatureHeaders(req.publicKeyHeader, req.signatureHeader, req.timestampHeader, networkKey);
    if (!headers.ok) {
      return { valid: false, reason: headers.reason, message: headers.message };
    }

    const body = req.body instanceof Uint8Array
      ? req.body
      : ArrayBuffer.isView(req.body)
        ? new Uint8Array(req.body.buffer, req.body.byteOffset, req.body.byteLength)
        : new Uint8Array(req.body as ArrayBufferLike);

    const digest = computeDigest(body, Number(headers.timestamp));

    if (!verifySignature(headers.publicKey, digest, headers.signature)) {
      return { valid: false, reason: 'signature_failed', message: SIGNATURE_VERIFICATION_FAILED };
    }

    return { valid: true };
  };
}

export interface RejectedRequest {
  status: number;
  headers: Record<string, string>;
  body: string;
}

// The code of each rejection, and its text when rejectRequest is given none.
const REJECTIONS: Record<VerifyRequestFailure, { code: 'invalid_argument' | 'unauthenticated'; message: string }> = {
  invalid_timestamp: { code: 'invalid_argument', message: TIMESTAMP_NOT_DECIMAL },
  timestamp_out_of_range: { code: 'invalid_argument', message: TIMESTAMP_OUTSIDE_WINDOW },
  invalid_public_key: { code: 'invalid_argument', message: MISSING_HEADER(NetworkHeaders.PublicKey) },
  unknown_public_key: { code: 'unauthenticated', message: UNKNOWN_PUBLIC_KEY },
  invalid_signature_format: { code: 'invalid_argument', message: INVALID_HEADER_ENCODING(NetworkHeaders.Signature) },
  signature_failed: { code: 'unauthenticated', message: SIGNATURE_VERIFICATION_FAILED },
};

// The code of a rejection, as Connect names it. Not exported from the package.
export function rejectionCode(reason: VerifyRequestFailure): 'invalid_argument' | 'unauthenticated' {
  return REJECTIONS[reason]?.code ?? 'unauthenticated';
}

/**
 * The Connect error response for a request createRequestVerifier refused. Pass the result's
 * `message`, so the text is the one every SDK sends for that check.
 */
export function rejectRequest(reason: VerifyRequestFailure, message?: string): RejectedRequest {
  const code = rejectionCode(reason);
  return {
    status: code === 'invalid_argument' ? 400 : 401,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, message: message ?? REJECTIONS[reason]?.message ?? SIGNATURE_VERIFICATION_FAILED }),
  };
}
