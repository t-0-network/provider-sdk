import type { DescMessage, MessageShape, Registry } from '@bufbuild/protobuf';
import { fromJsonString, fromBinary, toJsonString, toBinary } from '@bufbuild/protobuf';
import { createValidator } from '@bufbuild/protovalidate';
import { createRequestVerifier, rejectRequest } from './request.js';
import type { CreateVerifierOptions, RejectedRequest } from './request.js';
import NetworkHeaders from '../headers.js';

export interface CreateDecoderOptions extends CreateVerifierOptions {
  registry?: Registry;
}

export type IncomingHeaders =
  | { get(name: string): string | null }
  | Record<string, string | string[] | undefined>;

export interface IncomingRequest {
  body: Uint8Array | ArrayBufferView | ArrayBufferLike;
  headers: IncomingHeaders;
}

export type WireFormat = 'json' | 'proto';

export interface Violation {
  field: string;
  message: string;
}

export type DecodeError =
  | 'unsupported_content_type'
  | 'malformed_body'
  | 'invalid_request'
  | 'validation_error';

export interface WireResponse {
  status: number;
  headers: Record<string, string>;
  body: string | Uint8Array<ArrayBuffer>;
}

export type DecodeRequestFailure =
  | RejectedRequest
  | { status: number; headers: Record<string, string>; body: string; error: DecodeError; violations?: Violation[] };

export type DecodeRequestResult<Desc extends DescMessage> =
  | { ok: true; request: MessageShape<Desc>; format: WireFormat; encodeResponse: <R extends DescMessage>(schema: R, message: MessageShape<R>) => WireResponse }
  | { ok: false; error: DecodeRequestFailure };

export type RequestDecoder = <Desc extends DescMessage>(
  schema: Desc,
  req: IncomingRequest,
) => DecodeRequestResult<Desc>;

function getHeader(headers: IncomingHeaders, name: string): string {
  if (typeof (headers as { get?: unknown }).get === 'function') {
    return (headers as { get(n: string): string | null }).get(name) ?? '';
  }
  const rec = headers as Record<string, string | string[] | undefined>;
  const lc = name.toLowerCase();
  for (const key of Object.keys(rec)) {
    if (key.toLowerCase() === lc) {
      const v = rec[key];
      if (Array.isArray(v)) return v[0] ?? '';
      return v ?? '';
    }
  }
  return '';
}

function normalizeBody(body: Uint8Array | ArrayBufferView | ArrayBufferLike): Uint8Array {
  if (body instanceof Uint8Array) return body;
  if (ArrayBuffer.isView(body)) return new Uint8Array(body.buffer, body.byteOffset, body.byteLength);
  return new Uint8Array(body as ArrayBufferLike);
}

function detectFormat(contentType: string): WireFormat | null {
  const base = contentType.split(';')[0].trim().toLowerCase();
  if (base === 'application/json') return 'json';
  if (base === 'application/proto' || base === 'application/protobuf' || base === 'application/x-protobuf') return 'proto';
  return null;
}

function failResponse(status: number, code: string, message: string, error: DecodeError, violations?: Violation[]): DecodeRequestFailure {
  return {
    status,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(violations ? { code, message, violations } : { code, message }),
    error,
    violations,
  };
}

export function createRequestDecoder(opts: CreateDecoderOptions): RequestDecoder {
  const verify = createRequestVerifier(opts);
  const validator = createValidator(opts.registry ? { registry: opts.registry } : undefined);
  const textDecoder = new TextDecoder('utf-8', { fatal: true });

  return <Desc extends DescMessage>(schema: Desc, req: IncomingRequest): DecodeRequestResult<Desc> => {
    const body = normalizeBody(req.body);

    const sigResult = verify({
      body,
      signatureHeader: getHeader(req.headers, NetworkHeaders.Signature),
      publicKeyHeader: getHeader(req.headers, NetworkHeaders.PublicKey),
      timestampHeader: getHeader(req.headers, NetworkHeaders.SignatureTimestamp),
    });

    if (!sigResult.valid) {
      return { ok: false, error: rejectRequest(sigResult.reason) };
    }

    const format = detectFormat(getHeader(req.headers, 'content-type'));
    if (!format) {
      return { ok: false, error: failResponse(415, 'unsupported_content_type', 'Unsupported Content-Type', 'unsupported_content_type') };
    }

    let message: MessageShape<Desc>;
    try {
      if (format === 'json') {
        message = fromJsonString(schema, textDecoder.decode(body), { ignoreUnknownFields: true, registry: opts.registry }) as MessageShape<Desc>;
      } else {
        message = fromBinary(schema, body) as MessageShape<Desc>;
      }
    } catch {
      return { ok: false, error: failResponse(400, 'invalid_argument', 'Malformed request body', 'malformed_body') };
    }

    const valResult = validator.validate(schema, message);
    if (valResult.kind === 'invalid') {
      const violations: Violation[] = valResult.violations.map(v => ({
        field: v.field?.toString() ?? '',
        message: v.message,
      }));
      return {
        ok: false,
        error: failResponse(400, 'invalid_argument', 'Request validation failed', 'invalid_request', violations),
      };
    }
    if (valResult.kind === 'error') {
      return { ok: false, error: failResponse(500, 'internal', `Validation error: ${valResult.error.message}`, 'validation_error') };
    }

    const encodeResponse = <R extends DescMessage>(respSchema: R, resp: MessageShape<R>): WireResponse => {
      const respVal = validator.validate(respSchema, resp);
      if (respVal.kind === 'invalid' || respVal.kind === 'error') {
        return {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code: 'internal', message: 'Response validation failed' }),
        };
      }
      if (format === 'json') {
        return {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
          body: toJsonString(respSchema, resp, { registry: opts.registry }),
        };
      }
      return {
        status: 200,
        headers: { 'Content-Type': 'application/proto' },
        body: toBinary(respSchema, resp),
      };
    };

    return { ok: true, request: message, format, encodeResponse };
  };
}
