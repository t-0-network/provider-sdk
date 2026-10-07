import type * as http from "node:http";
import type * as http2 from "node:http2";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { createService, type CreateServiceOptions, type Router } from "./service.js";

export type NodeHandlerFn = (request: http.IncomingMessage, response: http.ServerResponse) => void;

/** A handler as node:http2 takes it. */
type Http2HandlerFn = (request: http2.Http2ServerRequest, response: http2.Http2ServerResponse) => void;

/**
 * Returns next unchanged, typed as it was given. Signature verification is part of every handler
 * that createService builds, so nothing has to wrap it.
 *
 * @deprecated Not used by the SDK; will be removed in a future release.
 */
export const signatureValidation = <H extends NodeHandlerFn | Http2HandlerFn>(next: H): H => next;

/** A handler for node:http (Connect) and for node:http2 (gRPC, and Connect over HTTP/2). */
export const createHandler = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): NodeHandlerFn & Http2HandlerFn =>
  connectNodeAdapter(createService(networkPublicKey, registerRoutes, options)) as NodeHandlerFn & Http2HandlerFn;
