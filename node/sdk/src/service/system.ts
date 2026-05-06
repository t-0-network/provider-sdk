import { create } from "@bufbuild/protobuf";
import { timestampNow } from "@bufbuild/protobuf/wkt";
import {
  HealthRequest,
  HealthResponse,
  HealthResponseSchema,
  SdkEcosystem,
} from "../common/gen/tzero/v1/system/system_pb.js";
import { SDK_VERSION } from "../version.js";

export const createSystemServiceImpl = (services: string[]) => ({
  health(_req: HealthRequest): HealthResponse {
    return create(HealthResponseSchema, {
      services,
      currentTime: timestampNow(),
      sdkVersion: SDK_VERSION,
      sdkEcosystem: SdkEcosystem.NODE,
    });
  },
});
