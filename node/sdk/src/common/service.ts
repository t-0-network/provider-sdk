import {
  Code,
  ConnectError,
  ConnectRouter,
  createContextKey,
  createContextValues,
  StreamRequest,
  UnaryRequest
} from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import NetworkHeaders from "./headers.js";
import {keccak_256} from "@noble/hashes/sha3.js";
import { verifySignature } from './crypto/verify.js';
import { parseNetworkPublicKey, parsePublicKeyPoint } from './crypto/keys.js';
import { parseTimestamp } from './crypto/request.js';
import type {DescService, Registry} from "@bufbuild/protobuf";
import type {ServiceImpl} from "@connectrpc/connect";
import {createValidationInterceptor, type Logger} from "./validation.js";
import {Health} from "./health_pb.js";
import {createHealthServiceImpl} from "./health.js";

export interface CreateServiceOptions {
  /**
   * Logger used by the SDK for error-level events (currently:
   * response-validation failures from the interceptor safety net). The same
   * logger will be used for any future server-wide SDK log sites.
   *
   * If omitted, the SDK logs to `console.error` with a JSON-encoded payload.
   */
  logger?: Logger;

  /**
   * Protobuf registry for resolving custom protovalidate predefined-rule
   * extensions. Pass a registry built with `createRegistry(fileDescriptor)`
   * when your protos define custom predefined rules.
   */
  registry?: Registry;

  /**
   * SDK version stamped on health-check responses via the `T0-Sdk-Version`
   * header. Omit to suppress the header.
   */
  version?: string;

  /**
   * Maximum request body size in bytes. Default: 10 MiB.
   * Requests exceeding this limit are rejected before signature verification.
   */
  maxBodySize?: number;
}

export const REQUEST_VALIDITY_MILLIS = 60_000;
export const DEFAULT_MAX_BODY_SIZE = 10 * 1024 * 1024; // 10 MiB

// The request body as signatureValidation hashes it on arrival: the hash of the whole body, and for
// the gRPC framing fallback the hash of the body after its first 5 bytes, those 5 bytes and the length.
export class BodyHashes {
  readonly body = keccak_256.create();
  readonly payload = keccak_256.create();
  readonly prefix = Buffer.alloc(5);
  length = 0;

  update(chunk: Buffer) {
    this.body.update(chunk);
    const inPrefix = Math.min(Math.max(this.prefix.length - this.length, 0), chunk.length);
    if (inPrefix > 0) {
      chunk.copy(this.prefix, this.length, 0, inPrefix);
    }
    this.payload.update(chunk.subarray(inPrefix));
    this.length += chunk.length;
  }

  // One uncompressed gRPC frame: flag 0 and a length that is the rest of the body.
  isOneUncompressedFrame(): boolean {
    return this.length >= 5 && this.prefix[0] === 0 && this.prefix.readUInt32BE(1) === this.length - 5;
  }
}

const createSignatureVerification: (networkPublicKey: Buffer) => Interceptor = (networkPublicKey: Buffer) => (next) => async (req) => {
  // Streaming interceptors run before the body is read; only unary calls can verify its hash.
  if (req.stream) {
    throw new ConnectError("streaming calls are not supported", Code.Unimplemented);
  }

  const ts = decodeTimestamp(getHeader(req, NetworkHeaders.SignatureTimestamp));
  // Number(ts) is exact for any timestamp inside the window.
  if (Math.abs(Date.now() - Number(ts)) > REQUEST_VALIDITY_MILLIS) {
    throw new ConnectError(`${NetworkHeaders.SignatureTimestamp} must be within ${REQUEST_VALIDITY_MILLIS} milliseconds from now` , Code.InvalidArgument);
  }

  // Both are 65-byte uncompressed encodings, so the compressed form of the network key matches.
  const publicKey = parsePublicKeyHeader(getHeader(req, NetworkHeaders.PublicKey))
  if (networkPublicKey.compare(publicKey) !== 0 ) {
    throw new ConnectError(`${NetworkHeaders.PublicKey} value is not network public key`, Code.Unauthenticated);
  }

  const signature = decodeHex(getHeader(req, NetworkHeaders.Signature))

  const body = req.contextValues.get(kBodyHashes)!;

  const tsBuf = Buffer.alloc(8);
  tsBuf.writeBigUInt64LE(ts); // 64‑bit little‑endian timestamp

  // The whole body; failing that, for gRPC, a body of one uncompressed frame without its 5-byte
  // prefix, as the Java SDK signs it (above the gRPC framer). Go's signatureVerifier.verify.
  const verified = verifySignature(publicKey, body.body.update(tsBuf).digest(), signature)
    || ((req.header.get("Content-Type") ?? "").startsWith("application/grpc")
      && body.isOneUncompressedFrame()
      && verifySignature(publicKey, body.payload.update(tsBuf).digest(), signature));
  if (!verified) {
    throw new ConnectError(`${NetworkHeaders.Signature} has invalid signature` , Code.Unauthenticated);
  }
  return await next(req);
};

export interface Router {
  service: <T extends DescService, I extends ServiceImpl<T>>(
    service: T,
    implementation: I,
  ) => void;
}

export const createService = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions) => {
  networkPublicKey = parseNetworkPublicKey(networkPublicKey)

  return {
    routes: (router: ConnectRouter)=> {
      const collected: string[] = [];
      const origService = router.service.bind(router);
      const wrappedRouter: Router = {
        service: <T extends DescService>(desc: T, impl: Partial<ServiceImpl<T>>) => {
          collected.push(desc.typeName);
          origService(desc, impl);
        },
      };
      registerRoutes(wrappedRouter);
      collected.push(Health.typeName);
      origService(Health, createHealthServiceImpl(collected, options?.version));
    },
    interceptors: [createSignatureVerification(networkPublicKey), createValidationInterceptor({ logger: options?.logger, registry: options?.registry, version: options?.version })],
    readMaxBytes: options?.maxBodySize ?? DEFAULT_MAX_BODY_SIZE,
    grpcWeb: false,
    contextValues: (req: any) => {
      return createContextValues().set(kBodyHashes, (req as any).bodyHashes as BodyHashes)
    }
  }
}

const kBodyHashes = createContextKey<BodyHashes | undefined>(undefined);

function getHeader(req: UnaryRequest | StreamRequest, header: NetworkHeaders) {
  const raw = req.header.get(header);
  if (!raw) {
    throw new ConnectError(`missing required header '${header}'`, Code.InvalidArgument);
  }
  return raw;
}

// A value that is not a key is not the network key either: Unauthenticated.
function parsePublicKeyHeader(value: string) {
  try {
    return parsePublicKeyPoint(value);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new ConnectError(`${NetworkHeaders.PublicKey} value is not a public key: ${msg}`, Code.Unauthenticated);
  }
}

function decodeHex(value: string) {
  value = value.startsWith('0x') ? value.slice(2) : value;
  try {
    return Buffer.from(value, "hex");
  } catch (e) {
    throw new ConnectError(`invalid header format. '${value}' must be hex encoded`, Code.InvalidArgument);
  }
}

function decodeTimestamp(value: string) {
  const ts = parseTimestamp(value);
  if (ts === undefined) {
    throw new ConnectError(`invalid header format. '${value}' must be a number`, Code.InvalidArgument);
  }
  return ts;
}
