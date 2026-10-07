import { Code, ConnectError, createContextKey, createContextValues } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createAsyncIterable } from "@connectrpc/connect/protocol";
import type { UniversalHandler, UniversalServerRequest, UniversalServerResponse } from "@connectrpc/connect/protocol";
import { codeFromString, codeToHttpStatus, endStreamFlag, endStreamToJson, errorToJsonBytes } from "@connectrpc/connect/protocol-connect";
import { setTrailerStatus } from "@connectrpc/connect/protocol-grpc";
import { keccak_256 } from "@noble/hashes/sha3.js";
import NetworkHeaders from "./headers.js";
import { verifySignature } from "./crypto/verify.js";
import { checkSignatureHeaders, rejectionCode } from "./crypto/request.js";
import { BODY_TOO_LARGE, NO_SIGNATURE_RESULT, SIGNATURE_VERIFICATION_FAILED, STREAMING_NOT_SUPPORTED } from "./messages.js";

const kVerified = createContextKey<boolean>(false);

interface SignedHeaders {
  signature: Buffer;
  timestamp: Buffer;
}

// Everything the signature headers alone decide (checkSignatureHeaders). Throws the rejection.
function checkHeaders(header: Headers, networkPublicKey: Buffer): SignedHeaders {
  const checked = checkSignatureHeaders(
    header.get(NetworkHeaders.PublicKey),
    header.get(NetworkHeaders.Signature),
    header.get(NetworkHeaders.SignatureTimestamp),
    networkPublicKey,
  );
  if (!checked.ok) {
    throw new ConnectError(checked.message, codeFromString(rejectionCode(checked.reason)));
  }
  const timestampBytes = Buffer.alloc(8);
  timestampBytes.writeBigUInt64LE(checked.timestamp);
  return { signature: checked.signature, timestamp: timestampBytes };
}

// The whole body, at most limit bytes: a declared Content-Length over the limit is refused before
// anything is read, and an undeclared one as soon as the bytes read pass the limit.
async function readBody(request: UniversalServerRequest, limit: number): Promise<Buffer> {
  const declared = request.header.get("Content-Length");
  if (declared !== null && /^[0-9]+$/.test(declared) && Number(declared) > limit) {
    throw new ConnectError(BODY_TOO_LARGE(limit), Code.ResourceExhausted);
  }
  const body = request.body;
  // A body a framework parsed already is not the bytes the signature covers.
  if (body === null || typeof body !== "object" || !(Symbol.asyncIterator in body)) {
    throw new ConnectError(NO_SIGNATURE_RESULT, Code.Internal);
  }
  const chunks: Uint8Array[] = [];
  let length = 0;
  for await (const chunk of body as AsyncIterable<Uint8Array>) {
    length += chunk.length;
    if (length > limit) {
      throw new ConnectError(BODY_TOO_LARGE(limit), Code.ResourceExhausted);
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks, length);
}

const isGrpc = (contentType: string) => /^application\/grpc(?:[+;]|$)/i.test(contentType);

// The signature covers the whole body; failing that, for gRPC, a body of one uncompressed frame
// without its 5-byte prefix, as the Java SDK signs it (above the gRPC framer). Rule V6.
function verifyBody(body: Buffer, contentType: string, signed: SignedHeaders, networkPublicKey: Buffer) {
  const digest = (part: Uint8Array) => keccak_256(Buffer.concat([part, signed.timestamp]));
  if (verifySignature(networkPublicKey, digest(body), signed.signature)) {
    return;
  }
  const oneUncompressedFrame = body.length >= 5 && body[0] === 0 && body.readUInt32BE(1) === body.length - 5;
  if (isGrpc(contentType) && oneUncompressedFrame && verifySignature(networkPublicKey, digest(body.subarray(5)), signed.signature)) {
    return;
  }
  throw new ConnectError(SIGNATURE_VERIFICATION_FAILED, Code.Unauthenticated);
}

// The rejection as the caller's protocol carries an error: gRPC trailers, a Connect end-stream
// message, or a Connect unary JSON error.
function errorResponse(contentType: string, error: ConnectError): UniversalServerResponse {
  const mediaType = contentType.split(";")[0].trim();
  if (isGrpc(contentType)) {
    return {
      status: 200,
      header: new Headers({ "Content-Type": mediaType }),
      body: createAsyncIterable([]),
      trailer: setTrailerStatus(new Headers(), error),
    };
  }
  if (/^application\/connect\+/i.test(contentType)) {
    const json = new TextEncoder().encode(JSON.stringify(endStreamToJson(new Headers(), error, undefined)));
    const envelope = new Uint8Array(5 + json.length);
    envelope[0] = endStreamFlag;
    new DataView(envelope.buffer).setUint32(1, json.length);
    envelope.set(json, 5);
    return { status: 200, header: new Headers({ "Content-Type": mediaType }), body: createAsyncIterable([envelope]) };
  }
  return {
    status: codeToHttpStatus(error.code),
    header: new Headers({ "Content-Type": "application/json" }),
    body: createAsyncIterable([errorToJsonBytes(error, undefined)]),
  };
}

/**
 * Wraps a handler of the router so that it verifies the signature of every request before the RPC
 * library reads or decodes anything, and answers a rejected request itself. Streaming calls are
 * refused: only unary calls are verified here (rule V9: the Go SDK alone verifies streams).
 */
export function verifiedHandler(handler: UniversalHandler, networkPublicKey: Buffer, maxBodySize: number): UniversalHandler {
  const verified = async (request: UniversalServerRequest): Promise<UniversalServerResponse> => {
    const contentType = request.header.get("Content-Type") ?? "";
    let body: Buffer;
    try {
      if (handler.method.methodKind !== "unary") {
        throw new ConnectError(STREAMING_NOT_SUPPORTED, Code.Unimplemented);
      }
      const signed = checkHeaders(request.header, networkPublicKey);
      body = await readBody(request, maxBodySize);
      verifyBody(body, contentType, signed, networkPublicKey);
    } catch (e) {
      if (e instanceof ConnectError) {
        return errorResponse(contentType, e);
      }
      throw e;
    }
    const contextValues = (request.contextValues ?? createContextValues()).set(kVerified, true);
    return handler({ ...request, body: createAsyncIterable([body]), contextValues });
  };
  return Object.assign(verified, {
    protocolNames: handler.protocolNames,
    service: handler.service,
    method: handler.method,
    requestPath: handler.requestPath,
    allowedMethods: handler.allowedMethods,
    supportedContentType: handler.supportedContentType,
  });
}

/**
 * Fails closed: a call that did not pass through verifiedHandler is refused before its
 * implementation runs.
 */
export const requireVerified: Interceptor = (next) => async (request) => {
  if (!request.contextValues.get(kVerified)) {
    throw new ConnectError(NO_SIGNATURE_RESULT, Code.Internal);
  }
  return next(request);
};
