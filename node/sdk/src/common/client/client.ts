import {Code, ConnectError, createClient as createConnectClient} from "@connectrpc/connect";
import {validateReadWriteMaxBytes, type CommonTransportOptions} from "@connectrpc/connect/protocol";
import {createTransport} from "@connectrpc/connect/protocol-connect";
import CreateSigner from "./signer.js";
import {createSigningFetchClient} from "./signing-http-client.js";
import {DescService} from "@bufbuild/protobuf";

/**
 * Creates a Connect client for a T-0 Network service that signs every request: a unary call over
 * its whole body, a client- or server-streaming call over its first request envelope. Bidirectional
 * streams fail with `unimplemented`. See docs/node/STREAMING.md.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string, svc: T, opts?: ClientOptions) {
    const sign: SignerFunction = typeof signer === "string" || Buffer.isBuffer(signer) ? CreateSigner(signer) : signer;

    const useBinaryFormat = opts?.useBinaryFormat ?? true;
    const unaryTransport = createTransport(transportOptions(sign, endpoint, opts?.unaryTimeoutMs, useBinaryFormat));
    const streamTransport = createTransport(transportOptions(sign, endpoint, opts?.streamTimeoutMs, useBinaryFormat));

    return createConnectClient(svc, {
        unary: unaryTransport.unary.bind(unaryTransport),
        stream: (method, signal, timeoutMs, header, input, contextValues) => {
            // fetch streams the request half-duplex, so a bidi call could not interleave the two.
            if (method.methodKind === "bidi_streaming") {
                return Promise.reject(new ConnectError("bidirectional streams are not supported", Code.Unimplemented));
            }
            return streamTransport.stream(method, signal, timeoutMs, header, input, contextValues);
        },
    });
}

/**
 * CommonTransportOptions is `@private` connect-es API: every field is spelled out so that an
 * upstream change fails the build.
 *
 * @internal
 */
export function transportOptions(signer: SignerFunction, endpoint: string, timeoutMs?: number, useBinaryFormat = true): CommonTransportOptions {
    return {
        httpClient: createSigningFetchClient(signer),
        baseUrl: endpoint,
        useBinaryFormat,
        interceptors: [],
        acceptCompression: [],
        sendCompression: null,
        ...validateReadWriteMaxBytes(undefined, undefined, undefined),
        defaultTimeoutMs: timeoutMs,
    };
}

/**
 * Options for createClient. See docs/node/STREAMING.md.
 */
export interface ClientOptions {
    /** Deadline of each unary call in ms; a call's own `timeoutMs` overrides it. Default: none. */
    unaryTimeoutMs?: number;
    /**
     * Deadline of each client- or server-streaming call in ms, including the wait for the first
     * request message; a call's own `timeoutMs` overrides it. Default: none.
     */
    streamTimeoutMs?: number;
    /** Binary Protobuf (default) or, when false, Connect JSON. Either is signed over the bytes as sent. */
    useBinaryFormat?: boolean;
}

/**
 * A signature over a request digest and the signer's public key, as raw bytes; the client sends
 * them hex-encoded in the signature headers.
 */
export interface Signature {
    signature: Buffer;
    publicKey: Buffer;
}

/**
 * Signs a 32-byte request digest: Keccak256(signed bytes || uint64le(timestamp_ms)).
 */
export type SignerFunction = (data: Buffer) => Promise<Signature>;
