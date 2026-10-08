// Call forms of the v1.2.1 public API that must keep compiling (test/compat.test.ts runs tsc --strict
// on this file). Never run.
import * as http from 'node:http';
import * as http2 from 'node:http2';
import type { ContextValues } from '@connectrpc/connect';
import { create, type DescMessage, type MessageShape } from '@bufbuild/protobuf';
import type { Validator } from '@bufbuild/protovalidate';
import {
  createHandler, createService, DecimalSchema, nodeAdapter, signatureValidation, validate, validator, type NodeHandlerFn,
} from '../../src/index.js';

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

// signatureValidation types an inline handler's request and response as node:http's: given to
// http.createServer, kept in a variable, or given to a middleware chain whose request extends node:http's.
http.createServer(signatureValidation((req, res) => { res.end(String(req.url)); }));
const inline = signatureValidation((req, res) => { res.end(String(req.url)); });
http1Server(inline);
interface MiddlewareRequest extends http.IncomingMessage { body: unknown }
declare const use: (...handlers: ((req: MiddlewareRequest, res: http.ServerResponse, next: () => void) => void)[]) => void;
use(signatureValidation((req, res) => { res.end(String(req.url)); }));

// ReturnType and Parameters of signatureValidation are NodeHandlerFn, as in 1.2.1.
const returned: NodeHandlerFn = null as unknown as ReturnType<typeof signatureValidation>;
const returnType: ReturnType<typeof signatureValidation> = null as unknown as NodeHandlerFn;
const parameter: NodeHandlerFn = null as unknown as Parameters<typeof signatureValidation>[0];
const parameters: Parameters<typeof signatureValidation> = [null as unknown as NodeHandlerFn];
void returned; void returnType; void parameter; void parameters;

// createHandler's handler is a NodeHandlerFn: kept in a variable and replaced by a wrapped one, given to
// http.createServer, or given to a middleware chain whose request extends node:http's.
declare const withLogging: (handler: NodeHandlerFn) => NodeHandlerFn;
let reassigned = createHandler(networkPublicKey, () => {});
reassigned = withLogging(reassigned);
void reassigned;
http.createServer(createHandler(networkPublicKey, () => {}));
use(createHandler(networkPublicKey, () => {}));

// ReturnType and Parameters of createHandler are NodeHandlerFn and its arguments, as in 1.2.1.
const handlerReturned: NodeHandlerFn = null as unknown as ReturnType<typeof createHandler>;
const handlerReturnType: ReturnType<typeof createHandler> = null as unknown as NodeHandlerFn;
const handlerParameters: Parameters<typeof createHandler> = [networkPublicKey, () => {}, { version: '1.2.1' }];
void handlerReturned; void handlerReturnType; void handlerParameters;

// The node:http2 forms.
http2Server(signatureValidation(adapter));
http2Server(createHandler(networkPublicKey, () => {}));
http2.createServer(createHandler(networkPublicKey, () => {}));
// Where node:http2's server takes options first, the handler's types are given.
http2.createSecureServer({ allowHTTP1: true }, createHandler<http2.Http2ServerRequest, http2.Http2ServerResponse>(networkPublicKey, () => {}));

// validate with a schema and a message, kept as a function of those two, and its shared validator.
const decimal = validate(DecimalSchema, create(DecimalSchema, { unscaled: 1n, exponent: 0 }));
const exponent: number = decimal.exponent;
const twoArgs: <Desc extends DescMessage>(schema: Desc, msg: MessageShape<Desc>) => MessageShape<Desc> = validate;
const validateParameters: Parameters<typeof validate> = [DecimalSchema, create(DecimalSchema)];
const shared: Validator = validator;
void exponent; void twoArgs; void validateParameters; void shared;
