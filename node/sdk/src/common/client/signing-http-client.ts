import {Code, ConnectError} from "@connectrpc/connect";
import type {UniversalClientFn} from "@connectrpc/connect/protocol";
import {createNodeHttpClient} from "@connectrpc/connect-node";
import {signatureHeaders} from "./sign.js";
import type {SignerFunction} from "./client.js";

/**
 * The transport's HTTP client: signs an enveloped body (Connect streaming, gRPC) over its first
 * envelope as sent and sends it at once, any other body whole. See docs/STREAMING.md.
 *
 * @param httpClient sends the signed request; connect-node's HTTP/1.1 client unless a test injects one.
 */
export function createSigningHttpClient(signer: SignerFunction, httpClient: UniversalClientFn = createNodeHttpClient({httpVersion: "1.1"})): UniversalClientFn {
    return async (req) => {
        // A GET carries its message in the URL, which the signature would not cover.
        if (req.method.toUpperCase() === "GET") {
            throw new ConnectError("GET requests are not supported", Code.Unimplemented);
        }
        const it = (req.body ?? emptyBody)[Symbol.asyncIterator]();
        const enveloped = isEnveloped(req.header.get("Content-Type"));

        let first: Uint8Array | undefined;
        let whole: Uint8Array | undefined;
        let headers: [string, string][];
        try {
            if (enveloped) {
                // A client stream waits here for its first message; the call's deadline or signal ends that.
                const r = await untilAborted(it.next(), req.signal);
                if (r.done !== true) {
                    first = r.value;
                    requireOneEnvelope(first);
                }
                // The signing function is the caller's: the deadline or a cancellation ends the wait for it too.
                headers = await untilAborted(signatureHeaders(signer, first ?? new Uint8Array(0)), req.signal);
            } else {
                whole = await untilAborted(readAll(it), req.signal);
                headers = await untilAborted(signatureHeaders(signer, whole), req.signal);
            }
        } catch (e) {
            it.return?.().catch(() => {});
            throw e;
        }
        for (const [name, value] of headers) {
            req.header.set(name, value);
        }

        let body: AsyncIterable<Uint8Array> | undefined;
        if (enveloped) {
            body = firstThenRest(first, it);
        } else if (whole !== undefined && req.body !== undefined) {
            req.header.set("Content-Length", String(whole.byteLength));
            body = firstThenRest(whole, emptyBody[Symbol.asyncIterator]());
        }
        return httpClient({...req, body});
    };
}

function isEnveloped(contentType: string | null): boolean {
    const mediaType = (contentType ?? "").split(";")[0].trim().toLowerCase();
    return mediaType.startsWith("application/connect+")
        || mediaType === "application/grpc"
        || mediaType.startsWith("application/grpc+");
}

// The first chunk is signed as the first envelope, so it must be exactly one: the call fails
// instead of signing other bytes.
function requireOneEnvelope(chunk: Uint8Array): void {
    const size = chunk.byteLength >= 5
        ? 5 + new DataView(chunk.buffer, chunk.byteOffset, chunk.byteLength).getUint32(1)
        : Infinity;
    if (size > chunk.byteLength) {
        throw new ConnectError("streaming request ends inside its first message", Code.InvalidArgument);
    }
    if (size < chunk.byteLength) {
        throw new ConnectError("the first request chunk is not one complete envelope", Code.Internal);
    }
}

// A unary body is signed whole, however many chunks it comes in.
async function readAll(it: AsyncIterator<Uint8Array>): Promise<Uint8Array> {
    const chunks: Uint8Array[] = [];
    let size = 0;
    for (let r = await it.next(); r.done !== true; r = await it.next()) {
        chunks.push(r.value);
        size += r.value.byteLength;
    }
    const body = new Uint8Array(size);
    let at = 0;
    for (const chunk of chunks) {
        body.set(chunk, at);
        at += chunk.byteLength;
    }
    return body;
}

// `first` (if any), then the rest of `it`. Not an async generator: return() and throw() must reach
// `it` at once, even while a next() is pending, so that a failed send closes the caller's stream.
function firstThenRest(first: Uint8Array | undefined, it: AsyncIterator<Uint8Array>): AsyncIterable<Uint8Array> {
    let pending = first;
    const rest: AsyncIterator<Uint8Array> = {
        next() {
            if (pending === undefined) {
                return it.next();
            }
            const value = pending;
            pending = undefined;
            return Promise.resolve({done: false, value});
        },
        return: it.return?.bind(it),
        throw: it.throw?.bind(it),
    };
    return {[Symbol.asyncIterator]: () => rest};
}

function untilAborted<T>(promise: Promise<T>, signal: AbortSignal | undefined): Promise<T> {
    if (signal === undefined) {
        return promise;
    }
    return new Promise<T>((resolve, reject) => {
        const onAbort = () => reject(signal.reason);
        if (signal.aborted) {
            onAbort();
        }
        signal.addEventListener("abort", onAbort, {once: true});
        promise.then(resolve, reject).finally(() => signal.removeEventListener("abort", onAbort));
    });
}

const emptyBody: AsyncIterable<Uint8Array> = {
    [Symbol.asyncIterator]: () => ({next: async () => ({done: true, value: undefined})}),
};
