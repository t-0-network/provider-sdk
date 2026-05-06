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
import NetworkHeaders from "../common/headers.js";
import { secp256k1 } from '@noble/curves/secp256k1.js'
import {Hash} from "@noble/hashes/utils.js";
import type {DescService, } from "@bufbuild/protobuf";
import type {ServiceImpl} from "@connectrpc/connect";
import {createValidationInterceptor} from "./validate_response.js";
import {SystemService} from "../common/gen/tzero/v1/system/system_pb.js";
import {createSystemServiceImpl} from "./system.js";

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

  let signature = decodeHex(getHeader(req, NetworkHeaders.Signature))
  if (signature.length === 65) {
    signature = signature.subarray(0, 64);
  }

  const hasher = req.contextValues.get(kHash)!;

  const tsBuf = Buffer.alloc(8);
  tsBuf.writeBigUInt64LE(BigInt(ts)); // 64‑bit little‑endian timestamp

  const hash = hasher
    .update(tsBuf)
    .digest();
  let signatureValid = false;
  try {
    signatureValid = secp256k1.verify(signature, hash, publicKey, {prehash: false});
  } catch (e) {
    throw new ConnectError(`${NetworkHeaders.Signature} has invalid signature or public key format: ${e}` , Code.Unauthenticated);
  }

  if (!signatureValid) {
    throw new ConnectError(`${NetworkHeaders.Signature} has invalid signature` , Code.Unauthenticated);
  }
  return await next(req);
};

interface Router {
  service: <T extends DescService, I extends ServiceImpl<T>>(
    service: T,
    implementation: I,
  ) => void;
}

export const createService = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void) => {
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
      collected.push(SystemService.typeName);
      origService(SystemService, createSystemServiceImpl(collected));
    },
    interceptors: [createSignatureVerification(networkPublicKey), createValidationInterceptor()],
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