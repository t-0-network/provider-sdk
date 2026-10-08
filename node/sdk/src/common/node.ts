import type * as http from "node:http";
import type * as http2 from "node:http2";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { createService, type CreateServiceOptions, type Router } from "./service.js";

export type NodeHandlerFn = (request: http.IncomingMessage, response: http.ServerResponse) => void;

/** A handler as node:http2 takes it. */
type Http2HandlerFn = (request: http2.Http2ServerRequest, response: http2.Http2ServerResponse) => void;

// The node:http overload comes first, so an inline handler's request and response are typed as
// node:http's, as in 1.2.1. The first two are generic, so a handler for both protocols
// (nodeAdapter's) keeps its type and can still be given to node:http2. The last one is never
// chosen for a call: it is there so that ReturnType and Parameters give NodeHandlerFn, as in 1.2.1.
/**
 * Returns next unchanged, typed as it was given. Signature verification is part of every handler
 * that createService builds, so nothing has to wrap it.
 *
 * @deprecated Not used by the SDK; will be removed in a future release.
 */
export function signatureValidation<H extends NodeHandlerFn>(next: H): H;
/** @deprecated Not used by the SDK; will be removed in a future release. */
export function signatureValidation<H extends Http2HandlerFn>(next: H): H;
/** @deprecated Not used by the SDK; will be removed in a future release. */
export function signatureValidation(next: NodeHandlerFn): NodeHandlerFn;
export function signatureValidation(next: NodeHandlerFn | Http2HandlerFn): NodeHandlerFn | Http2HandlerFn {
  return next;
}

/**
 * A handler for node:http (Connect) and for node:http2 (gRPC, and Connect over HTTP/2). The public
 * createHandler in service/node.ts gives it the types of the server it is given to.
 */
export const createHandler = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): ReturnType<typeof connectNodeAdapter> =>
  connectNodeAdapter(createService(networkPublicKey, registerRoutes, options));
