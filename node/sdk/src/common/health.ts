import { create } from "@bufbuild/protobuf";
import { Code, ConnectError, type ServiceImpl } from "@connectrpc/connect";
import {
  Health,
  HealthCheckResponse_ServingStatus,
  HealthCheckResponseSchema,
} from "./health_pb.js";

/**
 * Headers carrying the identity of the SDK answering the probe. They ride on
 * the health response and nowhere else: `HealthCheckResponse` has a single
 * status field and `check` names its service in the request, so the contract
 * itself has no room for this — and taxing every callback with headers only the
 * probe reads would be worse.
 */
export const SDK_ECOSYSTEM_HEADER = "T0-Sdk-Ecosystem";
export const SDK_VERSION_HEADER = "T0-Sdk-Version";

const SDK_ECOSYSTEM = "node";

/**
 * Reports SERVING for the services registered on this server and NOT_FOUND for
 * anything else. The set is frozen at registration; nothing is computed per
 * request.
 *
 * `watch` is absent on purpose: it is server-streaming, and the body-hash
 * signature scheme these servers run behind has no story for streams. Omitting
 * it from a partial `ServiceImpl` makes connect-es answer UNIMPLEMENTED.
 */
export const createHealthServiceImpl = (
  services: string[],
  version?: string,
): Partial<ServiceImpl<typeof Health>> => {
  const registered = new Set(services);
  const serving = create(HealthCheckResponseSchema, {
    status: HealthCheckResponse_ServingStatus.SERVING,
  });

  return {
    check(req, ctx) {
      ctx.responseHeader.set(SDK_ECOSYSTEM_HEADER, SDK_ECOSYSTEM);
      if (version) {
        ctx.responseHeader.set(SDK_VERSION_HEADER, version);
      }

      const { service } = req;

      if (service !== "" && !registered.has(service)) {
        throw new ConnectError(`unknown service '${service}'`, Code.NotFound);
      }
      return serving;
    },
  };
};
