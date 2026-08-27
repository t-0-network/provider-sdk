import { createService as createServiceCommon, type CreateServiceOptions, type Router } from "../common/service.js";
import { SDK_VERSION } from "../version.js";

export type { CreateServiceOptions, Router } from "../common/service.js";
export { REQUEST_VALIDITY_MILLIS } from "../common/service.js";

export const createService = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): ReturnType<typeof createServiceCommon> =>
  createServiceCommon(networkPublicKey, registerRoutes, { ...options, version: options?.version ?? SDK_VERSION });
