import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import type { DescMessage, MessageShape, Registry } from "@bufbuild/protobuf";
import { SDK_VERSION } from "../version.js";

/**
 * Minimal logger contract accepted by the SDK. Providers may pass `console`
 * directly, or adapt their preferred logger (e.g. pino) with:
 *
 *   { error: (msg, fields) => pinoInstance.error(fields, msg) }
 */
export interface Logger {
  error(msg: string, fields?: Record<string, unknown>): void;
}

export interface ValidationInterceptorOptions {
  logger?: Logger;
  registry?: Registry;
}

const defaultLogger: Logger = {
  error: (msg, fields) =>
    // eslint-disable-next-line no-console
    console.error(JSON.stringify({ msg, ...(fields ?? {}) })),
};

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
      }));
      const details = violations.map((v) => `${v.field}: ${v.message}`).join("; ");
      logger.error("response validation failed", {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        violations,
        sdk_version: SDK_VERSION,
      });
      throw new ConnectError(`response validation failed: ${details}`, Code.Internal);
    }
    if (result.kind === "error") {
      logger.error("response validation error", {
        rpc_method: `${req.service.typeName}/${req.method.name}`,
        response_type: schema.typeName,
        error: result.error.message,
        sdk_version: SDK_VERSION,
      });
      throw new ConnectError(`response validation error: ${result.error.message}`, Code.Internal);
    }

    return resp;
  };
}
