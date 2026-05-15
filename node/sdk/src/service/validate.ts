import { Code, ConnectError } from "@connectrpc/connect";
import { createValidator } from "@bufbuild/protovalidate";
import type { DescMessage, MessageShape } from "@bufbuild/protobuf";

/**
 * Shared protovalidate validator instance for the public {@link validate} helper.
 * Construction is cheap, but reusing one instance avoids repeated compilation
 * of the same rules.
 */
export const validator = createValidator();

/**
 * Validates a response message against its buf.validate proto annotations.
 *
 * On success, returns the message unchanged (typed). On failure, throws a
 * {@link ConnectError} with {@link Code.Internal} and a message matching the
 * shape emitted by the SDK's response-validation interceptor — so propagating
 * the error from a handler produces the same wire response as not calling
 * `validate` at all.
 *
 * Intended use:
 * ```ts
 * return validate(PayOutResponseSchema, { result: { case: "accepted", value: {} } });
 * ```
 *
 * Catch the error to convert it into a domain-level failure (e.g. the `Failed`
 * arm of a `oneof result`) instead of an opaque `Code.Internal`.
 */
export function validate<Desc extends DescMessage>(
  schema: Desc,
  msg: MessageShape<Desc>,
): MessageShape<Desc> {
  const result = validator.validate(schema, msg);
  if (result.kind === "invalid") {
    const details = result.violations
      .map((v) => `${v.field?.toString() ?? ""}: ${v.message}`)
      .join("; ");
    throw new ConnectError(`response validation failed: ${details}`, Code.Internal);
  }
  if (result.kind === "error") {
    throw new ConnectError(
      `response validation error: ${result.error.message}`,
      Code.Internal,
    );
  }
  return msg;
}
