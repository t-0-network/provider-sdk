export * from "../common/crypto/index.js";
export type { Logger } from "../common/logger.js";
export { default as NetworkHeaders } from "../common/headers.js";

import { createRequestDecoder as _createBaseDecoder } from "../common/crypto/decode.js";
import type { CreateDecoderOptions, RequestDecoder } from "../common/crypto/decode.js";
import { SDK_VERSION } from "../version.js";

export function createRequestDecoder(opts: CreateDecoderOptions): RequestDecoder {
  return _createBaseDecoder({ ...opts, version: opts.version ?? SDK_VERSION });
}
