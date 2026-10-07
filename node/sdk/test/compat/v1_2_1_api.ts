// Call forms of the v1.2.1 public API that must keep compiling (test/compat.test.ts runs tsc --strict
// on this file). Never run.
import type * as http from 'node:http';
import type * as http2 from 'node:http2';
import type { ContextValues } from '@connectrpc/connect';
import { createHandler, createService, nodeAdapter, signatureValidation, type NodeHandlerFn } from '../../src/index.js';

declare const networkPublicKey: string;
declare const http1Server: (listener: http.RequestListener) => void;
declare const http2Server: (listener: (request: http2.Http2ServerRequest, response: http2.Http2ServerResponse) => void) => void;

// createService's result, spread into the adapter or read, contextValues included.
const service = createService(networkPublicKey, () => {});
const contextValues: (req: any) => ContextValues = service.contextValues;
const adapter = nodeAdapter({ ...service });
void contextValues;

// signatureValidation and NodeHandlerFn with a node:http handler of one's own.
const mine = (_request: http.IncomingMessage, _response: http.ServerResponse): void => {};
const typed: NodeHandlerFn = mine;
const wrapped: NodeHandlerFn = signatureValidation(mine);
http1Server(signatureValidation(typed));
http1Server(wrapped);
http1Server(signatureValidation(adapter));
http1Server(createHandler(networkPublicKey, () => {}));
const handler: NodeHandlerFn = createHandler(networkPublicKey, () => {});
void handler;

// The node:http2 forms.
http2Server(signatureValidation(adapter));
http2Server(createHandler(networkPublicKey, () => {}));
