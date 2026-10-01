import type * as http from "node:http";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { BodyHashes, createService, type CreateServiceOptions, type Router } from "./service.js";

export type NodeHandlerFn = (request: http.IncomingMessage, response: http.ServerResponse) => void;

export const signatureValidation= (next: NodeHandlerFn): NodeHandlerFn => (req :any, resp:any) => {
  const bodyHashes = new BodyHashes();
  (req as any).bodyHashes = bodyHashes

  req.on("data", (chunk : any)=>{
    if (chunk instanceof Buffer) {
      bodyHashes.update(chunk);
    }
  })

  next(req, resp);
}

export const createHandler = (
  networkPublicKey: string | Buffer,
  registerRoutes: (router: Router) => void,
  options?: CreateServiceOptions,
): NodeHandlerFn =>
  signatureValidation(connectNodeAdapter(createService(networkPublicKey, registerRoutes, options)));
