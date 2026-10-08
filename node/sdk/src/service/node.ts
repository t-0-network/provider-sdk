import type * as http from "node:http";
import type * as http2 from "node:http2";
import { createHandler as createHandlerCommon, type NodeHandlerFn } from "../common/node.js";
import type { Router, CreateServiceOptions } from "../common/service.js";
import { SDK_VERSION } from "../version.js";

export { signatureValidation } from "../common/node.js";
export type { NodeHandlerFn } from "../common/node.js";

// The handler serves node:http (Connect) and node:http2 (gRPC, and Connect over HTTP/2). The first
// overload types its request and response as the place it is given to expects: node:http2's for
// http2.createServer, node:http's (NodeHandlerFn, as in 1.2.1) when nothing says otherwise, as for
// `let handler = createHandler(...)`. The last one is never chosen for a call: it is there so that
// ReturnType and Parameters give NodeHandlerFn, as in 1.2.1.
/**
 * A handler for node:http (Connect) and for node:http2 (gRPC, and Connect over HTTP/2). Typed as
 * NodeHandlerFn unless it is given where a node:http2 handler is expected.
 *
 * Throws if the network public key is missing or malformed; surrounding
 * whitespace is trimmed.
 */
export function createHandler<
  Req extends http.IncomingMessage | http2.Http2ServerRequest = http.IncomingMessage,
  Res extends http.ServerResponse | http2.Http2ServerResponse = http.ServerResponse,
>(
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): (request: Req, response: Res) => void;
/**
 * Throws if the network public key is missing or malformed; surrounding
 * whitespace is trimmed.
 */
export function createHandler(
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): NodeHandlerFn;
export function createHandler(
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): ReturnType<typeof createHandlerCommon> {
  return createHandlerCommon(networkPublicKey, registerRoutes, { ...options, version: options?.version ?? SDK_VERSION });
}
