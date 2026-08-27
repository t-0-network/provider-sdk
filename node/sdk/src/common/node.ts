import type * as http from "node:http";
import {keccak_256} from "@noble/hashes/sha3.js";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { createService, type CreateServiceOptions, type Router } from "./service.js";

export type NodeHandlerFn = (request: http.IncomingMessage, response: http.ServerResponse) => void;

export const signatureValidation= (next: NodeHandlerFn): NodeHandlerFn => (req :any, resp:any) => {
  const hasher = keccak_256.create();
  (req as any).hasher = hasher

  req.on("data", (chunk : any)=>{
    if (chunk instanceof Buffer) {
      hasher.update(chunk);
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
