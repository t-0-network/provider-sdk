import type { Interceptor } from "@connectrpc/connect";
import { createRegistry } from "@bufbuild/protobuf";
import { file_tzero_v1_payment_provider } from "../common/gen/tzero/v1/payment/provider_pb.js";
import { file_tzero_v1_payment_network } from "../common/gen/tzero/v1/payment/network_pb.js";
import type { Logger } from "../common/validation.js";
import { createValidationInterceptor } from "../common/validation.js";
import { createRequestDecoder as createBaseRequestDecoder } from "../common/crypto/decode.js";
import type { RequestDecoder } from "../common/crypto/decode.js";
import type { CreateVerifierOptions } from "../common/crypto/request.js";

// Re-export everything from the common layer for backward compatibility.
export { createValidationInterceptor, type Logger, type ValidationInterceptorOptions } from "../common/validation.js";

/**
 * Registry covering the t-0 network provider contract protos. Leaf file
 * descriptors pull in their full dependency graph, so adding new messages
 * or fields to existing protos requires only `buf:generate`.
 */
export const networkRegistry = createRegistry(
  file_tzero_v1_payment_provider,
  file_tzero_v1_payment_network,
);

/**
 * Validation interceptor pre-configured for the t-0 network provider contract.
 */
export function createNetworkValidationInterceptor(logger?: Logger): Interceptor {
  return createValidationInterceptor({ logger, registry: networkRegistry });
}

/**
 * Request decoder pre-configured for the t-0 network provider contract.
 */
export function createRequestDecoder(opts: CreateVerifierOptions): RequestDecoder {
  return createBaseRequestDecoder({ ...opts, registry: networkRegistry });
}

/**
 * @deprecated Use createValidationInterceptor instead.
 */
export const createResponseValidation = createValidationInterceptor;
