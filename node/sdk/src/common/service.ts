import { ConnectRouter, createContextValues, type ContextValues } from "@connectrpc/connect";
import { parseNetworkPublicKey } from './crypto/keys.js';
import { requireVerified, verifiedHandler } from "./signature.js";
import { DEFAULT_MAX_BODY_SIZE } from "./limits.js";
import type {DescService, Registry} from "@bufbuild/protobuf";
import type {ServiceImpl} from "@connectrpc/connect";
import {createValidationInterceptor, type Logger} from "./validation.js";
import {Health} from "./health_pb.js";
import {createHealthServiceImpl} from "./health.js";
import {SERVICE_NULL} from "./messages.js";

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
   * SDK version reported in the `T0-Sdk-Version` header of health-check
   * replies and in the response-validation log. A missing or blank value
   * reports the SDK's own version.
   */
  version?: string;

  /**
   * Maximum request body size in bytes. Default: 10 MiB; 0 or less (or NaN) keeps the default.
   * Requests exceeding this limit are rejected before signature verification.
   */
  maxBodySize?: number;
}

export { DEFAULT_MAX_BODY_SIZE, TIMESTAMP_WINDOW_MS } from "./limits.js";

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
  const networkKey = parseNetworkPublicKey(networkPublicKey);
  // 0 or less keeps the default, as in every SDK; NaN too.
  const maxBodySize = (options?.maxBodySize ?? 0) > 0 ? options!.maxBodySize! : DEFAULT_MAX_BODY_SIZE;

  return {
    routes: (router: ConnectRouter)=> {
      const collected: string[] = [];
      const origService = router.service.bind(router);
      const wrappedRouter: Router = {
        service: <T extends DescService>(desc: T, impl: Partial<ServiceImpl<T>>) => {
          if (desc == null || impl == null) {
            throw new TypeError(SERVICE_NULL);
          }
          collected.push(desc.typeName);
          origService(desc, impl);
        },
      };
      registerRoutes(wrappedRouter);
      collected.push(Health.typeName);
      origService(Health, createHealthServiceImpl(collected, options?.version));
      // Every handler verifies the signature of a request before the RPC library reads it.
      for (let i = 0; i < router.handlers.length; i++) {
        router.handlers[i] = verifiedHandler(router.handlers[i], networkKey, maxBodySize);
      }
    },
    interceptors: [requireVerified, createValidationInterceptor({ logger: options?.logger, registry: options?.registry, version: options?.version })],
    readMaxBytes: maxBodySize,
    grpcWeb: false,
    /**
     * @deprecated Not used by the SDK; will be removed in a future release. Each request gets new,
     * empty context values, as without it.
     */
    contextValues: (_req: any): ContextValues => createContextValues(),
  }
}
