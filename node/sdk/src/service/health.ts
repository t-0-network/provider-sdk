import { create } from "@bufbuild/protobuf";
import { Code, ConnectError, type ServiceImpl } from "@connectrpc/connect";
import {
  Health,
  HealthCheckResponse_ServingStatus,
  HealthCheckResponseSchema,
  type HealthCheckRequest,
} from "@buf/grpc_grpc.bufbuild_es/grpc/health/v1/health_pb.js";
import { SDK_VERSION } from "../version.js";

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
): Partial<ServiceImpl<typeof Health>> => {
  const registered = new Set(services);
  const serving = create(HealthCheckResponseSchema, {
    status: HealthCheckResponse_ServingStatus.SERVING,
  });

  return {
    check(req, ctx) {
      ctx.responseHeader.set(SDK_ECOSYSTEM_HEADER, SDK_ECOSYSTEM);
      ctx.responseHeader.set(SDK_VERSION_HEADER, SDK_VERSION);

      // The schema comes from a published .d.ts, whose type brands do not
      // survive ServiceImpl's generics — connect-es widens the request to
      // Message<string>. The descriptor is still the real one at runtime, so
      // this narrows back to what the wire actually carries.
      const { service } = req as HealthCheckRequest;

      // An empty service name asks about the process as a whole, which is up if
      // this handler is running at all.
      if (service !== "" && !registered.has(service)) {
        throw new ConnectError(`unknown service '${service}'`, Code.NotFound);
      }
      return serving;
    },
  };
};
