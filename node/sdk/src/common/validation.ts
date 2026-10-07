import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import type { DescMessage, MessageShape, Registry } from "@bufbuild/protobuf";
import { pathToString } from "@bufbuild/protobuf/reflect";
import type { Logger } from "./logger.js";
import { defaultLogger } from "./logger.js";
import { reportedVersion } from "../version.js";
import { RESPONSE_INVALID, RESPONSE_VALIDATION_ERROR } from "./messages.js";

export type { Logger } from "./logger.js";

export interface ValidationInterceptorOptions {
  logger?: Logger;
  registry?: Registry;
  version?: string;
}

/**
 * Creates a ConnectRPC interceptor that validates requests and responses
 * against buf.validate proto annotations.
 *
 * Invalid requests return Code.InvalidArgument; invalid responses return
 * Code.Internal.
 *
 * Pass a `registry` when your protos define custom predefined rules
 * (proto2 `extend buf.validate.*Rules`).
 */
export function createValidationInterceptor(loggerOrOptions?: Logger | ValidationInterceptorOptions): Interceptor {
  const opts: ValidationInterceptorOptions =
    loggerOrOptions && typeof (loggerOrOptions as Logger).error === "function"
      ? { logger: loggerOrOptions as Logger }
      : (loggerOrOptions as ValidationInterceptorOptions | undefined) ?? {};
  const logger = opts.logger ?? defaultLogger;
  const version = reportedVersion(opts.version);
  const validator = createValidator(opts.registry ? { registry: opts.registry } : undefined);
  const requestInterceptor = createValidateInterceptor({ validator });

  return (next) => async (req) => {
    const resp = await requestInterceptor(next)(req);

    const schema = req.method.output as DescMessage;
    const msg = resp.message as MessageShape<DescMessage>;
    const result = validator.validate(schema, msg);
    if (result.kind === "invalid") {
      const violations = result.violations.map((v) => ({
        field: pathToString(v.field),
        message: v.message,
        ruleId: v.ruleId,
      }));
      const details = violations.map((v) => `${v.field}: ${v.message}`).join("; ");
      const fields: Record<string, unknown> = {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        violations,
      };
      fields.sdk_version = version;
      logger.error("response validation failed", fields);
      throw new ConnectError(RESPONSE_INVALID(details), Code.Internal);
    }
    if (result.kind === "error") {
      const fields: Record<string, unknown> = {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        error: result.error.message,
      };
      fields.sdk_version = version;
      logger.error("response validation error", fields);
      throw new ConnectError(RESPONSE_VALIDATION_ERROR(result.error.message), Code.Internal);
    }

    return resp;
  };
}
