import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import { createRegistry, type DescMessage, type MessageShape, type Registry } from "@bufbuild/protobuf";
import { SDK_VERSION } from "../version.js";
import { file_tzero_v1_payment_provider } from "../common/gen/tzero/v1/payment/provider_pb.js";
import { file_tzero_v1_payment_network } from "../common/gen/tzero/v1/payment/network_pb.js";

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
 * Creates a ConnectRPC interceptor that validates provider responses against
 * buf.validate proto annotations before they are serialized and sent.
 * Also validates incoming requests using the official @connectrpc/validate interceptor.
 *
 * Invalid requests return Code.InvalidArgument; invalid responses return Code.Internal.
 *
 * On invalid responses, a single structured `error`-level line is emitted to
 * the supplied {@link Logger} (default: `console.error` with JSON-encoded
 * fields) before the `Code.Internal` error is thrown. This is the safety net
 * for handler code paths that skipped the public `validate()` helper.
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
    // Validate request (delegates to official interceptor which throws on invalid)
    const resp = await requestInterceptor(next)(req);

    // Validate response
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

/**
 * Registry covering the t-0 network provider contract protos. Leaf file
 * descriptors pull in their full dependency graph, so two entries are enough.
 *
 * Downstream SDKs with custom predefined-rule extensions should build their
 * own registry that includes their extension file descriptors.
 */
export const networkRegistry: Registry = createRegistry(
  file_tzero_v1_payment_provider,
  file_tzero_v1_payment_network,
);

/**
 * Pre-configured validation interceptor for the t-0 network provider contract.
 * Uses {@link networkRegistry} so all standard constraints resolve out of the box.
 */
export function createNetworkValidationInterceptor(logger?: Logger): Interceptor {
  return createValidationInterceptor({ logger, registry: networkRegistry });
}

/**
 * @deprecated Use createValidationInterceptor instead.
 */
export const createResponseValidation = createValidationInterceptor;
