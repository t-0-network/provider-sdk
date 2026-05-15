import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import type { DescMessage, MessageShape } from "@bufbuild/protobuf";
import { SDK_VERSION } from "../version.js";

const validator = createValidator();

/**
 * Minimal logger contract accepted by the SDK. Providers may pass `console`
 * directly, or adapt their preferred logger (e.g. pino) with:
 *
 *   { error: (msg, fields) => pinoInstance.error(fields, msg) }
 */
export interface Logger {
  error(msg: string, fields?: Record<string, unknown>): void;
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
export function createValidationInterceptor(logger: Logger = defaultLogger): Interceptor {
  const requestInterceptor = createValidateInterceptor();

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
 * @deprecated Use createValidationInterceptor instead.
 */
export const createResponseValidation = createValidationInterceptor;
