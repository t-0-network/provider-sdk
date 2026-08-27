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
import {Hash} from "@noble/hashes/utils.js";
import { verifySignature } from './crypto/verify.js';
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
}

export const REQUEST_VALIDITY_MILLIS = 60_000;

const createSignatureVerification: (networkPublicKey: Buffer) => Interceptor = (networkPublicKey: Buffer) => (next) => async (req) => {
  const ts = decodeNum(getHeader(req, NetworkHeaders.SignatureTimestamp));
  if (Date.now() - ts > 60_000) {
    throw new ConnectError(`${NetworkHeaders.SignatureTimestamp} must be within ${REQUEST_VALIDITY_MILLIS} milliseconds from now` , Code.InvalidArgument);
  }

  const publicKey = decodeHex(getHeader(req, NetworkHeaders.PublicKey))
  if (networkPublicKey.compare(publicKey) !== 0 ) {
    throw new ConnectError(`${NetworkHeaders.PublicKey} value is not network public key`, Code.Unauthenticated);
  }

  const signature = decodeHex(getHeader(req, NetworkHeaders.Signature))

  const hasher = req.contextValues.get(kHash)!;

  const tsBuf = Buffer.alloc(8);
  tsBuf.writeBigUInt64LE(BigInt(ts)); // 64‑bit little‑endian timestamp

  const digest = hasher
    .update(tsBuf)
    .digest();

  if (!verifySignature(publicKey, digest, signature)) {
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
  if (typeof networkPublicKey == "string") {
    networkPublicKey = decodeHex(networkPublicKey)
  }

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
    interceptors: [createSignatureVerification(networkPublicKey), createValidationInterceptor({ logger: options?.logger, registry: options?.registry })],
    grpcWeb: false,
    contextValues: (req: any) => {
      return createContextValues().set(kHash, (req as any).hasher as Hash<Hash<any>>)
    }
  }
}

const kHash = createContextKey<Hash<Hash<any>>| undefined>(undefined);

function getHeader(req: UnaryRequest | StreamRequest, header: NetworkHeaders) {
  const raw = req.header.get(header);
  if (!raw) {
    throw new ConnectError(`missing required header '${header}'`, Code.InvalidArgument);
  }
  return raw;
}

function decodeHex(value: string) {
  value = value.startsWith('0x') ? value.slice(2) : value;
  try {
    return Buffer.from(value, "hex");
  } catch (e) {
    throw new ConnectError(`invalid header format. '${value}' must be hex encoded`, Code.InvalidArgument);
  }
}

function decodeNum(value: string) {
  try {
    return parseInt(value);
  } catch (e) {
    throw new ConnectError(`invalid header format. '${value}' must be a number`, Code.InvalidArgument);
  }
}
