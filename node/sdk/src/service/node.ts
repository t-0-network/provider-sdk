import { createHandler as createHandlerCommon } from "../common/node.js";
import type { Router, CreateServiceOptions } from "../common/service.js";
import { SDK_VERSION } from "../version.js";

export { signatureValidation } from "../common/node.js";
export type { NodeHandlerFn } from "../common/node.js";

export const createHandler = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): ReturnType<typeof createHandlerCommon> =>
  createHandlerCommon(networkPublicKey, registerRoutes, { ...options, version: options?.version ?? SDK_VERSION });
