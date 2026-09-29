import {createClient as createConnectClient} from "@connectrpc/connect";
import {validateReadWriteMaxBytes, type CommonTransportOptions} from "@connectrpc/connect/protocol";
import {createTransport} from "@connectrpc/connect/protocol-connect";
import CreateSigner from "./signer.js";
import {createSigningFetchClient} from "./signing-http-client.js";
import {DescService} from "@bufbuild/protobuf";

/**
 * Creates a client for a T-0 Network service that signs every request. It speaks the Connect
 * protocol in binary: unary calls as `application/proto`, streaming calls as
 * `application/connect+proto`.
 *
 * Unary calls are signed over the whole request body. Client-streaming and server-streaming calls
 * are signed over their first request message only, and the request goes out as soon as that
 * message is available. Bidirectional streaming is not supported.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string, svc: T, opts?: ClientOptions) {
    const sign: SignerFunction = typeof signer === "string" || Buffer.isBuffer(signer) ? CreateSigner(signer) : signer;

    // One transport configuration for every call. Unary and streaming calls take separate
    // instances only for their own default timeout.
    const unaryTransport = createTransport(transportOptions(sign, endpoint, opts?.unaryTimeoutMs));
    const streamTransport = createTransport(transportOptions(sign, endpoint, opts?.streamTimeoutMs));

    return createConnectClient(svc, {
        unary: unaryTransport.unary.bind(unaryTransport),
        stream: streamTransport.stream.bind(streamTransport),
    });
}

/**
 * The options of the client's transport: the Connect protocol, binary format, over the signing
 * fetch client. CommonTransportOptions is connect-es internal API that does not follow semantic
 * versioning, so every field is spelled out here and a change to it upstream fails the build.
 *
 * @internal
 */
export function transportOptions(signer: SignerFunction, endpoint: string, timeoutMs?: number): CommonTransportOptions {
    return {
        httpClient: createSigningFetchClient(signer),
        baseUrl: endpoint,
        useBinaryFormat: true,
        interceptors: [],
        acceptCompression: [],
        sendCompression: null,
        ...validateReadWriteMaxBytes(undefined, undefined, undefined),
        defaultTimeoutMs: timeoutMs,
    };
}

/**
 * Options for createClient.
 */
export interface ClientOptions {
    /**
     * Timeout of each unary call in milliseconds, from sending the request to reading the
     * response. A call's own `timeoutMs` overrides it. Default: none.
     */
    unaryTimeoutMs?: number;
    /**
     * Timeout of each client-streaming or server-streaming call in milliseconds, from waiting for
     * the first request message to reading the end of the response. A call's own `timeoutMs`
     * overrides it. Default: none.
     */
    streamTimeoutMs?: number;
}

/**
 * Signature with metadata for particular request
 */
export interface Signature {
    //hex encoded signature
    signature: Buffer;
    // hex encoded public key
    publicKey: Buffer;
}

/**
 * Signature function for signing requests to T-0 API. Accepts any data in string format and return signature
 * with metadata
 */
export type SignerFunction = (data: Buffer) => Promise<Signature>;
