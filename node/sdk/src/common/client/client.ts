import {Code, ConnectError, createClient as createConnectClient, type CallOptions, type Client} from "@connectrpc/connect";
import {validateReadWriteMaxBytes, type CommonTransportOptions} from "@connectrpc/connect/protocol";
import {createTransport} from "@connectrpc/connect/protocol-connect";
import {compressionGzip} from "@connectrpc/connect-node";
import CreateSigner from "./signer.js";
import {createSigningHttpClient} from "./signing-http-client.js";
import {WireFormat} from "../wire-format.js";
import {
    BIDI_NOT_SUPPORTED,
    CALL_DEADLINE_PASSED,
    SIGNER_NULL,
    STREAM_CLOSED_EARLY,
    STREAM_TIMEOUT_NOT_VALID,
    TIMEOUT_NOT_VALID,
} from "../messages.js";
import {DescService} from "@bufbuild/protobuf";

/**
 * Creates a Connect client for a T-0 Network service that signs every request: a unary call over
 * its whole body, a client- or server-streaming call over its first request envelope. Bidirectional
 * streams fail with `unimplemented`. See docs/STREAMING.md.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, baseUrl: string, svc: T, opts?: ClientOptions) {
    const wireFormat = opts?.wireFormat ?? WireFormat.Binary;
    // Checked in the order of every SDK: the base URL (by the caller), the timeouts, the key or signer.
    const unaryTimeoutMs = timeout(TIMEOUT_NOT_VALID, opts?.timeoutMs) ?? DEFAULT_TIMEOUT_MS;
    const streamTimeoutMs = timeout(STREAM_TIMEOUT_NOT_VALID, opts?.streamTimeoutMs) ?? DEFAULT_STREAM_TIMEOUT_MS;
    if (signer === null || signer === undefined) {
        throw new TypeError(SIGNER_NULL);
    }
    const sign: SignerFunction = typeof signer === "function" ? signer : CreateSigner(signer);
    const unaryTransport = createTransport(transportOptions(sign, baseUrl, unaryTimeoutMs, wireFormat));
    const streamTransport = createTransport(transportOptions(sign, baseUrl, streamTimeoutMs, wireFormat));

    const client = createConnectClient(svc, {
        unary: async (method, signal, timeoutMs, header, input, contextValues) =>
            unaryTransport.unary(method, signal, callTimeout(timeoutMs), header, input, contextValues),
        stream: (method, signal, timeoutMs, header, input, contextValues) => {
            // async: a refused call fails where the call's result is awaited, and nothing is sent.
            const response = (async () => {
                // Policy: the network accepts no bidi streams (#370); over HTTP/1.1 they could not interleave anyway.
                if (method.methodKind === "bidi_streaming") {
                    throw new ConnectError(BIDI_NOT_SUPPORTED, Code.Unimplemented);
                }
                return streamTransport.stream(method, signal, callTimeout(timeoutMs), header, input, contextValues);
            })();
            // A stream is awaited only once its iteration starts; until then a failure must not count
            // as an unhandled rejection. The caller still gets it from the first next().
            response.catch(() => {});
            return response;
        },
    });
    const calls = client as Record<string, unknown>;
    for (const method of svc.methods) {
        if (method.methodKind === "server_streaming") {
            calls[method.localName] = cancelOnReturn(calls[method.localName] as ServerStreamingCall);
        }
    }
    return client as Client<T>;
}

type ServerStreamingCall = (input: unknown, options?: CallOptions) => AsyncIterable<unknown>;

// Leaving a for-await loop over a server stream early calls return(): it cancels the call and reads
// it to its end, so the socket and the deadline timer are released at once, not at the deadline.
function cancelOnReturn(call: ServerStreamingCall): ServerStreamingCall {
    return (input, options) => {
        const cancel = new AbortController();
        const signal = options?.signal === undefined ? cancel.signal : AbortSignal.any([options.signal, cancel.signal]);
        const it = call(input, {...options, signal})[Symbol.asyncIterator]();
        return {
            [Symbol.asyncIterator]: () => ({
                next: () => it.next(),
                return: async (value?: unknown) => {
                    cancel.abort(new ConnectError(STREAM_CLOSED_EARLY, Code.Canceled));
                    try {
                        while (!(await it.next()).done) { /* discard */ }
                    } catch {
                        // the cancellation
                    }
                    return {done: true, value};
                },
            }),
        };
    };
}

