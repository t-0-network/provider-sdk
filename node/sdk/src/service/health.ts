import { createHealthServiceImpl as createHealthServiceImplCommon } from "../common/health.js";
import { SDK_VERSION } from "../version.js";
import type { ServiceImpl } from "@connectrpc/connect";
import type { Health } from "../common/health_pb.js";

export { SDK_ECOSYSTEM_HEADER, SDK_VERSION_HEADER } from "../common/health.js";

export const createHealthServiceImpl = (
  services: string[],
  version?: string,
): Partial<ServiceImpl<typeof Health>> =>
  createHealthServiceImplCommon(services, version ?? SDK_VERSION);
