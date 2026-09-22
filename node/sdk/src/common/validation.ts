import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import type { DescMessage, MessageShape, Registry } from "@bufbuild/protobuf";
import type { Logger } from "./logger.js";
import { defaultLogger } from "./logger.js";

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
  const validator = createValidator(opts.registry ? { registry: opts.registry } : undefined);
  const requestInterceptor = createValidateInterceptor({ validator });

  return (next) => async (req) => {
    const resp = await requestInterceptor(next)(req);

    const schema = req.method.output as DescMessage;
    const msg = resp.message as MessageShape<DescMessage>;
    const result = validator.validate(schema, msg);
    if (result.kind === "invalid") {
      const violations = result.violations.map((v) => ({
        field: v.field?.toString() ?? "",
        message: v.message,
        ruleId: v.ruleId,
      }));
      const details = violations.map((v) => `${v.field}: ${v.message}`).join("; ");
      const fields: Record<string, unknown> = {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        violations,
      };
      if (opts.version) fields.sdk_version = opts.version;
      logger.error("response validation failed", fields);
      throw new ConnectError(`response validation failed: ${details}`, Code.Internal);
    }
    if (result.kind === "error") {
      const fields: Record<string, unknown> = {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        error: result.error.message,
      };
      if (opts.version) fields.sdk_version = opts.version;
      logger.error("response validation error", fields);
      throw new ConnectError(`response validation error: ${result.error.message}`, Code.Internal);
    }

    return resp;
  };
}
