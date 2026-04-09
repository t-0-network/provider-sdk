import { Code, ConnectError } from "@connectrpc/connect";
import type { Interceptor } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import { createValidateInterceptor } from "@connectrpc/validate";
import type { DescMessage, MessageShape } from "@bufbuild/protobuf";

const validator = createValidator();

/**
 * Creates a ConnectRPC interceptor that validates provider responses against
 * buf.validate proto annotations before they are serialized and sent.
 * Also validates incoming requests using the official @connectrpc/validate interceptor.
 *
 * Invalid requests return Code.InvalidArgument; invalid responses return Code.Internal.
 */
export function createValidationInterceptor(): Interceptor {
  const requestInterceptor = createValidateInterceptor();

  return (next) => async (req) => {
    // Validate request (delegates to official interceptor which throws on invalid)
    const resp = await requestInterceptor(next)(req);

    // Validate response
    const schema = req.method.output as DescMessage;
    const msg = resp.message as MessageShape<DescMessage>;
    const result = validator.validate(schema, msg);
    if (result.kind === "invalid") {
      const details = result.violations.map(v => `${v.field?.toString() ?? ""}: ${v.message}`).join("; ");
      throw new ConnectError(`response validation failed: ${details}`, Code.Internal);
    }
    if (result.kind === "error") {
      throw new ConnectError(`response validation error: ${result.error.message}`, Code.Internal);
    }

    return resp;
  };
}

/**
 * @deprecated Use createValidationInterceptor instead.
 */
export const createResponseValidation = createValidationInterceptor;