/**
 * CommonTransportOptions is `@private` connect-es API: every field is spelled out so that an
 * upstream change fails the build.
 *
 * @internal
 */
export function transportOptions(signer: SignerFunction, baseUrl: string, timeoutMs: number, wireFormat: WireFormat): CommonTransportOptions {
    return {
        httpClient: createSigningHttpClient(signer),
        baseUrl,
        useBinaryFormat: wireFormat !== WireFormat.Json,
        interceptors: [],
        // gzip responses are accepted, as in every SDK; requests are sent uncompressed.
        acceptCompression: [compressionGzip],
        sendCompression: null,
        ...validateReadWriteMaxBytes(undefined, undefined, undefined),
        defaultTimeoutMs: timeoutMs,
    };
}

/** The deadline of a unary call when the client is given no timeout, in ms. */
export const DEFAULT_TIMEOUT_MS = 15_000;
/** The deadline of a streaming call when the client is given no stream timeout, in ms. */
export const DEFAULT_STREAM_TIMEOUT_MS = 300_000;
/** The largest timeout and stream timeout a client takes, in ms: also the limit of Node's timers. */
export const MAX_TIMEOUT_MS = 2 ** 31 - 1;

/**
 * A configured timeout in ms, or undefined when none is given (the default applies). It must be
 * greater than 0 and at most MAX_TIMEOUT_MS, else the client is not built: it throws message.
 */
function timeout(message: string, ms: number | undefined): number | undefined {
    if (ms !== undefined && !(ms > 0 && ms <= MAX_TIMEOUT_MS)) {
        throw new RangeError(message);
    }
    return ms;
}

/**
 * A call's own timeout in ms. One that is not greater than 0 is a deadline that has passed: the
 * call fails at once with DeadlineExceeded and nothing is sent, as in every SDK (connect-es would
 * drop the deadline instead). One over MAX_TIMEOUT_MS is cut to it, the limit of Node's timers.
 */
function callTimeout(ms: number | undefined): number | undefined {
    if (ms === undefined) {
        return undefined;
    }
    if (!(ms > 0)) {
        // connect-es's text for a deadline that passed.
        throw new ConnectError(CALL_DEADLINE_PASSED, Code.DeadlineExceeded);
    }
    return Math.min(ms, MAX_TIMEOUT_MS);
}

/**
 * Options for createClient.
 */
export interface ClientOptions {
    /**
     * Deadline of each unary call in ms: greater than 0, at most 2^31 − 1. A call's own `timeoutMs`
     * replaces it; a call's `timeoutMs` of 0 or less fails the call at once with
     * `deadline_exceeded`. Default: 15_000.
     */
    timeoutMs?: number;
    /**
     * Deadline of each client- or server-streaming call in ms, including the wait for the first
     * request message: greater than 0, at most 2^31 − 1. A call's own `timeoutMs` replaces it.
     * Default: 300_000 (5 minutes).
     */
    streamTimeoutMs?: number;
    /** `WireFormat.Binary` (default) or `WireFormat.Json`. Either is signed over the bytes as sent. */
    wireFormat?: WireFormat;
}

/**
 * A signature over a request digest and the signer's public key, as raw bytes; the client sends
 * them hex-encoded in the signature headers, exactly as given.
 */
export interface Signature {
    signature: Buffer;
    publicKey: Buffer;
}

/**
 * A custom signer, as createClient takes it for its signer argument (newSignerFromHex builds one
 * from a hex key). It signs a 32-byte request digest, Keccak256(signed bytes || uint64le(timestamp_ms)),
 * and returns the signature, 64 bytes r ‖ s or 65 bytes r ‖ s ‖ v, and the signer's 65-byte
 * uncompressed public key. The client checks their lengths, and that the key is uncompressed,
 * before anything is sent, and sends the signature exactly as returned. If the check fails or the
 * signer throws, the call fails with `internal` "signing the request failed: <cause>".
 */
export type SignerFunction = (data: Buffer) => Promise<Signature>;
