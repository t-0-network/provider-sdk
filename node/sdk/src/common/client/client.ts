import {Code, ConnectError, createClient as createConnectClient, type CallOptions, type Client} from "@connectrpc/connect";
import {validateReadWriteMaxBytes, type CommonTransportOptions} from "@connectrpc/connect/protocol";
import {createTransport} from "@connectrpc/connect/protocol-connect";
import CreateSigner from "./signer.js";
import {createSigningHttpClient} from "./signing-http-client.js";
import {WireFormat} from "../wire-format.js";
import {DescService} from "@bufbuild/protobuf";

/**
 * Creates a Connect client for a T-0 Network service that signs every request: a unary call over
 * its whole body, a client- or server-streaming call over its first request envelope. Bidirectional
 * streams fail with `unimplemented`. See docs/STREAMING.md.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string, svc: T, opts?: ClientOptions) {
    const sign: SignerFunction = typeof signer === "function" ? signer : CreateSigner(signer);

    const wireFormat = opts?.wireFormat === undefined ? WireFormat.Binary : opts.wireFormat;
    if (wireFormat !== WireFormat.Binary && wireFormat !== WireFormat.Json) {
        throw new Error("wireFormat must be WireFormat.Binary or WireFormat.Json");
    }
    const unaryTimeoutMs = timeout("timeoutMs", opts?.timeoutMs) ?? DEFAULT_TIMEOUT_MS;
    const streamTimeoutMs = timeout("streamTimeoutMs", opts?.streamTimeoutMs) ?? DEFAULT_STREAM_TIMEOUT_MS;
    const unaryTransport = createTransport(transportOptions(sign, endpoint, unaryTimeoutMs, wireFormat));
    const streamTransport = createTransport(transportOptions(sign, endpoint, streamTimeoutMs, wireFormat));

    // async: a refused call fails where the call's result is awaited, and nothing is sent.
    const client = createConnectClient(svc, {
        unary: async (method, signal, timeoutMs, header, input, contextValues) =>
            unaryTransport.unary(method, signal, timeout("timeoutMs", timeoutMs), header, input, contextValues),
        stream: (method, signal, timeoutMs, header, input, contextValues) => {
            const response = (async () => {
                // Policy: the network accepts no bidi streams (#370); over HTTP/1.1 they could not interleave anyway.
                if (method.methodKind === "bidi_streaming") {
                    throw new ConnectError("bidirectional streams are not supported", Code.Unimplemented);
                }
                return streamTransport.stream(method, signal, timeout("timeoutMs", timeoutMs), header, input, contextValues);
            })();
            // A server stream is awaited only once its iteration starts; until then a refusal must not
            // count as an unhandled rejection. The caller still gets it from the first next().
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
        const unlink = linkSignal(options?.signal, cancel);
        const it = call(input, {...options, signal: cancel.signal})[Symbol.asyncIterator]();
        return {
            [Symbol.asyncIterator]: () => ({
                next: () => it.next().then(
                    (r) => {
                        if (r.done) {
                            unlink();
                        }
                        return r;
                    },
                    (e) => {
                        unlink();
                        throw e;
                    },
                ),
                return: async (value?: unknown) => {
                    cancel.abort(new ConnectError("the stream was closed before its end", Code.Canceled));
                    try {
                        while (!(await it.next()).done) { /* discard */ }
                    } catch {
                        // the cancellation
                    }
                    unlink();
                    return {done: true, value};
                },
            }),
        };
    };
}

// The caller's signal cancels the call; the returned function detaches it once the call has ended,
// so a signal shared by many calls does not collect their listeners.
function linkSignal(signal: AbortSignal | undefined, cancel: AbortController): () => void {
    if (signal === undefined) {
        return () => {};
    }
    if (signal.aborted) {
        cancel.abort(signal.reason);
        return () => {};
    }
    const onAbort = () => cancel.abort(signal.reason);
    signal.addEventListener("abort", onAbort, {once: true});
    return () => signal.removeEventListener("abort", onAbort);
}

/**
 * CommonTransportOptions is `@private` connect-es API: every field is spelled out so that an
 * upstream change fails the build.
 *
 * @internal
 */
export function transportOptions(signer: SignerFunction, endpoint: string, timeoutMs: number, wireFormat: WireFormat): CommonTransportOptions {
    return {
        httpClient: createSigningHttpClient(signer),
        baseUrl: endpoint,
        useBinaryFormat: wireFormat === WireFormat.Binary,
        interceptors: [],
        acceptCompression: [],
        sendCompression: null,
        ...validateReadWriteMaxBytes(undefined, undefined, undefined),
        defaultTimeoutMs: timeoutMs,
    };
}

const DEFAULT_TIMEOUT_MS = 15_000;
const DEFAULT_STREAM_TIMEOUT_MS = 300_000;

// Node's timers fire at once from 2^31 ms, Infinity included.
const MAX_TIMEOUT_MS = 2 ** 31 - 1;

/**
 * A timeout in ms, or undefined when none is given (the default applies). Anything else that is
 * not a positive number up to MAX_TIMEOUT_MS is refused: 0, a negative value or null would mean
 * no deadline, and NaN or a larger value would end the call at once.
 */
function timeout(name: string, ms: number | undefined): number | undefined {
    if (ms === undefined) {
        return undefined;
    }
    if (typeof ms !== "number" || !(ms > 0 && ms <= MAX_TIMEOUT_MS)) {
        throw new RangeError(`${name} must be a positive duration of at most ${MAX_TIMEOUT_MS} ms`);
    }
    // Connect-Timeout-Ms is a whole number of ms; a server refuses "1000.5".
    return Math.ceil(ms);
}

/**
 * Options for createClient. See docs/STREAMING.md.
 */
export interface ClientOptions {
    /**
     * Deadline of each unary call in ms: greater than 0, at most 2^31 − 1. A call's own `timeoutMs`
     * replaces it. Default: 15_000.
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
