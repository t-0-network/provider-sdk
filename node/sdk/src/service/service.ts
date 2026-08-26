import { createService as createServiceCommon, type CreateServiceOptions } from "../common/service.js";
import { SDK_VERSION } from "../version.js";

export type { CreateServiceOptions } from "../common/service.js";
export { REQUEST_VALIDITY_MILLIS } from "../common/service.js";

interface Router {
  service: <T extends import("@bufbuild/protobuf").DescService, I extends import("@connectrpc/connect").ServiceImpl<T>>(
    service: T,
    implementation: I,
  ) => void;
}

export const createService = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
) => createServiceCommon(networkPublicKey, registerRoutes, { ...options, version: options?.version ?? SDK_VERSION });
