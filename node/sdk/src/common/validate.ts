import { Code, ConnectError } from "@connectrpc/connect";
import { createValidator, type Validator } from "@bufbuild/protovalidate";
import type { DescMessage, MessageShape, Registry } from "@bufbuild/protobuf";
import { fieldPathString } from "./field-path.js";
import { RESPONSE_INVALID, RESPONSE_VALIDATION_ERROR } from "./messages.js";

/**
 * Shared protovalidate validator instance for the public {@link validate} helper.
 * Construction is cheap, but reusing one instance avoids repeated compilation
 * of the same rules.
 */
export const validator = createValidator();

/**
 * Options of {@link validate}.
 */
export interface ValidateOptions {
  /**
   * Protobuf registry for resolving custom protovalidate predefined-rule
   * extensions (proto2 `extend buf.validate.*Rules`). Pass the registry given
   * to `createService` or `createHandler`, so the check resolves the same
   * rules as the server's.
   */
  registry?: Registry;
}

// One validator per registry, built on first use and then reused, as the shared one is.
const registryValidators = new WeakMap<Registry, Validator>();

function validatorFor(registry: Registry | undefined): Validator {
  if (!registry) {
    return validator;
  }
  let v = registryValidators.get(registry);
  if (!v) {
    v = createValidator({ registry });
    registryValidators.set(registry, v);
  }
  return v;
}

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
 * When your protos define custom predefined rules, pass the registry the
 * server was given:
 * ```ts
 * return validate(TransferResponseSchema, resp, { registry });
 * ```
 *
 * Catch the error to convert it into a domain-level failure (e.g. the `Failed`
 * arm of a `oneof result`) instead of an opaque `Code.Internal`.
 */
export function validate<Desc extends DescMessage>(
  schema: Desc,
  msg: MessageShape<Desc>,
  options?: ValidateOptions,
): MessageShape<Desc> {
  const result = validatorFor(options?.registry).validate(schema, msg);
  if (result.kind === "invalid") {
    const details = result.violations
      .map((v) => `${fieldPathString(v.field)}: ${v.message}`)
      .join("; ");
    throw new ConnectError(RESPONSE_INVALID(details), Code.Internal);
  }
  if (result.kind === "error") {
    throw new ConnectError(
      RESPONSE_VALIDATION_ERROR(result.error.message),
      Code.Internal,
    );
  }
  return msg;
}
